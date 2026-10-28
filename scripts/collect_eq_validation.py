"""Collect reproducible EQ validation evidence after Gradle tests (no private audio)."""
from pathlib import Path
import csv
import shutil
import xml.etree.ElementTree as ET
import hashlib
import json
import numpy as np

root = Path(__file__).resolve().parents[1]
app = root/'signalchain-android/app'
out = root/'signalchain-android/verification/eq-alignment'
out.mkdir(parents=True, exist_ok=True)
summary=[]
for p in sorted((app/'build/eq_validation').glob('response_*.csv')):
    shutil.copyfile(p,out/p.name)
    d=np.genfromtxt(p,delimiter=',',names=True)
    band=d[(d['hz']>=300)&(d['hz']<=7000)]
    summary.append({'file':p.name,'legacy_min_db':float(min(band['legacy_db'])),'aligned_min_db':float(min(band['aligned_db'])),'aligned_max_db':float(max(band['aligned_db']))})
counts={}
for variant in ('testDebugUnitTest','testReleaseUnitTest'):
    suites=[ET.parse(p).getroot() for p in (app/'build/test-results'/variant).glob('TEST-*.xml')]
    counts[variant]={k:sum(int(s.get(k,0)) for s in suites) for k in ('tests','failures','errors','skipped')}
    for s in suites:
        if s.get('name','').endswith(('EqAlignmentTest','ClarityComparisonTest')):
            (out/f"{variant}-{s.get('name').split('.')[-1]}.txt").write_text(s.findtext('system-out',''),encoding='utf-8')
apk=app/'build/outputs/apk/release/app-release.apk'
evidence={'test_counts':counts,'response_summary':summary,'apk_bytes':apk.stat().st_size,'apk_sha256':hashlib.sha256(apk.read_bytes()).hexdigest()}
(out/'summary.json').write_text(json.dumps(evidence,indent=2)+'\n')
print(json.dumps(evidence,indent=2))
try:
    import matplotlib
    matplotlib.use('Agg')
    import matplotlib.pyplot as plt
    fig, axes=plt.subplots(3,1,figsize=(10,10))
    for ax,sr in zip(axes,(16000,44100,48000)):
        d=np.genfromtxt(out/f'response_{sr}_1.0.csv',delimiter=',',names=True)
        ax.plot(d['hz'],d['legacy_db'],label='Legacy causal EQ',linewidth=.8)
        ax.plot(d['hz'],d['aligned_db'],label='Aligned EQ',linewidth=1)
        ax.set(xlim=(0,sr/2),ylim=(-45,10),title=f'{sr} Hz / Full adaptive EQ',xlabel='Frequency (Hz)',ylabel='Gain (dB)')
        ax.grid(alpha=.25); ax.legend()
    fig.tight_layout(); fig.savefig(out/'response.png',dpi=150); plt.close(fig)
except ImportError:
    print('matplotlib unavailable; complete response CSVs still produced')
