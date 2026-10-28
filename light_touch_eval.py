"""Report Light-touch bypass and PCM quantization effects for eight held-out conditions."""
import csv
from pathlib import Path
import numpy as np
import soundfile as sf
from self_eval import evaluate
ROOT=Path(__file__).resolve().parent; DATA=ROOT/'ablation_testset'; OUT=ROOT/'ablation_output'
def read(path):
 with path.open(newline='',encoding='utf-8') as f:return list(csv.DictReader(f))
def name(x):return x.replace('\\','/').split('/')[-1]
def main():
 metadata=read(DATA/'metadata.csv');modes=read(OUT/'kotlin/kotlin_modes.csv');rows=[]
 for item,mode in zip(metadata,modes):
  if mode['mode']!='Light touch':continue
  file=name(item['noisy_path']);clean,sr=sf.read(DATA/'clean'/name(item['clean_path']),dtype='float64');noisy,_=sf.read(DATA/'noisy'/file,dtype='float64')
  current,_=sf.read(OUT/'kotlin'/file);candidate,_=sf.read(OUT/'light_touch_candidate'/file)
  normalized=(noisy/np.max(np.abs(noisy))).astype(np.float32)
  pcm=(np.trunc(np.clip(normalized,-1,1)*np.float32(32767)).astype(np.int16).astype(np.float64)/32768)
  row={'file_id':item['file_id'],'noise_type':item['noise_type'],'snr_db':item['snr_db'],'input_float':evaluate(clean,normalized,sr)['stoi'],'pcm16_only':evaluate(clean,pcm,sr)['stoi'],'current':evaluate(clean,current,sr)['stoi'],'bypass_candidate':evaluate(clean,candidate,sr)['stoi']}
  row['candidate_minus_current']=row['bypass_candidate']-row['current'];row['pcm16_minus_input']=row['pcm16_only']-row['input_float'];rows.append(row)
 assert len(rows)==8
 with (OUT/'light_touch_fidelity.csv').open('w',newline='',encoding='utf-8') as f:
  w=csv.DictWriter(f,fieldnames=rows[0].keys());w.writeheader();w.writerows(rows)
 print('files',len(rows))
 for k in ('input_float','pcm16_only','current','bypass_candidate','candidate_minus_current','pcm16_minus_input'):print(k,format(np.mean([r[k] for r in rows]),'.8f'))
 for r in rows: print(r)
if __name__=='__main__':main()
