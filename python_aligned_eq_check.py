"""Verify Python aligned EQ against independent stage fixture, with legacy default preserved."""
import csv
from pathlib import Path
import numpy as np
from dsp_pipeline import apply_eq, apply_eq_aligned
p=Path('signalchain-android/app/src/test/resources/eq_aligned_reference.csv')
with p.open(newline='') as f:rows=list(csv.DictReader(f))
worst=0.0
for r in rows:
 x=np.array([float(v) for v in r['input'].split(';')]); y=np.array([float(v) for v in r['expected'].split(';')])
 actual=apply_eq_aligned(x,int(r['rate']),float(r['gain']))
 worst=max(worst,float(np.max(np.abs(actual-y))))
 np.testing.assert_allclose(actual,y,rtol=0,atol=1e-5)
assert len(rows)==18
assert np.array_equal(apply_eq(np.arange(20.)/20,16000,0),apply_eq_aligned(np.arange(20.)/20,16000,0))
print(f'Python aligned EQ: {len(rows)} fixture cases; max absolute error {worst:.12g}; tolerance 1e-5')
