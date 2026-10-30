from pathlib import Path
import sys,csv,numpy as np,soundfile as sf
R=Path(__file__).resolve().parent.parent;sys.path.insert(0,str(R));from self_eval import evaluate
O=R/'ablation_output';D=R/'ablation_testset_padded'
with (O/'v11_downstream_stoi.csv').open(newline='',encoding='utf8') as f:rows=list(csv.DictReader(f))
with (D/'metadata.csv').open(newline='',encoding='utf8') as f:meta={r['noisy_path'].replace('\\','/').split('/')[-1]:r for r in csv.DictReader(f)}
for r in rows:
 clean,sr=sf.read(D/'clean'/meta[r['file']]['clean_path'].replace('\\','/').split('/')[-1])
 for arm in ('production','only_eq','only_dynamics'):
  y,_=sf.read(O/'v11_downstream'/arm/r['file'],dtype='float32');pcm=np.trunc(np.clip(y,-1,1)*np.float32(32767)).astype(np.int16).astype(np.float64)/32768
  r[arm+'_pcm16_stoi']=evaluate(clean,pcm,sr)['stoi'];r[arm+'_pcm16_clipped_samples']=int(np.sum(np.abs(y)>1))
with (O/'v11_downstream_stoi.csv').open('w',newline='',encoding='utf8') as f:
 w=csv.DictWriter(f,fieldnames=rows[0]);w.writeheader();w.writerows(rows)
for mode in ('Classical DSP','Full adaptive','Light touch'):
 a=[x for x in rows if x['mode']==mode]
 for arm in ('production','only_eq','only_dynamics'):
  print(mode,arm,'float',np.mean([float(x[arm]) for x in a]),'pcm',np.mean([float(x[arm+'_pcm16_stoi']) for x in a]),'files_clipped',sum(int(x[arm+'_pcm16_clipped_samples'])>0 for x in a))
