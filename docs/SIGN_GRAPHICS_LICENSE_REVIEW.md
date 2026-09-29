# Sign graphic reuse review — 2026-09-27

This is a source-by-source release decision for the **graphics**, not a conclusion
that a publicly downloadable PDF grants permission to republish every image
inside it. The Czech legal codes and official titles are separate from the
binary artwork. The completed batches bundle **275 illustrations for 271 legal-code
cards** from the consolidated e-Sbírka ZIP dated 1 July 2025. Each WebP's
decoded pixels match its official TIFF; source file ID, TIFF SHA-256 and
archive SHA-256 are retained. The remaining 137 cards have no image,
including two low-emission-zone codes requiring a separate version check.
A completed text card does not change its graphic release status.

| Source | Publisher / owner | Evidence and legal status | Reuse decision |
| --- | --- | --- | --- |
| Vyhláška č. 294/2015 Sb., consolidated annexes | Czech state, e-Sbírka | Annexes 1–9 specify signs and illustrations. Act No. 121/2000 Sb., § 3(a) excludes legal regulations as official works from copyright protection. [Consolidated ZIP](https://e-sbirka.gov.cz/sb/2015/294/2025-07-01.zip), [decree](https://e-sbirka.gov.cz/sb/2015/294), [copyright law](https://e-sbirka.gov.cz/sb/2000/121). | The first 275 images have individual TIFF-to-lossless-local-image pixel and annex-row checks from the official legal annex. Remaining images require the same checks. `VERIFIED` is an individual source/fidelity decision, not a claim that all variants or the module are complete. |
| Original 294/2015 PDF of the Collection of Laws | Ministry of the Interior / Czech state | [Parliament's reference](https://www.psp.cz/sqw/sbirka.sqw?cz=294&r=2015) links a 103 MB official PDF. It is the original act, not the consolidated 2025 graphics. | Suitable evidence for original illustrations only; amendment graphics require matching effective versions. No extracted assets bundled. |
| 386/2023 Sb. and 205/2025 Sb. | Czech state, e-Sbírka | [2023 amendment](https://e-sbirka.gov.cz/sb/2023/386) and [2025 amendment](https://e-sbirka.gov.cz/sb/2025/205); the Ministry [explains the 2025 changes](https://md.gov.cz/Media/Media-a-tiskove-zpravy/TEST). Transitional rules permit earlier installations for specified periods; a surviving roadside graphic is not necessarily the current catalog illustration. | Reconcile changed illustrations with the original act before release; current indexed VL sheets alone do not establish the consolidated legal artwork. |
| VL 6.1 (2019) and change 1 (2025), VL 6.2 | Ministry-approved technical graphic sheets published by ŘSD | [VL listing](https://pjpk.rsd.cz/vzorove-listy-staveb-pozemnich-komunikaci-vl/) has a CC BY-SA 4.0 footer. The PDF cover says the 2019 document was approved by the Ministry (ref. 56/2019-120-TN/1), and the PDF is a separate technical specification, **not itself the decree**. The footer is not explicit evidence that all embedded PDF artwork is covered or that the publisher can sublicense third-party elements. | **LICENSE_REVIEW_REQUIRED** for copying VL PDF sheets. Ask the publisher or obtain explicit item-level rights / use verified normative illustrations. Site-footer license alone is insufficient for bundling this PDF's images as unrestricted app assets. |
| Ministry / ŘSD website preview images | Respective public authority or contractor | Public display does not by itself establish binary reuse rights, source version, or equivalence with the legal annex. | **LICENSE_REVIEW_REQUIRED** unless an image can be tied to the promulgated graphic or an express applicable license. |
| Our own vector redrawings from legal shapes | AutoSkolaCZ authors, subject to checking normative fidelity | Creating an independent SVG can avoid copying VL-specific drawings, but the legal proportions, pictograms, colors and variants must be checked against current promulgated annexes. | Possible future route. No approximate redrawings should appear in an exam teaching card as an official sign. |

The remaining graphics require per-code and per-variant matching to the
consolidated legal annex. Keep the VL material under separate rights review if
it is ever used. Record every verified image path, source file ID, SHA-256 and
effective version. Do not infer variant completeness from 408 legal codes.

The 2025 low-emission-zone illustrations for `IZ 7a` and `IZ 7b` need a
separate version check. Their old 2019 VL sheets cannot be presented as the
current legal designs just because the legal codes survived.

The source registry cites the legal rules governing a sign. It does **not**
grant a blanket license to a publisher's rendition or prove that the indexed
347 VL families are the complete set of current legal and graphic variants.
