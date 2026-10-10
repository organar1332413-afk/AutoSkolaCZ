import hashlib
import json
from pathlib import Path
import re
import unittest
from xml.etree import ElementTree

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / 'content/learning/first_aid'

def read(name):
    return json.loads((DATA / name).read_text())

class FirstAidIntegrityTest(unittest.TestCase):
    def test_all_104_option_helpers_match_labels_and_preserve_every_number(self):
        questions=read('official-questions.json')['questions']
        helpers={h['id']:h for h in read('question-helpers.json')['questions']}
        from first_aid_option_helpers import OPTIONS
        self.assertEqual(set(helpers),set(OPTIONS))
        self.assertEqual(104,sum(len(h['options']) for h in helpers.values()))
        for q in questions:
            options=helpers[q['id']]['options']
            self.assertEqual({o['label'] for o in q['options']},set(options))
            for o in q['options']:
                for index,tag in enumerate(['ru','uk']):
                    value=options[o['label']][tag]
                    self.assertTrue(value.strip(),(q['id'],o['label'],tag))
                    self.assertEqual(OPTIONS[q['id']][o['label']][index],value)
                    self.assertEqual(re.findall(r'\d+',o['textCs']),re.findall(r'\d+',value),(q['id'],o['label'],tag))
                    self.assertNotRegex(value,r'(?i)правильн|správn')

    def test_etesty_labels_and_compact_captions_in_every_interface_language(self):
        captions=['Formulace ke zkoušce','Formulace ke zkoušce','Формулировка экзамена','Формулювання до іспиту']
        for folder,caption in zip(['values','values-cs','values-ru','values-uk'],captions):
            strings={r.attrib['name']:r.text for r in ElementTree.parse(ROOT/f'app/src/main/res/{folder}/first_aid.xml').getroot()}
            self.assertEqual(caption,strings['aid_exam_warning'])
            for key in ['aid_linked_questions','aid_official_questions']:
                self.assertIn('eTesty MD ČR · %1$d',strings[key]);self.assertNotIn('RP',strings[key])
            self.assertIn('eTesty',strings['aid_no_direct_questions'])
            self.assertNotIn('инструкция',strings['aid_exam_version'])

    def test_exact_contract_and_no_invented_questions(self):
        cards = read('cards.json')['cards']; questions = read('official-questions.json')['questions']
        approved = read('approved-manifest.json')['cards']
        self.assertEqual([f'C{i:02}' for i in range(1,17)], [c['id'] for c in cards])
        self.assertEqual(35, len(questions)); self.assertEqual(35,len({q['id'] for q in questions}))
        self.assertEqual({q['id'] for q in questions},{r for c in cards for r in c['questionIds']})
        for card,reference in zip(cards,approved): self.assertEqual(reference['questionIds'],card['questionIds'])
        self.assertEqual(['C06','C12'],[c['id'] for c in cards if not c['questionIds']])
        self.assertTrue(all(sum(o['correct'] for o in q['options'])==1 for q in questions))
        self.assertEqual(104,sum(len(q['options']) for q in questions))

    def test_both_official_pdf_extracts_match_all_questions_options_keys(self):
        questions=read('official-questions.json')['questions']; canon=lambda s:re.sub(r'\s+','',s)
        verification=read('verification.json')
        self.assertEqual([],verification['changesIn35Questions'])
        for version in ['2026-04-02','2026-10-08']:
            text=(ROOT/f'docs/first-aid/{version}-official-extract.txt').read_text()
            self.assertEqual(35,len(verification['snapshots'][version]['verified']))
            self.assertRegex(verification['snapshots'][version]['pdfSha256'],r'^[0-9a-f]{64}$')
            for q in questions:
                block=re.search(re.escape(q['id'])+r'(.*?)(?:\nKEY ([abc]))',text,re.S)
                self.assertIsNotNone(block,q['id'])
                parts=re.split(r'\s+[abc]\)\s+',block[1])
                self.assertEqual(canon(q['questionCs']),canon(parts[0]),q['id'])
                self.assertEqual([canon(o['textCs']) for o in q['options']],[canon(p) for p in parts[1:]],q['id'])
                self.assertEqual(next(o['label'] for o in q['options'] if o['correct']),block[2].upper())

    def test_all_learning_helpers_are_separate_and_source_hashed(self):
        cards=read('cards.json')['cards']; questions={q['id']:q for q in read('official-questions.json')['questions']}
        helpers=read('question-helpers.json')['questions']
        self.assertEqual(set(questions),{h['id'] for h in helpers})
        for h in helpers:
            q=questions[h['id']]
            self.assertEqual(hashlib.sha256(json.dumps(q,ensure_ascii=False,sort_keys=True).encode()).hexdigest(),h['sourceSha256'])
            self.assertEqual(q['questionCs'],h['question']['cs'])
            self.assertEqual(next(o['textCs'] for o in q['options'] if o['correct']),h['answer']['cs'])
            for value in [h['question'],h['answer']]: self.assertTrue(all(value[tag].strip() for tag in ['cs','ru','uk']))
        for c in cards:
            self.assertNotEqual(c['examSummary']['cs'],c['clinical']['cs'])
            for value in [c['title'],c['summary'],c['examSummary'],c['clinical'],*c['badges']]:
                self.assertEqual({'cs','ru','uk'},set(value)); self.assertTrue(all(v.strip() for v in value.values()))

    def test_image_files_and_originals_match_recorded_hashes(self):
        images=read('image-manifest.json')['images']
        self.assertEqual(16,len(images))
        for image in images:
            optimized=ROOT/'content/learning'/image['assetPath']; source=ROOT/image['source']
            self.assertEqual(image['sha256'],hashlib.sha256(optimized.read_bytes()).hexdigest())
            self.assertEqual(image['sourceSha256'],hashlib.sha256(source.read_bytes()).hexdigest())
            self.assertEqual(b'RIFF',optimized.read_bytes()[:4]);self.assertEqual(b'WEBP',optimized.read_bytes()[8:12])
            self.assertEqual(b'\x89PNG\r\n\x1a\n',source.read_bytes()[:8])
            self.assertGreaterEqual(image['width'],640)

    def test_medical_release_is_not_implied_by_build(self):
        cards=read('cards.json'); images=read('image-manifest.json')
        self.assertFalse(cards['publicationReady']);self.assertFalse(images['publicationReady'])
        for c in cards['cards']:
            self.assertEqual('PENDING_INDEPENDENT_REVIEW',c['clinicalReview'])
            self.assertEqual('DRAFT_REVIEW_REQUIRED',c['translationReview'])
        byid={c['id']:c for c in cards['cards']}
        self.assertIn('100–120',byid['C05']['clinical']['cs']);self.assertIn('5–6',byid['C05']['clinical']['cs'])
        self.assertIn('100/min',byid['C05']['examSummary']['cs']);self.assertIn('4–5',byid['C05']['examSummary']['cs'])
        self.assertIn('nejprve zastav masivní krvácení',byid['C13']['summary']['cs'])
        self.assertIn('zaklínění',byid['C15']['clinical']['cs'])

    def test_ui_localization_has_complete_identical_resource_keys(self):
        expected=None
        for folder in ['values','values-cs','values-ru','values-uk']:
            rows=ElementTree.parse(ROOT/f'app/src/main/res/{folder}/first_aid.xml').getroot()
            names={r.attrib['name'] for r in rows}
            self.assertTrue(all((r.text or '').strip() for r in rows))
            if expected is None:expected=names
            self.assertEqual(expected,names)

    def test_glossary_has_translations_forms_and_unique_ids(self):
        words=read('vocabulary.json')['words']
        self.assertEqual(len(words),len({w['lemma'] for w in words}))
        for w in words:
            self.assertTrue(w['ru'].strip() and w['uk'].strip())
            self.assertTrue(w['forms']);self.assertIn(w['lemma'],w['forms'])

if __name__=='__main__':unittest.main()
