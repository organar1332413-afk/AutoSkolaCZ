"""Refresh snapshots/exports after a manually adjudicated production revision.

This does not infer legal truth or apply proposed corrections. The review
decisions are checked-in data. Existing unresolved findings are retained.
"""
from collections import Counter
import csv
import json
from pathlib import Path
import re
import subprocess

from validate_signs_content_audit import (
    ROOT, AUDIT_DIR, CSV_COLUMNS, ENUMS, expected_displayed, csv_record,
    production_hashes, sha256,
)


def read(path):
    return json.loads(path.read_text(encoding='utf-8'))


def refresh(root=ROOT):
    directory = root / AUDIT_DIR
    decisions = read(directory / 'production-revision-decisions.json')
    base = decisions['base_head']
    previous = json.loads(subprocess.check_output(
        ['git', 'show', base + ':docs/signs-audit/signs-content-audit.json'], cwd=root))
    cards = {c['code']: c for c in read(root / 'content/learning/signs/curated.json')['cards']}
    signs = {s['code']: s for s in read(root / 'content/learning/signs/catalog.json')['signs']}
    translations = {t['cs']: t for t in read(root / 'content/learning/signs/detail-translations.json')['texts']}
    evidence = read(directory / 'official-sources.json')
    changed = {d['sign_code']: d for d in decisions['records']}
    assert len(changed) == decisions['changed_cards']
    rows = previous['records']
    for row in rows:
        code = row['sign_code']
        current = cards[code]
        decision = changed.get(code)
        assert (current != row['original_card']) == bool(decision), code
        if decision:
            row['revision'] = dict(
                base_head=base, previous_overall=row['overall'],
                previous_reasons=row['reasons'],
                adjudicated_before_fix=decision['adjudicated_before_fix'],
                reclassification_reason=decision['reclassification_reason'],
                resolution=decision['resolution'],
                changed_fields=decision['changed_fields'],
                cs_ru_ua_review='REVIEWED',
            )
            assert decision['reviewed_overall'] == 'OK', code
            row.update(overall='OK', legal='LEGAL_OK', pedagogy='PEDAGOGY_OK',
                       reasons=[], issue_kind=None, proposed_correction=[])
            for field, check in row['review_checks'].items():
                if field not in ('official_code', 'czech_title', 'repetition'):
                    check.update(status='REVIEWED_AFTER_CORRECTION',
                                 note=decision['resolution'])
            for section in decision['additional_law_sections']:
                for fragment in evidence['fragments']:
                    if fragment['law'] != '361/2000' or not re.match(
                            r'§ ' + re.escape(section) + r'(\b|\s)', fragment['citation']):
                        continue
                    if len(fragment['text']) <= 20:
                        continue
                    refs = row['official']['driver_duties_and_exceptions_references']
                    if fragment['id'] not in {r['fragment_id'] for r in refs}:
                        refs.append(dict(fragment_id=fragment['id'], url=fragment['url'],
                                         citation=fragment['citation']))
            row['confidence_basis'] = (
                'Актуальное официальное основание повторно получено. Исправленные блоки '
                'и переводы проверены; вывод относится к учебному содержанию, а не к '
                'конкретной дорожной установке.'
            )
            row['note'] = 'Исправление применено к production; решение и прежнее замечание сохранены в revision.'
        else:
            row['note'] = ('Карточка не переписывалась. Сохранено исходное замечание о полезном '
                           'дополнении; отсутствие полного пересказа ПДД не является правовой ошибкой.'
                           if row['overall'] == 'IMPROVE' else
                           'Production не менялся; ранее подтверждённая оценка сохраняется.')
        row['original_card'] = current
        row['displayed_blocks'] = expected_displayed(signs[code], current, translations)
        raw = [current[k] for k in ('meaningCs', 'driverActionsCs', 'simpleCs', 'memoryCs', 'mistakeCs')]
        raw += current['exceptionsCs']
        groups = {text: [k for k in ('meaningCs', 'driverActionsCs', 'simpleCs', 'memoryCs', 'mistakeCs')
                         if current[k] == text] for text in sorted(set(raw)) if raw.count(text) > 1}
        row['review_checks']['repetition'].update(raw_duplicate_groups=groups)
    previous.update(schema_version=2, audited_at='2026-10-06', audited_source_head=base,
                    snapshot_stage='POST_REVISION_WORKTREE',
                    snapshot_note='HEAD denotes the revision base; production_sha256 identifies the exact revised content, without a self-referential commit hash.',
                    production_sha256=production_hashes(root),
                    revision_decisions_sha256=sha256(directory / 'production-revision-decisions.json'))
    previous['methodology']['corrections'] = (
        'Текущие proposed_correction относятся только к оставшимся IMPROVE/RISK/WRONG. '
        'История решённых замечаний сохранена в revision и Git. ua соответствует production uk.'
    )
    previous['counts'] = {field: {value: Counter(r[field] for r in rows)[value]
                                  for value in sorted(values)} for field, values in ENUMS.items()}
    (directory / 'signs-content-audit.json').write_text(
        json.dumps(previous, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    with (directory / 'signs-content-audit.csv').open('w', encoding='utf-8', newline='') as file:
        writer = csv.DictWriter(file, fieldnames=CSV_COLUMNS, lineterminator='\n')
        writer.writeheader()
        for row in rows:
            exported = csv_record(row)
            for key, value in exported.items():
                if isinstance(value, (dict, list)):
                    exported[key] = json.dumps(value, ensure_ascii=False)
            writer.writerow(exported)
    summary = [
        '# Ревизия production-содержания дорожных знаков', '',
        '- audited: 408/408;', '- Дата: 2026-10-06.',
        f'- Основа изменений: `{base}`; ветка `stage4b-premium-ui`.',
        '- Аудит относится к исправленному production: точный снимок связан через production_sha256. HEAD выше — база ревизии, а не выдаваемый за новый commit старый источник.',
        f'- Реально изменено карточек: {len(changed)}. Остальные {408-len(changed)} сохранены.',
        '- PR #11 остаётся Draft; merge и force push не выполняются.', '',
    ]
    for field in ('overall', 'confidence', 'legal', 'pedagogy'):
        summary += [f'| {field.upper()} | Количество |', '|---|---:|']
        summary += [f'| {value} | {previous["counts"][field][value]} |' for value in sorted(ENUMS[field])]
        summary += ['']
    summary += [
        '## Официальные основания и метод', '',
        '- Повторно получены актуальные метаданные и все сохранённые страницы фрагментов e-Sbírka: 294/2015 Sb. (2025-07-01), 361/2000 Sb. (2026-01-01), 201/2012 Sb. (2026-01-01). Ответы побайтно совпали с official-sources.json; дата проверки 2026-10-06.',
        '- Официальный источник определяет факт. Audit proposals не применялись автоматически: чешские тексты составлены заново/сокращены, затем согласованы RU/UA. Все изменённые видимые блоки и raw-повторы перепроверены.',
        '- Co znamená объясняет знак; Co má řidič udělat содержит действие; Zapamatuj si — короткую мысль; условия и исключения вынесены в отдельные короткие Další informace. Архитектура и фильтрация повторов не менялись.',
        '- LEGAL_OK не означает полного пересказа ПДД. Некоторые прежние RISK переклассифицированы как недостаток полноты; полезные дополнения всё равно внесены. История решения каждого изменения сохранена в production-revision-decisions.json и поле revision.',
        '- Три WRONG исправлены: A 12a — нет безусловного права пешехода; B 22a — точная группа тракторов и обязательная правая полоса; IS 6d — запрещённое размещение над проезжей частью.',
        '- P 4: прежний аудит пропустил двусмысленность. До исправления оценка пересмотрена на RISK; новая мнемоника прямо говорит «Přednost musíte dát vy».',
        '- Ограничение области: содержание карточки, а не конкретные дорожные установки, тарифы или проверка изображений. Функции приложения и данные словаря не менялись.',
        '- Validator доказывает структуру, покрытие, связи с источниками и соответствие снимков; он не заменяет содержательную правовую проверку.', '',
    ]
    for verdict in ('RISK', 'WRONG', 'UNVERIFIED'):
        summary += [f'## Все {verdict}', '']
        findings = [r for r in rows if r['overall'] == verdict]
        if findings:
            summary += ['| Код | Причина | Основание |', '|---|---|---|']
            summary += [f'| {r["sign_code"]} | {"; ".join(r["reasons"])} | {r["official"]["citation_url"]} |' for r in findings]
        else:
            summary += ['Нет.']
        summary += ['']
    summary += ['## Переклассификация прежних RISK', '', '| Код | Объяснение |', '|---|---|']
    summary += [f'| {d["sign_code"]} | {d["reclassification_reason"]} |'
                for d in decisions['records'] if d['reclassification_reason']]
    summary += ['', '## Оставшиеся IMPROVE', '', '| Код | Конкретная полезная причина |', '|---|---|']
    summary += [f'| {r["sign_code"]} | {"; ".join(r["reasons"])} |' for r in rows if r['overall'] == 'IMPROVE']
    summary += ['', '## Главные повторяющиеся проблемы', '',
                '- Указание «соблюдайте особые правила» заменено конкретными действиями там, где без них теряется учебный смысл.',
                '- Условия и исключения: масса/конструкционная скорость, собственные и соседние полосы, тип сигнала, момент включения, место установки.',
                '- Ложные права или советы как требования: пешеходы вне перехода, P 4, P+R, парковочная разметка.',
                '- RU/UA: область запрета въезда/поворота, обязательная полоса, ожидание посреди перехода, виньетка и mýtné.',
                '- Цветовые мнемоники и повторение смысла. Исправлены точечно; хорошие карточки ради стиля не переписывались.', '',
                '## Синхронизация словаря', '',
                '- По разрешению пользователя добавлены 124 необходимые lexical entries и 330 форм: 266 новых форм текущего корпуса и 64 канонических формы новых лемм. Итого 1302 записи / 3058 форм.',
                '- Все 1178 прежних IDs, переводы и 2728 форм сохранены. Историческое происхождение исчезнувших из карточек форм зафиксировано отдельно в content/dictionary/retained-sign-forms.json; оно не является источником текущего учебного/правового смысла.',
                '- Строгое покрытие текущего production-корпуса: отсутствующие lookup, RU/UA translation и context values — 0; конфликты и дубли — 0. Числовые corpus fixtures обновлены, требования полного покрытия не ослаблены.',
                '- Runtime dictionary, loader, tap-to-translate, UI и Room schema не менялись. Точный статус проверок, включая ограничения локального assembleDebug, сохранён в validation-status.json.', '',
                '## Запуск проверок', '', '```sh',
                'python tools/validate_signs_content_audit.py',
                "python -m unittest discover -s tools -p 'test_signs_content_audit.py' -v",
                "python -m unittest discover -s tools -p 'test_*.py' -v",
                './gradlew :app:assembleDebug', '```', '']
    (directory / 'signs-content-audit-summary.md').write_text('\n'.join(summary), encoding='utf-8')
    print(json.dumps(previous['counts'], ensure_ascii=False))


if __name__ == '__main__':
    refresh()
