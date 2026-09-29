import copy
import hashlib
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from tools.learning_content.signs import CONTENT, canonical_bytes, parse_index, validate, validate_cards, validate_guide, full_audit


class SignInventoryTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.data = json.loads((CONTENT / "catalog.json").read_text(encoding="utf-8"))
        cls.sources = json.loads((CONTENT / "sources.json").read_text(encoding="utf-8"))
        cls.cards = json.loads((CONTENT / "curated.json").read_text(encoding="utf-8"))
        cls.guide = json.loads((CONTENT / "guide.json").read_text(encoding="utf-8"))

    def test_committed_inventory_and_audit(self):
        audit = full_audit(self.data, self.sources)
        self.assertEqual(canonical_bytes(audit), (CONTENT / "audit.json").read_bytes())
        self.assertEqual(408, audit["total"])
        self.assertEqual(408, audit["csTitles"])
        self.assertEqual(292, audit["indexedFamilies"])
        self.assertEqual(408, audit["atomicLegalEntries"])
        self.assertEqual(44, audit["warningLegalCodesVerified"])
        self.assertEqual(49, audit["warningGraphicExecutionsIndexed"])
        self.assertEqual(8, audit["priorityLegalCodesVerified"])
        self.assertEqual(13, audit["priorityGraphicExecutionsIndexed"])
        self.assertEqual(34, audit["mandatoryLegalCodesVerified"])
        self.assertEqual(51, audit["mandatoryGraphicExecutionsIndexed"])
        self.assertEqual(22, audit["zoneLegalCodesVerified"])
        self.assertEqual(50, audit["zoneGraphicExecutionsIndexed"])
        self.assertEqual(47, audit["trafficLegalCodesVerified"])
        self.assertEqual(47, audit["trafficGraphicExecutionsIndexed"])
        self.assertEqual(73, audit["directionLegalCodesVerified"])
        self.assertEqual(73, audit["directionGraphicExecutionsIndexed"])
        self.assertEqual(28, audit["otherInfoLegalCodesVerified"])
        self.assertEqual(33, audit["panelLegalCodesVerified"])
        self.assertEqual(40, audit["markingLegalCodesVerified"])
        self.assertEqual(39, audit["signalAtomicAspectsIndexed"])
        self.assertEqual(0, audit["graphicVersionReviewRequired"])
        self.assertIsNone(audit["canonicalVariants"])
        self.assertEqual(40, audit["categories"]["road_marking"])
        self.assertEqual(39, audit["categories"]["light_signal"])
        self.assertGreaterEqual(audit["bundledImages"], 402)
        self.assertEqual(408, audit["graphicsPresent"])
        self.assertEqual(408, audit["graphicLicenseVerified"])
        self.assertEqual(0, audit["graphicsMissing"])
        self.assertEqual(408, audit["ruTitles"])
        self.assertEqual(408, audit["ukTitles"])
        remaining_ceiling = {
            "E 9", "IS 16b", "IS 19a", "IS 19b", "IS 19c", "IS 20",
            "IS 21a", "IS 21b", "IS 21c", "IS 22a", "IS 22b", "IS 22c",
            "IS 22d", "IS 22e", "IS 22f",
        }
        self.assertTrue(set(audit["graphicsMissingCodes"]).issubset(remaining_ceiling))
        self.assertEqual([], audit["graphicsMissingCodes"])
        self.assertEqual(0, audit["graphicLicenseReviewRequired"])
        self.assertEqual([], audit["graphicVersionReviewRequiredCodes"])
        self.assertFalse(audit["productionReady"])
        self.assertEqual([], audit["unreviewedCodes"])

    def test_manual_composite_panels_follow_official_row_order(self):
        evidence = json.loads((CONTENT / "graphics-manual-resolution.json").read_text())
        signs = {sign["code"]: sign for sign in self.data["signs"]}
        expected = {"IS 19": "abc", "IS 21": "abc", "IS 22": "abcdef"}
        paths = set()
        for family, suffixes in expected.items():
            row = evidence["families"][family]
            codes = [f"{family}{suffix}" for suffix in suffixes]
            self.assertEqual(codes, row["legalCodesInOfficialRow"])
            self.assertEqual(1, row["officialIllustrationCount"])
            self.assertEqual(len(codes), row["panelCount"])
            self.assertEqual(1, len(row["officialTiffs"]))
            original = row["officialTiffs"][0]
            bounds = []
            for n, code in enumerate(codes, 1):
                self.assertEqual(1, len(row["codeImages"][code]))
                graphic = signs[code]["graphic"]
                self.assertEqual("VERIFIED", graphic["status"])
                self.assertEqual(row["codeImages"][code][0], graphic["path"])
                self.assertEqual(original["sourceFileId"], graphic["sourceFileId"])
                self.assertEqual(original["sourceTiffSha256"], graphic["sourceTiffSha256"])
                self.assertEqual(signs[code]["sourceProvision"], graphic["sourceProvision"])
                self.assertEqual(n, graphic["provenance"]["panelOrdinal"])
                self.assertEqual(codes, graphic["provenance"]["rowCodes"])
                self.assertEqual("official-composite-panel-order", graphic["provenance"]["method"])
                bounds.append(graphic["provenance"]["sourceCropBox"])
                paths.add(graphic["path"])
            self.assertEqual(0, bounds[0][1])
            self.assertEqual(original["sourcePixelSize"][1], bounds[-1][3])
            self.assertTrue(all(a[3] == b[1] for a, b in zip(bounds, bounds[1:])))
        self.assertEqual(12, len(paths))

    def test_manual_single_code_rows_preserve_only_official_illustrations(self):
        evidence = json.loads((CONTENT / "graphics-manual-resolution.json").read_text())
        signs = {sign["code"]: sign for sign in self.data["signs"]}
        for code, count in (("IS 16b", 2), ("IS 20", 2), ("E 9", 1)):
            row = evidence["families"][code]
            self.assertEqual([code], row["legalCodesInOfficialRow"])
            self.assertEqual(count, row["officialIllustrationCount"])
            self.assertEqual(count, len(row["officialTiffs"]))
            graphic = signs[code]["graphic"]
            images = [graphic] + graphic.get("additionalImages", [])
            self.assertEqual(count, len(images))
            self.assertEqual(row["codeImages"][code], [image["path"] for image in images])
            self.assertEqual(count, len({image["sourceFileId"] for image in images}))
            for image, source in zip(images, row["officialTiffs"]):
                self.assertEqual("VERIFIED", image["status"])
                self.assertEqual(source["sourceFileId"], image["sourceFileId"])
                self.assertEqual(source["sourceTiffSha256"], image["sourceTiffSha256"])
                self.assertEqual(signs[code]["sourceProvision"], image["sourceProvision"])
                self.assertEqual("exact-single-code-row", image["provenance"]["method"])
                self.assertEqual([code], image["provenance"]["rowCodes"])
        self.assertEqual(["E 9"], signs["E 9"]["graphicVariantCodes"])
        self.assertEqual(["IS 20"], signs["IS 20"]["graphicVariantCodes"])
        self.assertEqual(["IS 16b"], signs["IS 16b"]["graphicVariantCodes"])
        self.assertEqual(15, sum(len(row["legalCodesInOfficialRow"])
                                 for row in evidence["families"].values()))

    def test_duplicate_code_rejected(self):
        data = copy.deepcopy(self.data)
        data["signs"].append(copy.deepcopy(data["signs"][0]))
        data["inventoryCount"] += 1
        with self.assertRaisesRegex(ValueError, "Duplicate sign code"):
            validate(data, self.sources)

    def test_empty_czech_title_and_invalid_category_rejected(self):
        for key, value, error in (("titleCs", "", "Empty Czech"), ("category", "unknown", "Invalid category")):
            with self.subTest(key=key):
                data = copy.deepcopy(self.data)
                data["signs"][0][key] = value
                with self.assertRaisesRegex(ValueError, error):
                    validate(data, self.sources)

    def test_unreviewed_translation_cannot_replace_official_czech(self):
        data = copy.deepcopy(self.data)
        data["signs"][0]["titleRu"] = "Предупреждение"
        with self.assertRaisesRegex(ValueError, "Unreviewed explanation"):
            validate(data, self.sources)

    def test_source_and_image_integrity(self):
        data = copy.deepcopy(self.data)
        data["signs"][0]["sourceIds"] = ["absent"]
        with self.assertRaisesRegex(ValueError, "Missing source"):
            validate(data, self.sources)
        data = copy.deepcopy(self.data)
        data["signs"][0]["graphic"]["path"] = "../escape.png"
        with self.assertRaisesRegex(ValueError, "Unsafe or missing image"):
            validate(data, self.sources)

    def test_local_graphic_hash_mime_orphan_and_explicit_sharing(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            graphics = root / "graphics"
            graphics.mkdir()
            image = graphics / "A-10.png"
            image.write_bytes(b"\x89PNG\r\n\x1a\nexample bytes")
            sign = copy.deepcopy(self.data["signs"][0])
            sign["graphic"] = {
                "sourceId": "decree-294-2015", "status": "VERIFIED",
                "path": "graphics/A-10.png", "mime": "image/png",
                "sha256": hashlib.sha256(image.read_bytes()).hexdigest(),
            }
            data = {"signs": [sign], "inventoryCount": 1}
            with patch("tools.learning_content.signs.CONTENT", root):
                self.assertEqual(1, validate(data, self.sources)["bundledImages"])
                corrupted = copy.deepcopy(data)
                corrupted["signs"][0]["graphic"]["sha256"] = "0" * 64
                with self.assertRaisesRegex(ValueError, "Image hash mismatch"):
                    validate(corrupted, self.sources)
                corrupted = copy.deepcopy(data)
                corrupted["signs"][0]["graphic"]["mime"] = "image/webp"
                with self.assertRaisesRegex(ValueError, "Image MIME mismatch"):
                    validate(corrupted, self.sources)
                extra = graphics / "unreviewed.png"
                extra.write_bytes(image.read_bytes())
                with self.assertRaisesRegex(ValueError, "Orphan sign graphics"):
                    validate(data, self.sources)
                extra.unlink()
                shared = copy.deepcopy(sign)
                shared["code"] = "A 11"
                shared["sourceProvision"] = sign["sourceProvision"].replace("A 10", "A 11")
                with self.assertRaisesRegex(ValueError, "Unexplained duplicate image hash"):
                    validate({"signs": [sign, shared], "inventoryCount": 2}, self.sources)
                sign["graphic"]["shared"] = True
                shared["graphic"]["shared"] = True
                self.assertEqual(2, validate({"signs": [sign, shared], "inventoryCount": 2}, self.sources)["total"])

    def test_prohibition_batch_has_distinct_annex_three_sources(self):
        by_code = {sign["code"]: sign for sign in self.data["signs"]}
        paths = set()
        for number, file_id in zip(range(14, 19), range(840305, 840314, 2)):
            code = f"B {number}"
            graphic = by_code[code]["graphic"]
            self.assertEqual("VERIFIED", graphic["status"])
            self.assertEqual("esbirka-294-2015-2025-07-01-zip", graphic["sourceId"])
            self.assertEqual(str(file_id), graphic["sourceFileId"])
            self.assertIn("_pril_3_frag_1075596033_IZ.tiff", graphic["sourceArchivePath"])
            self.assertEqual("image/webp", graphic["mime"])
            self.assertNotIn(graphic["path"], paths)
            paths.add(graphic["path"])
        self.assertEqual(5, len(paths))

    def test_b3_through_b7_annex_images_include_both_b4_illustrations(self):
        by_code = {sign["code"]: sign for sign in self.data["signs"]}
        expected = {
            "B 3": {"840281"}, "B 4": {"840283", "1535987"},
            "B 5": {"840287"}, "B 6": {"840289"}, "B 7": {"840291"},
        }
        paths = set()
        for code, file_ids in expected.items():
            graphic = by_code[code]["graphic"]
            self.assertEqual("VERIFIED", graphic["status"])
            self.assertEqual("esbirka-294-2015-2025-07-01-zip", graphic["sourceId"])
            images = [graphic, *graphic.get("additionalImages", [])]
            self.assertEqual(file_ids, {image["sourceFileId"] for image in images})
            for image in images:
                self.assertIn("_pril_3_frag_1075596033_IZ.tiff", image["sourceArchivePath"])
                self.assertEqual("image/webp", image["mime"])
                self.assertNotIn(image["path"], paths)
                paths.add(image["path"])
        self.assertEqual(6, len(paths))

    def test_b8_b9_annex_rows_are_not_interchanged(self):
        by_code = {sign["code"]: sign for sign in self.data["signs"]}
        for code, file_id in (("B 8", "840293"), ("B 9", "840295")):
            graphic = by_code[code]["graphic"]
            self.assertEqual("VERIFIED", graphic["status"])
            self.assertEqual("esbirka-294-2015-2025-07-01-zip", graphic["sourceId"])
            self.assertEqual(file_id, graphic["sourceFileId"])
            self.assertIn("_pril_3_frag_1075596033_IZ.tiff", graphic["sourceArchivePath"])
        self.assertNotEqual(by_code["B 8"]["graphic"]["path"], by_code["B 9"]["graphic"]["path"])

    def test_speed_and_overtaking_graphics_trace_to_own_annex_rows(self):
        by_code = {sign["code"]: sign for sign in self.data["signs"]}
        expected = {"B 20a": "841317", "B 20b": "841319", "B 21a": "841321"}
        paths = set()
        for code, file_id in expected.items():
            graphic = by_code[code]["graphic"]
            self.assertEqual("VERIFIED", graphic["status"])
            self.assertEqual("esbirka-294-2015-2025-07-01-zip", graphic["sourceId"])
            self.assertEqual(file_id, graphic["sourceFileId"])
            self.assertIn("_pril_3_frag_1075596033_IZ.tiff", graphic["sourceArchivePath"])
            self.assertNotIn(graphic["path"], paths)
            paths.add(graphic["path"])

    def test_overtaking_end_and_truck_overtaking_rows_are_distinct(self):
        by_code = {sign["code"]: sign for sign in self.data["signs"]}
        expected = {"B 21b": "841323", "B 22a": "841325", "B 22b": "841327"}
        paths = set()
        for code, file_id in expected.items():
            graphic = by_code[code]["graphic"]
            self.assertEqual("VERIFIED", graphic["status"])
            self.assertEqual("esbirka-294-2015-2025-07-01-zip", graphic["sourceId"])
            self.assertEqual(file_id, graphic["sourceFileId"])
            self.assertIn("_pril_3_frag_1075596033_IZ.tiff", graphic["sourceArchivePath"])
            self.assertNotIn(graphic["path"], paths)
            paths.add(graphic["path"])

    def test_horn_and_turn_bans_trace_to_separate_annex_rows(self):
        by_code = {sign["code"]: sign for sign in self.data["signs"]}
        expected = {"B 23a": "841329", "B 23b": "841331", "B 24a": "841333", "B 24b": "841335"}
        paths = set()
        for code, file_id in expected.items():
            graphic = by_code[code]["graphic"]
            self.assertEqual("VERIFIED", graphic["status"])
            self.assertEqual("esbirka-294-2015-2025-07-01-zip", graphic["sourceId"])
            self.assertEqual(file_id, graphic["sourceFileId"])
            self.assertIn("_pril_3_frag_1075596033_IZ.tiff", graphic["sourceArchivePath"])
            self.assertNotIn(graphic["path"], paths)
            paths.add(graphic["path"])

    def test_remaining_prohibition_and_mandatory_annexes_are_covered(self):
        by_code = {sign["code"]: sign for sign in self.data["signs"]}
        for category, annex in (("prohibition", "_pril_3_"), ("mandatory", "_pril_4_")):
            signs = [sign for sign in self.data["signs"] if sign["category"] == category]
            self.assertEqual(40 if category == "prohibition" else 34, len(signs))
            for sign in signs:
                with self.subTest(code=sign["code"]):
                    self.assertEqual("VERIFIED", sign["graphic"]["status"])
                    self.assertIn(annex, sign["graphic"]["sourceArchivePath"])
                    self.assertTrue(sign["graphic"]["sourceFileId"])
        self.assertEqual("841345", by_code["B 29"]["graphic"]["sourceFileId"])
        self.assertEqual("image/png", by_code["B 29"]["graphic"]["mime"])

    def test_zone_graphics_are_complete_with_current_official_binaries(self):
        zone = [sign for sign in self.data["signs"] if sign["category"] == "information_zone"]
        self.assertEqual(22, len(zone))
        self.assertTrue(all(sign["graphic"].get("path") for sign in zone))
        self.assertTrue(all(sign["graphic"]["status"] == "VERIFIED" for sign in zone))
        for sign in zone:
            graphic = sign["graphic"]
            self.assertTrue(graphic.get("sourceFileId"))
            self.assertTrue(
                "_pril_5-bod_1_" in graphic.get("sourceArchivePath", "")
                or graphic.get("sourceBinaryUrl", "").startswith("https://e-sbirka.gov.cz/")
            )

    def test_traffic_information_is_complete_including_multi_illustration_rows(self):
        traffic = [sign for sign in self.data["signs"] if sign["category"] == "information_traffic"]
        self.assertEqual(47, len(traffic))
        self.assertTrue(all(sign["graphic"].get("path") for sign in traffic))
        self.assertTrue(all(sign["graphic"]["status"] == "VERIFIED" for sign in traffic))
        for sign in traffic:
            graphic = sign["graphic"]
            self.assertTrue(
                "_pril_5-bod_2_" in graphic.get("sourceArchivePath", "")
                or graphic.get("sourceBinaryUrl", "").startswith("https://e-sbirka.gov.cz/")
            )
        other = [sign for sign in self.data["signs"] if sign["category"] == "information_other"]
        self.assertEqual(28, sum(bool(sign["graphic"].get("path")) for sign in other))

    def test_other_information_and_direction_sources_have_verified_provenance(self):
        for category, annex, minimum_covered in (
            ("information_other", "_pril_5-bod_4_", 28),
            ("information_direction", "_pril_5-bod_3_", 59),
        ):
            signs = [sign for sign in self.data["signs"] if sign["category"] == category]
            images = [sign for sign in signs if sign["graphic"].get("path")]
            self.assertGreaterEqual(len(images), minimum_covered)
            for sign in images:
                graphic = sign["graphic"]
                self.assertTrue(graphic["sourceFileId"])
                self.assertTrue(
                    annex in graphic.get("sourceArchivePath", "")
                    or graphic.get("sourceBinaryUrl", "").startswith("https://e-sbirka.gov.cz/")
                )

    def test_supplementary_and_marking_rows_leave_only_real_review_cases(self):
        expectations = (
            ("additional_panel", "_pril_6_", {"E 9"}),
            ("road_marking", "_pril_8-bod_", set()),
        )
        for category, annex, allowed_remaining in expectations:
            signs = [sign for sign in self.data["signs"] if sign["category"] == category]
            remaining = {s["code"] for s in signs if not s["graphic"].get("path")}
            self.assertTrue(remaining.issubset(allowed_remaining))
            for sign in signs:
                if sign["code"] not in remaining:
                    graphic = sign["graphic"]
                    self.assertEqual("VERIFIED", graphic["status"])
                    self.assertTrue(
                        annex in graphic.get("sourceArchivePath", "")
                        or graphic.get("sourceBinaryUrl", "").startswith("https://e-sbirka.gov.cz/")
                    )

    def test_index_wrap_and_variant_kept_without_fabrication(self):
        text = "\f" * 10 + "6.1 B 3      Zákaz vozidel                     07/2019\n" + "\f" * 8
        signs = parse_index(text)
        self.assertEqual(["B 3"], [s["code"] for s in signs])
        self.assertEqual("Zákaz vozidel", signs[0]["titleCs"])

    def test_deterministic_json(self):
        self.assertEqual(canonical_bytes(self.data), (CONTENT / "catalog.json").read_bytes())
        self.assertEqual(canonical_bytes(self.cards), (CONTENT / "curated.json").read_bytes())
        self.assertEqual(canonical_bytes(self.guide), (CONTENT / "guide.json").read_bytes())

    def test_curated_cards_have_provenance_and_all_languages(self):
        validate_cards(self.data, self.cards, self.sources)
        self.assertEqual(408, len(self.cards["cards"]))
        first_five = {s["code"] for s in self.data["signs"]}
        reviewed = {c["code"] for c in self.cards["cards"] if c["code"] in first_five}
        self.assertEqual(first_five, reviewed)

    def test_legal_codes_and_graphic_executions_are_distinct(self):
        signs = {s["code"]: s for s in self.data["signs"]}
        self.assertEqual({"A 31a", "A 31b", "A 31c"}, set(signs) & {"A 31a", "A 31b", "A 31c"})
        self.assertEqual({240, 160, 80}, {
            int(signs[c]["titleCs"].split("(")[1].split()[0]) for c in ("A 31a", "A 31b", "A 31c")
        })
        self.assertEqual(["A 6b-1", "A 6b-2"], signs["A 6b"]["graphicVariantCodes"])
        self.assertNotIn("A 6b-1", signs)
        self.assertEqual(["P 4-1", "P 4-2", "P 4-3"], signs["P 4"]["graphicVariantCodes"])
        self.assertNotIn("P 4-1", signs)
        self.assertEqual(["C 5a", "C 5b"], [code for code in ("C 5a", "C 5b") if code in signs])
        self.assertEqual("C 5", signs["C 5a"]["familyCode"])
        self.assertEqual(["IZ 10a-1", "IZ 10a-2"], signs["IZ 10a"]["graphicVariantCodes"])
        self.assertEqual("VERIFIED", signs["IZ 7a"]["graphic"]["status"])
        self.assertEqual(13, len(signs["B 20a"]["graphicVariantCodes"]))
        self.assertEqual("Zákaz vjezdu vozidel, jejichž šířka přesahuje vyznačenou mez",
                         signs["B 15"]["titleCs"])
        self.assertEqual("Jednosměrný provoz s povoleným provozem cyklistů v protisměru",
                         signs["IP 4c"]["titleCs"])
        self.assertEqual({f"IP 11{suffix}" for suffix in "abcdefg"},
                         set(signs) & {f"IP 11{suffix}" for suffix in "abcdefg"})
        self.assertTrue(all(signs[code]["sourceProvision"].endswith(code)
                            for code in signs if code.startswith("IP ")))
        self.assertEqual({f"IS 22{suffix}" for suffix in "abcdef"},
                         set(signs) & {f"IS 22{suffix}" for suffix in "abcdef"})
        self.assertNotIn("IS 6c", signs)
        self.assertTrue(all(signs[code]["sourceProvision"].endswith(code)
                            for code in signs if code.startswith("IS ")))
        self.assertEqual("Označník zastávky", signs["IJ 4b"]["titleCs"])
        self.assertEqual("Návěst před odbočením na odpočívku", signs["IJ 18b"]["titleCs"])
        self.assertEqual({"E 2a", "E 2b", "E 2c", "E 2d"},
                         set(signs) & {"E 2a", "E 2b", "E 2c", "E 2d"})
        self.assertEqual("Vzdálenost", signs["E 3b"]["titleCs"])
        self.assertTrue(signs["V 10f"]["titleCs"].endswith("osobu těžce pohybově postiženou"))
        self.assertTrue(all(signs[code]["sourceProvision"].endswith(code)
                            for code in signs if code.startswith("V ")))
        self.assertNotIn("S 1", signs)
        self.assertEqual({"S 1a", "S 1b", "S 1c"},
                         set(signs) & {"S 1a", "S 1b", "S 1c"})
        self.assertEqual("S 1", signs["S 1a"]["familyCode"])
        self.assertTrue(all(signs[code]["sourceProvision"].endswith(code)
                            for code in signs if code.startswith("S ")))

    def test_card_cannot_replace_original_title_or_omit_translation(self):
        cards = copy.deepcopy(self.cards)
        cards["cards"][0]["titleCs"] = "Altered"
        with self.assertRaisesRegex(ValueError, "official Czech title"):
            validate_cards(self.data, cards, self.sources)
        cards = copy.deepcopy(self.cards)
        cards["cards"][0]["titleUk"] = ""
        with self.assertRaisesRegex(ValueError, "Incomplete CS/RU/UK"):
            validate_cards(self.data, cards, self.sources)

    def test_question_links_require_existing_official_id(self):
        cards = copy.deepcopy(self.cards)
        linked = next(c for c in cards["cards"] if c["code"] == "B 20a")
        linked["questionLinks"][0]["officialId"] = "not-in-bank"
        with self.assertRaisesRegex(ValueError, "lacks bank evidence"):
            validate_cards(self.data, cards, self.sources, {"RP000001"})
        cards = copy.deepcopy(self.cards)
        linked = next(c for c in cards["cards"] if c["code"] == "B 20a")
        linked["questionLinks"][0]["evidence"]["questionTextCs"] = "Jen nejvyšší rychlost."
        with self.assertRaisesRegex(ValueError, "not explicitly verified"):
            validate_cards(self.data, cards, self.sources)
        cards = copy.deepcopy(self.cards)
        linked = next(c for c in cards["cards"] if c["code"] == "B 20a")
        linked["questionLinks"] *= 2
        with self.assertRaisesRegex(ValueError, "lacks bank evidence"):
            validate_cards(self.data, cards, self.sources)

    def test_question_reference_inventory_is_hash_bound(self):
        refs = json.loads((CONTENT / "official_question_refs.json").read_text(encoding="utf-8"))
        self.assertEqual(1136, refs["officialIdsCount"])
        self.assertEqual(1, full_audit(self.data, self.sources)["linkedQuestionsVerified"])
        self.assertEqual(1, full_audit(self.data, self.sources)["linkedRelationshipsVerified"])

    def test_source_backed_guide_blocks(self):
        validate_guide(self.guide, self.sources)
        self.assertEqual(4, len(self.guide["blocks"]))
        corrupted = copy.deepcopy(self.guide)
        corrupted["blocks"][0]["provision"] = ""
        with self.assertRaisesRegex(ValueError, "Incomplete guide block"):
            validate_guide(corrupted, self.sources)

    def test_legal_appendix_inventory_has_provisions(self):
        legal = [s for s in self.data["signs"] if s["category"] in {"road_marking", "light_signal"}]
        self.assertEqual(79, len(legal))
        self.assertEqual({"V", "S"}, {s["code"].split()[0] for s in legal})
        self.assertTrue(all(s["sourceProvision"].startswith("Příloha č.") for s in legal))


if __name__ == "__main__":
    unittest.main()
