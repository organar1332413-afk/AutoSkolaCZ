"""Validate audit coverage, provenance and exports, without changing production.

This is a structural guard. Legal truth is established by the cited review, not
by this validator. Standard library only; runs in existing Python CI discovery.
"""
from __future__ import annotations

import argparse
from collections import Counter
import csv
import hashlib
import json
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
AUDIT_DIR = Path('docs/signs-audit')
SOURCE_HEAD = '6c24b6b99b610c4fb06dd30015df0793e6a9cbad'
ENUMS = {
    'legal': {'LEGAL_OK', 'LEGAL_RISK', 'LEGAL_WRONG', 'UNVERIFIED'},
    'pedagogy': {'PEDAGOGY_OK', 'PEDAGOGY_IMPROVE'},
    'overall': {'OK', 'IMPROVE', 'RISK', 'WRONG', 'UNVERIFIED'},
    'confidence': {'HIGH', 'MEDIUM', 'LOW'},
}
LEGAL_FOR_OVERALL = dict(OK='LEGAL_OK', IMPROVE='LEGAL_OK',
                         RISK='LEGAL_RISK', WRONG='LEGAL_WRONG', UNVERIFIED='UNVERIFIED')
CHECKS = {'official_code', 'czech_title', 'official_meaning', 'driver_duties',
          'conditions_exceptions', 'meaning', 'action', 'memory', 'additional',
          'ru_semantics', 'ua_semantics', 'repetition'}
CSV_COLUMNS = ['sign_code', 'production_title_cs', 'official_title_cs', 'legal',
               'pedagogy', 'overall', 'confidence', 'reason', 'issue_kind',
               'source_urls', 'official_meaning_and_conditions_cs',
               'original_card_json', 'displayed_blocks_json', 'review_checks_json',
               'proposed_correction_json', 'revision_json']


class AuditValidationError(ValueError):
    pass


def require(condition, message):
    if not condition:
        raise AuditValidationError(message)


