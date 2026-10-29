from pathlib import Path
import sys,csv
import numpy as np,soundfile as sf
from scipy import signal
R=Path(__file__).resolve().parent.parent;sys.path.insert(0,str(R))
import dsp_pipeline as d
from padded_sisdr_eval import sisdr

def nm(x):return x.replace('\\','/').split('/')[-1]
def lagfun(clean,x):
 c=signal.correlate(x-x.mean(),clean-clean.mean(),method='fft'); l=signal.correlation_lags(len(x),len(clean)); mask=np.abs(l)<=256;return int(l[mask][np.argmax(np.abs(c[mask]))])
def scores(c,n,y,sr):
 lag=lagfun(c,y);a=max(0,-lag);b=min(len(c),len(y)-lag); t=int(round(.1*sr)); ia=max(a,t);ib=min(b,len(c)-t)
 return lag,sisdr(c[a:b],y[a+lag:b+lag])-sisdr(c[a:b],n[a:b]),sisdr(c[ia:ib],y[ia+lag:ib+lag])-sisdr(c[ia:ib],n[ia:ib])
def oracle(c,n,sr):
 _,_,C=signal.stft(c,fs=sr,nperseg=512,noverlap=384,boundary='zeros',padded=True)
 _,_,N=signal.stft(n-c,fs=sr,nperseg=512,noverlap=384,boundary='zeros',padded=True)
 _,_,M=signal.stft(n,fs=sr,nperseg=512,noverlap=384,boundary='zeros',padded=True)
 gain=np.abs(C)**2/(np.abs(C)**2+np.abs(N)**2+1e-20)
 _,y=signal.istft(M*gain,fs=sr,nperseg=512,noverlap=384,boundary=True)
 return y[:len(c)]
rows=[];cleans=[];identity=[]
for ds in ('original','padded'):
 data=R/('ablation_testset' if ds=='original' else 'ablation_testset_padded');base=R/'ablation_output'/('postfix_'+ds); pyclean=base/'clean_python_legacy';pyclean.mkdir(exist_ok=True,parents=True)
 meta=list(csv.DictReader((data/'metadata.csv').open(encoding='utf8')))
 for cf in sorted((data/'clean').glob('*.wav')):
  c,sr=sf.read(cf); res=d.run_pipeline(str(cf),str(pyclean/cf.name),feedback_enabled=False);y,_=sf.read(pyclean/cf.name); l=lagfun(c,y);a=max(0,-l);b=min(len(c),len(y)-l)
  cleans.append({'dataset':ds,'file':cf.name,'arm':'python_legacy','mode':res['agent_decision']['mode'],'lag':l,'sisdr':sisdr(c[a:b],y[a+l:b+l])})
  ky=base/'clean_kotlin_aligned'/cf.name
  if ky.exists():
   y,_=sf.read(ky);l=lagfun(c,y);a=max(0,-l);b=min(len(c),len(y)-l);cleans.append({'dataset':ds,'file':cf.name,'arm':'kotlin_aligned_production','mode':'see Kotlin test log','lag':l,'sisdr':sisdr(c[a:b],y[a+l:b+l])})
 for i,item in enumerate(meta):
  name=nm(item['noisy_path']);c,sr=sf.read(data/'clean'/nm(item['clean_path']));n,_=sf.read(data/'noisy'/name)
  for arm,folder in [('python_legacy','python_legacy'),('kotlin_legacy','kotlin_legacy'),('kotlin_aligned_production','kotlin')]:
   y,_=sf.read(base/folder/name);lag,full,interior=scores(c,n,y,sr);rows.append(dict(dataset=ds,file=name,arm=arm,lag=lag,full_improvement=full,interior_improvement=interior))
  y=oracle(c,n,sr);lag,full,interior=scores(c,n,y,sr);rows.append(dict(dataset=ds,file=name,arm='oracle_wiener',lag=lag,full_improvement=full,interior_improvement=interior))
  if i==0:
   out=base/'identity.wav';res=d.run_pipeline(str(data/'noisy'/name),str(out),use_agent=False,params={'pre_emph_coeff':0.0,'use_spectral_subtraction':False,'use_eq':False,'compression_ratio':1.0,'compression_makeup_db':0.0,'use_ml_postfilter':False},feedback_enabled=False); inp=res['audio_raw']; y,_=sf.read(out);lag=lagfun(inp,y);a=max(0,-lag);b=min(len(inp),len(y)-lag);identity.append({'dataset':ds,'file':name,'arm':'python','lag':lag,'sisdr':sisdr(inp[a:b],y[a+lag:b+lag])})
  print(ds,i+1,flush=True)
for name,rr in [('alignment_postfix.csv',rows),('clean_input_postfix.csv',cleans),('identity_postfix.csv',identity)]:
 with (R/'ablation_output'/name).open('w',newline='',encoding='utf8') as f:w=csv.DictWriter(f,fieldnames=rr[0]);w.writeheader();w.writerows(rr)
for ds in ('original','padded'):
 for arm in ('python_legacy','kotlin_legacy','kotlin_aligned_production','oracle_wiener'):
  z=[r for r in rows if r['dataset']==ds and r['arm']==arm]; print(ds,arm,'full',np.mean([r['full_improvement'] for r in z]),'interior',np.mean([r['interior_improvement'] for r in z]),flush=True)
print('CLEAN',cleans,'IDENTITY',identity)
