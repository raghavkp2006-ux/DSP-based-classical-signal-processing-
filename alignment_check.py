from pathlib import Path
import csv
import numpy as np
import soundfile as sf
from scipy.signal import correlate, correlation_lags
from padded_sisdr_eval import sisdr, readcsv, name
R=Path(__file__).resolve().parent
rows=[]
for dataset in ('original','padded'):
 data=R/('ablation_testset' if dataset=='original' else 'ablation_testset_padded')
 base=R/'ablation_output' if dataset=='original' else R/'ablation_output/padded'
 for item in readcsv(data/'metadata.csv'):
  filename=name(item['noisy_path']); clean,sr=sf.read(data/'clean'/name(item['clean_path'])); noisy,_=sf.read(data/'noisy'/filename)
  for arm,folder in [('python_legacy','py_agent_regen'),('kotlin_legacy','kotlin_legacy'),('kotlin_aligned','kotlin')]:
   x,rate=sf.read(base/folder/filename); assert sr==rate and len(x)==len(clean)==len(noisy)
   corr=correlate(x-x.mean(),clean-clean.mean(),mode='full',method='fft'); lags=correlation_lags(len(x),len(clean)); lag=int(lags[np.argmax(np.abs(corr))]); window=np.abs(lags)<=256; local_lag=int(lags[window][np.argmax(np.abs(corr[window]))])
   if lag>=0: c=clean[:len(clean)-lag or None]; y=x[lag:]; n=noisy[:len(clean)-lag or None]
   else: c=clean[-lag:]; y=x[:len(x)+lag]; n=noisy[-lag:]
   if local_lag>=0: lc=clean[:len(clean)-local_lag or None]; ly=x[local_lag:]; ln=noisy[:len(clean)-local_lag or None]
   else: lc=clean[-local_lag:]; ly=x[:len(x)+local_lag]; ln=noisy[-local_lag:]
   rows.append(dict(local_lag_samples=local_lag,local_aligned_sisdr=sisdr(lc,ly),local_aligned_improvement=sisdr(lc,ly)-sisdr(lc,ln),dataset=dataset,file_id=item['file_id'],noise_type=item['noise_type'],snr_db=item['snr_db'],arm=arm,lag_samples=lag,raw_sisdr=sisdr(clean,x),aligned_sisdr=sisdr(c,y),aligned_noisy_sisdr=sisdr(c,n),aligned_improvement=sisdr(c,y)-sisdr(c,n),overlap_samples=len(c)))
  print(dataset,filename,flush=True)
out=R/'ablation_output/alignment_check.csv'
with out.open('w',newline='',encoding='utf8') as f:
 w=csv.DictWriter(f,fieldnames=rows[0]); w.writeheader(); w.writerows(rows)
for dataset in ('original','padded'):
 for arm in ('python_legacy','kotlin_legacy','kotlin_aligned'):
  a=[r for r in rows if r['dataset']==dataset and r['arm']==arm]
  print(dataset,arm,'lag range',min(r['lag_samples'] for r in a),max(r['lag_samples'] for r in a),'nonzero',sum(r['lag_samples']!=0 for r in a),'raw',np.mean([r['raw_sisdr'] for r in a]),'aligned',np.mean([r['aligned_sisdr'] for r in a]),'improvement',np.mean([r['aligned_improvement'] for r in a]))
