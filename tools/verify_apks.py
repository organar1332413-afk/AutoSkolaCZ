#!/usr/bin/env python3
"""Verify built variant boundaries with debug as a positive control. No device claims."""
from pathlib import Path
import os, subprocess, zipfile, hashlib, json
root=Path(__file__).resolve().parents[1]
sdk=Path(os.environ['ANDROID_HOME']);build_tools=sdk/'build-tools/35.0.0'
result={}
for variant,filename in [('debug','app-debug.apk'),('release','app-release-unsigned.apk')]:
    apk=root/f'app/build/outputs/apk/{variant}/{filename}'
    with zipfile.ZipFile(apk) as z:
        assert z.testzip() is None
        dex=b''.join(z.read(n) for n in z.namelist() if n.endswith('.dex'))
        synthetic_markers = [
            b'https://etesty.md.gov.cz/TEST_ONLY',
            b'TEST_ONLY:',
            b'ExamEngineTest',
            b'RoomPersistenceTest',
            b'ReviewPolicyTest',
        ]
        leaked = [marker.decode('ascii') for marker in synthetic_markers if marker in dex]
        assert not leaked, f'Synthetic test fixtures leaked into APK: {leaked}'
        markers={name:name.encode() in dex for name in ['DeveloperController','resetOnboardingForDevelopment']}
        dictionary_path='assets/content/dictionary-v1.json'
        assert z.namelist().count(dictionary_path)==1, f'{variant}: bundled dictionary missing/duplicated'
        dictionary_bytes=z.read(dictionary_path)
        source_bytes=(root/'app/src/main/assets/content/dictionary-v1.json').read_bytes()
        assert dictionary_bytes==source_bytes, f'{variant}: packaged dictionary differs from source'
        dictionary=json.loads(dictionary_bytes)
        assert dictionary and all({t['locale'] for t in w['translations']}=={'ru','uk'} for w in dictionary)
        assert all(t['translation'].strip() for w in dictionary for t in w['translations'])
        assert b'BundledDictionary' in dex, f'{variant}: common dictionary initializer missing'
        samples=[n for n in z.namelist() if n.startswith('assets/content/') and n!=dictionary_path]
    resources=subprocess.check_output([str(build_tools/'aapt2'),'dump','resources',str(apk)],text=True)
    badging=subprocess.check_output([str(build_tools/'aapt'),'dump','badging',str(apk)],text=True)
    permissions=subprocess.check_output([str(build_tools/'aapt'),'dump','permissions',str(apk)],text=True)
    assert 'android.permission.INTERNET' not in permissions
    debuggable='application-debuggable' in badging
    developer_resources='dev_title' in resources
    assert debuggable==(variant=='debug')
    assert developer_resources==(variant=='debug')
    assert all(markers.values()) if variant=='debug' else not any(markers.values())
    assert set(samples)=={'assets/content/sample-v1.json','assets/content/lesson-demo.json'} if variant=='debug' else not samples
    subprocess.run([str(build_tools/'zipalign'),'-c','-P','16','4',str(apk)],check=True)
    signature=None
    if variant=='debug':
        signature=subprocess.check_output([str(build_tools/'apksigner'),'verify','--verbose','--print-certs',str(apk)],text=True)
    result[variant]={'apk':filename,'size_bytes':apk.stat().st_size,'sha256':hashlib.sha256(apk.read_bytes()).hexdigest(),'debuggable':debuggable,'developer_resources':developer_resources,'developer_class_markers':markers,'sample_assets':samples,'dictionary_asset':dictionary_path,'dictionary_words':len(dictionary),'dictionary_forms':sum(len(w['forms']) for w in dictionary),'dictionary_sha256':hashlib.sha256(dictionary_bytes).hexdigest(),'badging':badging.splitlines()[:4],'alignment_16k_verified':True,'signature_verification':signature}
print(json.dumps(result,ensure_ascii=False,indent=2))