def sha256(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def read_json(path):
    # Silent repeated JSON keys could hide duplicate records/evidence bindings.
    def unique(pairs):
        result = {}
        for key, value in pairs:
            require(key not in result, f'Duplicate JSON key: {key}')
            result[key] = value
        return result
    return json.loads(path.read_text(encoding='utf-8'), object_pairs_hook=unique)


def text_present(value):
    return isinstance(value, str) and bool(value.strip())


def normalized_title(value):
    return re.sub(r'\s+', ' ', value.replace('–', '-').replace(' ,', ',')
                  .replace('stá ní', 'stání')).strip()


def production_hashes(root):
    return {str(p.relative_to(root)): sha256(p)
            for p in sorted((root / 'content/learning/signs').rglob('*')) if p.is_file()}


def expected_displayed(sign, card, translations):
    def tr(text, title=False):
        if title:
            return dict(cs=text, ru=card['titleRu'], ua=card['titleUk'])
        if text == card['meaningCs']:
            return dict(cs=text, ru=card['ru'], ua=card['uk'])
        require(text in translations, f'Missing production helper: {text}')
        return dict(cs=text, ru=translations[text]['ru'], ua=translations[text]['uk'])
    memory = next((card[k] for k in ('memoryCs', 'mistakeCs')
                   if card[k] not in (card['meaningCs'], card['driverActionsCs'])), None)
    additional = list(dict.fromkeys([card['simpleCs'], card['memoryCs'], card['mistakeCs']]
                                   + card['exceptionsCs']))
    additional = [s for s in additional
                  if s not in (card['meaningCs'], card['driverActionsCs'], memory)]
    return dict(title=tr(sign['titleCs'], True), meaning=tr(card['meaningCs']),
                action=tr(card['driverActionsCs']), memory=tr(memory) if memory else None,
                additional=[tr(s) for s in additional])


def validate_core(audit, catalog, cards, current_hashes, evidence, translations):
    require(audit.get('schema_version') == 2, 'Unknown audit schema')
    require(audit.get('audited_source_head') == SOURCE_HEAD, 'Wrong audited source HEAD')
    require(audit.get('snapshot_stage') == 'POST_REVISION_WORKTREE',
            'Revision audit must identify the exact revised worktree snapshot')
    require(audit.get('branch') == 'stage4b-premium-ui', 'Wrong audit branch')
    require(audit.get('expected_count') == audit.get('audited_count') == 408,
            'Audit metadata must state 408 records')
    require(audit.get('production_sha256') == current_hashes,
            'Production files differ from audited SHA-256 snapshot')
    require(len(catalog) == len(cards) == 408, 'Production must contain 408 cards')
    expected = [s['code'] for s in catalog]
    require(len(set(expected)) == len({c['code'] for c in cards}) == 408,
            'Duplicate production codes')
    require(set(expected) == {c['code'] for c in cards}, 'Production code sets differ')
    rows = audit.get('records')
    require(isinstance(rows, list) and len(rows) == 408, 'Exactly 408 audit records required')
    actual = [r.get('sign_code') for r in rows]
    require(len(set(actual)) == 408, 'Duplicate audit sign codes')
    require(set(actual) == set(expected),
            f'Missing/unknown audit codes: {set(expected)-set(actual)} / {set(actual)-set(expected)}')
    require(actual == expected, 'Audit ordering must match production catalog')
    bindings = evidence.get('sign_bindings', {})
    require(set(bindings) == set(expected), 'Official evidence must bind all 408 codes')
    fragments = {f['id']: f for f in evidence.get('fragments', [])}
    require(len(fragments) == len(evidence.get('fragments', [])), 'Duplicate evidence fragment IDs')
    require(evidence.get('metadata', {}).get('294/2015', {}).get('typZneni') == 'AKTUALNI',
            'Decree source was not recorded as current')
    require(evidence.get('metadata', {}).get('361/2000', {}).get('typZneni') == 'AKTUALNI',
            'Traffic law source was not recorded as current')
    by_code = {c['code']: c for c in cards}
    by_sign = {s['code']: s for s in catalog}
    for r in rows:
        code = r['sign_code']
        for field, allowed in ENUMS.items():
            require(r.get(field) in allowed, f'{code}: invalid/missing {field}')
        require(r['legal'] == LEGAL_FOR_OVERALL[r['overall']], f'{code}: inconsistent LEGAL/OVERALL')
        require(r['pedagogy'] == ('PEDAGOGY_OK' if r['overall'] == 'OK' else 'PEDAGOGY_IMPROVE'),
                f'{code}: inconsistent PEDAGOGY/OVERALL')
        require(text_present(r.get('confidence_basis')), f'{code}: missing confidence basis')
        require(r.get('original_card') == by_code[code], f'{code}: wrong production card snapshot')
        require(r.get('production_title_cs') == by_sign[code]['titleCs'], f'{code}: wrong runtime title')
        require(r.get('displayed_blocks') == expected_displayed(by_sign[code], by_code[code], translations),
                f'{code}: displayed blocks do not match production rendering/translation rules')
        checks = r.get('review_checks', {})
        require(set(checks) == CHECKS, f'{code}: missing review dimensions')
        for name, check in checks.items():
            require(text_present(check.get('status')) and text_present(check.get('note')),
                    f'{code}: empty review dimension {name}')
        reasons = r.get('reasons')
        require(isinstance(reasons, list) and all(text_present(x) for x in reasons),
                f'{code}: malformed reasons')
        if r['overall'] in {'IMPROVE', 'RISK', 'WRONG', 'UNVERIFIED'}:
            require(bool(reasons), f'{code}: {r["overall"]} requires a reason')
        correction = r.get('proposed_correction')
        require(isinstance(correction, list), f'{code}: malformed proposed correction')
        if r['overall'] in {'IMPROVE', 'RISK', 'WRONG'}:
            require(bool(correction), f'{code}: proposed correction required')
        for c in correction:
            require(c.get('operation') in {'add', 'replace'}, f'{code}: invalid correction operation')
            require(text_present(c.get('block')), f'{code}: correction target missing')
            for lang in ('cs', 'ru', 'ua'):
                require(text_present(c.get(lang)), f'{code}: missing corrected {lang}')
            for lang in ('ru', 'ua'):
                require(bool(re.search(r'[А-Яа-яІіЇїЄє]', c[lang])), f'{code}: non-Cyrillic {lang} correction')
        official = r.get('official', {})
        binding = bindings[code]
        require(official.get('code') == binding.get('code') == code, f'{code}: wrong official code')
        require(official.get('title_cs') == binding.get('title'), f'{code}: official title differs from evidence')
        require(normalized_title(by_sign[code]['titleCs']) == normalized_title(binding['title']),
                f'{code}: unreported Czech title mismatch')
        require(official.get('meaning_and_conditions_cs') == binding.get('text'), f'{code}: altered official wording')
        require(official.get('citation_url') == binding.get('url'), f'{code}: wrong official citation')
        require(official.get('fragment_id') == str(binding.get('fragment_id')), f'{code}: wrong official fragment')
        require(official['fragment_id'] in fragments, f'{code}: missing official fragment evidence')
        require(official['citation_url'].startswith('https://e-sbirka.gov.cz/sb/'), f'{code}: unofficial source')
        refs = official.get('driver_duties_and_exceptions_references')
        require(isinstance(refs, list) and refs, f'{code}: duties/conditions references missing')
        for ref in refs:
            fragment = fragments.get(ref.get('fragment_id'))
            require(fragment is not None and ref.get('url') == fragment['url']
                    and ref.get('citation') == fragment['citation'], f'{code}: invalid duties reference')
    for field in ENUMS:
        counts = dict(Counter(r[field] for r in rows))
        supplied = audit.get('counts', {}).get(field, {})
        require(all(supplied.get(k, 0) == counts.get(k, 0) for k in ENUMS[field])
                and set(supplied) <= ENUMS[field], f'Incorrect {field} counts')
    return rows


def csv_record(r):
    return dict(sign_code=r['sign_code'], production_title_cs=r['production_title_cs'],
                official_title_cs=r['official']['title_cs'], legal=r['legal'], pedagogy=r['pedagogy'],
                overall=r['overall'], confidence=r['confidence'], reason=' | '.join(r['reasons']),
                issue_kind=r['issue_kind'] or '', source_urls=' | '.join(dict.fromkeys(
                    [r['official']['citation_url']] + [x['url'] for x in r['official']['driver_duties_and_exceptions_references']])),
                official_meaning_and_conditions_cs=r['official']['meaning_and_conditions_cs'],
                original_card_json=r['original_card'], displayed_blocks_json=r['displayed_blocks'],
                review_checks_json=r['review_checks'], proposed_correction_json=r['proposed_correction'],
                revision_json=r.get('revision', {}))


def validate_csv(rows, csv_rows, columns):
    require(columns == CSV_COLUMNS, 'CSV columns differ from schema')
    require(len(csv_rows) == 408, 'CSV must contain 408 rows')
    for r, exported in zip(rows, csv_rows):
        actual = dict(exported)
        for field in ('original_card_json', 'displayed_blocks_json', 'review_checks_json', 'proposed_correction_json', 'revision_json'):
            try:
                actual[field] = json.loads(actual[field])
            except (ValueError, TypeError) as exc:
                raise AuditValidationError(f'{r["sign_code"]}: invalid CSV JSON field {field}') from exc
        require(actual == csv_record(r), f'{r["sign_code"]}: CSV differs from JSON')


def validate_summary(audit, summary):
    require('audited: 408/408;' in summary, 'Summary audited count missing')
    require(SOURCE_HEAD in summary, 'Summary source HEAD missing')
    for field in ENUMS:
        for verdict in ENUMS[field]:
            count = audit['counts'][field].get(verdict, 0)
            require(f'| {verdict} | {count} |' in summary, f'Summary count differs: {verdict}')
    for verdict in ('RISK', 'WRONG', 'UNVERIFIED'):
        match = re.search(r'^## Все '+verdict+r'\n(.*?)(?=^## |\Z)', summary, re.M | re.S)
        require(match is not None, f'Summary {verdict} list missing')
        codes = re.findall(r'^\| ((?:A|B|C|E|IJ|IP|IS|IZ|P|S|V) \d+[a-z]?) \|', match[1], re.M)
        expected = [r['sign_code'] for r in audit['records'] if r['overall'] == verdict]
        require(codes == expected, f'Summary {verdict} list differs from audit records')
        if not expected:
            require('Нет.' in match[1], f'Summary empty {verdict} list must be explicit')


def load_bundle(root=ROOT):
    path = root / AUDIT_DIR
    return dict(audit=read_json(path / 'signs-content-audit.json'),
                catalog=read_json(root / 'content/learning/signs/catalog.json')['signs'],
                cards=read_json(root / 'content/learning/signs/curated.json')['cards'],
                current_hashes=production_hashes(root), evidence=read_json(path / 'official-sources.json'),
                translations={t['cs']: t for t in read_json(root / 'content/learning/signs/detail-translations.json')['texts']})


def validate(root=ROOT):
    bundle = load_bundle(root)
    rows = validate_core(**bundle)
    path = root / AUDIT_DIR
    require(bundle['audit']['official_evidence_sha256'] == sha256(path / 'official-sources.json'),
            'Official evidence SHA-256 differs')
    decisions = read_json(path / 'production-revision-decisions.json')
    require(bundle['audit']['revision_decisions_sha256'] == sha256(path / 'production-revision-decisions.json'),
            'Revision decisions SHA-256 differs')
    validate_revision(rows, decisions)
    for image in bundle['evidence'].get('images', {}).values():
        require(sha256(path / image['path']) == image['sha256'], 'Official source image changed')
    with (path / 'signs-content-audit.csv').open(encoding='utf-8', newline='') as f:
        reader = csv.DictReader(f)
        validate_csv(rows, list(reader), reader.fieldnames)
    validate_summary(bundle['audit'], (path / 'signs-content-audit-summary.md').read_text(encoding='utf-8'))
    return bundle['audit']['counts']


def validate_revision(rows, decisions):
    require(decisions.get('base_head') == SOURCE_HEAD, 'Wrong revision base HEAD')
    edits = decisions.get('records', [])
    codes = [e.get('sign_code') for e in edits]
    require(len(codes) == len(set(codes)) == decisions.get('changed_cards'),
            'Duplicate/missing revision decisions')
    revised = {r['sign_code']: r for r in rows if r.get('revision')}
    require(set(codes) == set(revised), 'Revision records differ from decisions')
    for edit in edits:
        code = edit['sign_code']
        row = revised[code]
        history = row['revision']
        require(edit.get('cs_ru_ua_review') == history.get('cs_ru_ua_review') == 'REVIEWED',
                f'{code}: revision translations were not reviewed')
        require(edit.get('reviewed_overall') == row['overall'], f'{code}: verdict differs from revision decision')
        require(text_present(edit.get('resolution')) and edit['resolution'] == history.get('resolution'),
                f'{code}: revision resolution missing')
        require(edit.get('previous_overall') == history.get('previous_overall'),
                f'{code}: previous verdict differs')
        before = edit.get('before_card', {})
        current = row['original_card']
        changes = [k for k, value in current.items() if before.get(k) != value]
        require(changes and changes == edit.get('changed_fields') == history.get('changed_fields'),
                f'{code}: revision field list differs from actual production change')
        if history.get('adjudicated_before_fix') != history.get('previous_overall'):
            require(text_present(history.get('reclassification_reason')) or code == 'P 4',
                    f'{code}: reclassification needs an explanation')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, default=ROOT)
    args = parser.parse_args()
    try:
        counts = validate(args.root.resolve())
    except (AuditValidationError, KeyError, TypeError, OSError, json.JSONDecodeError) as exc:
        parser.exit(1, f'Signs content audit FAILED: {exc}\n')
    print('Signs content audit PASS: 408/408 unique production codes; verdicts, confidence, reasons, '
          'CS/RU/UA proposals, official evidence, production hashes, CSV and summary verified.')
    print(json.dumps(counts, ensure_ascii=False, sort_keys=True))


if __name__ == '__main__':
    main()
