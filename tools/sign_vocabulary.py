"""Finite Signs learning corpus; parity is tested against the production Kotlin adapter/tokenizer.
Only title, meaning, driver action, selected memory and expandable additional copy are tappable.
Source/legal metadata, codes, URLs, numeric identifiers and SI/single-letter labels are not words.
"""
import json,re,unicodedata
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
ASSET=ROOT/'app/src/main/assets/content/dictionary-v1.json'
WORD=re.compile(r'[^\W\d_]+(?:[-’\'][^\W\d_]+)*',re.UNICODE)
EXCLUDED=re.compile(r'(?i:https?)://\S+|\b(?:A|B|C|D|E|IP|IS|IZ|IJ|P|S|V|Z)\s*\d+[a-z]?\b')
LABELS={'b','d','e','h','m','mm','km','n','p','r','t'}
def normalize(token):
    match=WORD.search(token)
    return unicodedata.normalize('NFC',match.group() if match else '').lower()
def tokens(text):
    excluded=[m.span() for m in EXCLUDED.finditer(text)]
    for m in WORD.finditer(text):
        start,end=m.span()
        if any(start<b and end>a for a,b in excluded):continue
        if (start and (text[start-1].isdigit() or text[start-1]=='_')) or (end<len(text) and (text[end].isdigit() or text[end]=='_')):continue
        if normalize(m.group()) in LABELS:continue
        yield normalize(m.group())
def fields():
    inventory=json.loads((ROOT/'content/learning/signs/catalog.json').read_text())['signs']
    cards={c['code']:c for c in json.loads((ROOT/'content/learning/signs/curated.json').read_text())['cards']}
    for sign in inventory:
        c=cards[sign['code']]
        memory=next((c.get(k) for k in ['memoryCs','mistakeCs'] if c.get(k) and c[k] not in [c.get('meaningCs'),c.get('driverActionsCs')]),None)
        extra=list(dict.fromkeys([c[k] for k in ['simpleCs','memoryCs','mistakeCs'] if c.get(k)]+c.get('exceptionsCs',[])))
        extra=[v for v in extra if v not in [c.get('meaningCs'),c.get('driverActionsCs'),memory]]
        for key,value in [('titleCs',sign['titleCs']),('meaningCs',c.get('meaningCs')),('driverActionsCs',c.get('driverActionsCs')),('memoryAdvice',memory)]+[(f'additional-{i}',v) for i,v in enumerate(extra)]:
            if value:yield dict(code=sign['code'],field=key,cs=value)
def corpus():
    result={}
    all_fields=list(fields())
    for row in all_fields:
        for token in tokens(row['cs']):result.setdefault(token,[]).append(row)
    return all_fields,result
def audit(words):
    rows,forms=corpus();index={};duplicates=[];conflicts=[];lemmas=[]
    for w in words:
        lemma=normalize(w['lemma']);lemmas.append(lemma)
        seen=set()
        for f in w['forms']:
            key=normalize(f)
            if key in seen:duplicates.append(key)
            seen.add(key)
        for f in seen|{lemma}:
            if f in index and index[f]['id']!=w['id']:conflicts.append(f)
            index[f]=w
    missing={k:[] for k in ['lookup','ru_translation','ru_explanation','ua_translation','ua_explanation']}
    for token,occurrences in forms.items():
        word=index.get(token); texts={t['locale']:t for t in word['translations']} if word else {}
        if not word:missing['lookup'].append(token)
        for label,locale in [('ru','ru'),('ua','uk')]:
            for part,field in [('translation','translation'),('explanation','meaning')]:
                if not texts.get(locale,{}).get(field,'').strip():missing[f'{label}_{part}'].append(token)
    metrics={'sign_cards_scanned':len({r['code'] for r in rows}),'tappable_text_fields_scanned':len(rows),'token_occurrences':sum(map(len,forms.values())),'unique_normalized_forms':len(forms),'dictionary_lemmas':len(words),'dictionary_surface_forms':sum(len(w['forms']) for w in words),'duplicate_lemmas':len(lemmas)-len(set(lemmas)),'duplicate_forms':len(duplicates),'conflicting_mappings':len(conflicts),**{f'missing_{k}':len(v) for k,v in missing.items()}}
    return metrics,missing,forms
if __name__=='__main__':
    import argparse
    p=argparse.ArgumentParser();p.add_argument('--asset',type=Path,default=ASSET);p.add_argument('--output',type=Path);p.add_argument('--missing',action='store_true');args=p.parse_args()
    metrics,missing,forms=audit(json.loads(args.asset.read_text()))
    result={'metrics':metrics,'missing':{k:[dict(token=t,**forms[t][0]) for t in v] for k,v in missing.items()}}
    print(json.dumps(result if args.missing else metrics,ensure_ascii=False,indent=2))
    if args.output:args.output.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
