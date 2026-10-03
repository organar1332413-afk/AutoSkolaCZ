"""Compile explicitly authored lemma families; wildcard expansion is build-time only.
Runtime uses exact bundled forms. Every expansion is audited for conflicting mappings.
"""
import fnmatch,json,sys,hashlib
from pathlib import Path
from sign_vocabulary import ROOT,ASSET,corpus,normalize,audit
SOURCE=ROOT/'content/dictionary/sign-lexicon.tsv'
def build():
    _,corpus_forms=corpus();words=[];index={}
    # Preserve IDs/definitions already saved on devices.
    base=json.loads((ROOT/'content/dictionary/base-v1.json').read_text())
    for w in base:
        words.append(w)
        for f in w['forms']:index[normalize(f)]=w['id']
    for line_no,line in enumerate(SOURCE.read_text().splitlines(),1):
        if not line or line.startswith('#'):continue
        lemma,patterns,ru,uk,ru_meaning,uk_meaning=line.split('|')
        assert all(v.strip() for v in [lemma,patterns,ru,uk,ru_meaning,uk_meaning]),line_no
        forms=sorted({f for f in corpus_forms if any(fnmatch.fnmatchcase(f,p) for p in patterns.split())}|{lemma})
        identifier='sign-word-'+hashlib.sha256(lemma.encode()).hexdigest()[:16]
        for f in forms:
            assert f not in index, f'Line {line_no}: {f} conflicts with {index.get(f)} ({lemma})'
            index[f]=identifier
        example=next((corpus_forms[f][0]['cs'] for f in forms if f in corpus_forms),'')
        assert example, f'Unused lemma: {lemma}'
        words.append(dict(id=identifier,lemma=lemma,context='road_traffic',exampleCs=example,forms=forms,
            translations=[dict(locale=tag,translation=t,meaning=m,exampleTranslation='') for tag,t,m in [('ru',ru,ru_meaning),('uk',uk,uk_meaning)]]))
    return words
if __name__=='__main__':
    words=build();ASSET.write_text(json.dumps(words,ensure_ascii=False,indent=2)+'\n')
    metrics,missing,forms=audit(words);print(json.dumps(metrics,ensure_ascii=False,indent=2))

