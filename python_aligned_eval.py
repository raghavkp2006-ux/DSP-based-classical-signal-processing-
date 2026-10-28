"""Regenerate and score the aligned-EQ Python agent on the original 40 conditions."""
import csv
from pathlib import Path
import numpy as np
import soundfile as sf
from dsp_pipeline import run_pipeline
from self_eval import evaluate
R=Path(__file__).resolve().parent;DATA=R/'ablation_testset';OUT=R/'ablation_output';dest=OUT/'py_aligned_regen';dest.mkdir(exist_ok=True)
def read(p):
 with p.open(newline='',encoding='utf8') as f:return list(csv.DictReader(f))
def base(p):return p.replace('\\','/').split('/')[-1]
rows=[]
for i,r in enumerate(read(DATA/'metadata.csv'),1):
 n=base(r['noisy_path']);clean,sr=sf.read(DATA/'clean'/base(r['clean_path']),dtype='float32')
 print(f'PY_ALIGNED {i}/40 {n}',flush=True)
 result=run_pipeline(str(DATA/'noisy'/n),str(dest/n),use_agent=True,clean_reference=clean,feedback_enabled=False,aligned_eq=True)
 if result['params']['use_ml_postfilter'] and not result['metrics']['ml_postfilter_used']:raise RuntimeError(f'ML fallback: {n}')
 aligned,ar=sf.read(dest/n,dtype='float32');legacy,lr=sf.read(OUT/'py_agent_regen'/n,dtype='float32');assert len(clean)==len(aligned)==len(legacy) and sr==ar==lr
 rows.append({'file_id':r['file_id'],'noise_type':r['noise_type'],'snr_db_condition':r['snr_db'],'python_legacy':evaluate(clean,legacy,sr)['stoi'],'python_aligned':evaluate(clean,aligned,sr)['stoi']})
with (OUT/'python_aligned_results.csv').open('w',newline='',encoding='utf8') as f:
 w=csv.DictWriter(f,fieldnames=rows[0]);w.writeheader();w.writerows(rows)
lines=['','## Python aligned-EQ arm','','Same 40 conditions; both Python outputs are exported PCM and scored with `self_eval.evaluate`. The legacy arm remains the default for reproducibility.','', '| SNR | n | Python legacy | Python aligned | Aligned minus legacy |','|---|---:|---:|---:|---:|']
for snr in ('-5','0','5','10','15','all'):
 group=rows if snr=='all' else [r for r in rows if r['snr_db_condition']==snr]
 a=np.mean([r['python_legacy'] for r in group]);b=np.mean([r['python_aligned'] for r in group]);lines.append(f'| {snr} | {len(group)} | {a:.4f} | {b:.4f} | {b-a:+.4f} |')
path=OUT/'KOTLIN_EVAL.md'; text=path.read_text(encoding='utf8').split('\n## Python aligned-EQ arm')[0].rstrip();path.write_text(text+'\n'+'\n'.join(lines)+'\n',encoding='utf8')
print('\n'.join(lines))
