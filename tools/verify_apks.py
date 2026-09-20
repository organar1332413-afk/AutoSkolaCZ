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
        markers={name:name.encode() in dex for name in ['DeveloperController','resetOnboardingForDevelopment','SeedWord']}
        samples=[n for n in z.namelist() if n.startswith('assets/content/')]
    resources=subprocess.check_output([str(build_tools/'aapt2'),'dump','resources',str(apk)],text=True)
    badging=subprocess.check_output([str(build_tools/'aapt'),'dump','badging',str(apk)],text=True)
    permissions=subprocess.check_output([str(build_tools/'aapt'),'dump','permissions',str(apk)],text=True)
    assert 'android.permission.INTERNET' not in permissions
    debuggable='application-debuggable' in badging
    developer_resources='dev_title' in resources
    assert debuggable==(variant=='debug')
    assert developer_resources==(variant=='debug')
    assert all(markers.values()) if variant=='debug' else not any(markers.values())
    assert len(samples)==3 if variant=='debug' else not samples
    subprocess.run([str(build_tools/'zipalign'),'-c','-P','16','4',str(apk)],check=True)
    signature=None
    if variant=='debug':
        signature=subprocess.check_output([str(build_tools/'apksigner'),'verify','--verbose','--print-certs',str(apk)],text=True)
    result[variant]={'apk':filename,'size_bytes':apk.stat().st_size,'sha256':hashlib.sha256(apk.read_bytes()).hexdigest(),'debuggable':debuggable,'developer_resources':developer_resources,'developer_class_markers':markers,'sample_assets':samples,'badging':badging.splitlines()[:4],'alignment_16k_verified':True,'signature_verification':signature}
print(json.dumps(result,ensure_ascii=False,indent=2))
