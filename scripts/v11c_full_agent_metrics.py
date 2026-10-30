from pathlib import Path
import csv
import numpy as np,soundfile as sf
from pystoi import stoi
R=Path(__file__).resolve().parents[1];O=R/'ablation_output'
per=[];summ=[]
for ds in ('original','padded'):
 data=R/('ablation_testset' if ds=='original' else 'ablation_testset_padded')
 meta=list(csv.DictReader((data/'metadata.csv').open(encoding='utf-8-sig')))
 modes=list(csv.DictReader((O/'v11c_full_agent'/ds/'modes.csv').open(encoding='utf-8-sig')))
 for m in meta:
  name=Path(m['noisy_path']).name;mode=next(x for x in modes if x['file_id']==m['file_id'] and x['noise_type']==m['noise_type'] and int(x['snr_db'])==int(m['snr_db']))
  clean,sr=sf.read(data/'clean'/(m['file_id']+'.wav'),dtype='float64');noisy,_=sf.read(data/'noisy'/name,dtype='float64');out,_=sf.read(O/'v11c_full_agent'/ds/name,dtype='float64')
  s0=stoi(clean,noisy,sr);s1=stoi(clean,out,sr);e0=stoi(clean,noisy,sr,extended=True);e1=stoi(clean,out,sr,extended=True)
  per.append({'dataset':ds,'file':name,'noise_type':m['noise_type'],'snr_db':m['snr_db'],'route':mode['mode'],'warning':mode['warning'],'noisy_stoi':s0,'output_stoi':s1,'stoi_delta':s1-s0,'noisy_estoi':e0,'output_estoi':e1,'estoi_delta':e1-e0})
 for route in ('Light touch','Classical DSP','Full adaptive'):
  q=[x for x in per if x['dataset']==ds and x['route']==route]
  if q:summ.append({'dataset':ds,'route':route,'n':len(q),'noisy_stoi':np.mean([x['noisy_stoi'] for x in q]),'output_stoi':np.mean([x['output_stoi'] for x in q]),'stoi_delta':np.mean([x['stoi_delta'] for x in q]),'noisy_estoi':np.mean([x['noisy_estoi'] for x in q]),'output_estoi':np.mean([x['output_estoi'] for x in q]),'estoi_delta':np.mean([x['estoi_delta'] for x in q]),'warnings':sum(bool(x['warning']) for x in q)})
 q=[x for x in per if x['dataset']==ds]
 summ.append({'dataset':ds,'route':'OVERALL','n':len(q),'noisy_stoi':np.mean([x['noisy_stoi'] for x in q]),'output_stoi':np.mean([x['output_stoi'] for x in q]),'stoi_delta':np.mean([x['stoi_delta'] for x in q]),'noisy_estoi':np.mean([x['noisy_estoi'] for x in q]),'output_estoi':np.mean([x['output_estoi'] for x in q]),'estoi_delta':np.mean([x['estoi_delta'] for x in q]),'warnings':sum(bool(x['warning']) for x in q)})
for fn,rs in [('v11c_full_agent_per_file.csv',per),('v11c_full_agent_summary.csv',summ)]:
 with (O/fn).open('w',newline='',encoding='utf-8') as f:w=csv.DictWriter(f,fieldnames=rs[0].keys());w.writeheader();w.writerows(rs)
for x in summ:print(x)
