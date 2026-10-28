"""Compare float pipeline stages on three Classical cases and one Light case."""
import csv
from pathlib import Path
import numpy as np
import soundfile as sf
import dsp_pipeline as d
from agent_analyzer import analyze_audio
from decision_agent import decide
R=Path(__file__).resolve().parent; O=R/'ablation_output'; D=R/'ablation_testset'
def base(p):return p.replace('\\','/').split('/')[-1]
def read(p):
 with p.open(newline='',encoding='utf8') as f:return list(csv.DictReader(f))
def main():
 meta=read(D/'metadata.csv'); routes=read(O/'kotlin_routing.csv');chosen=[i for i,r in enumerate(routes) if r['kotlin_mode']=='Classical DSP'][:3]+[i for i,r in enumerate(routes) if r['file_id']=='holdout_00_7976-110124-0013' and r['noise_type']=='bursty' and r['snr_db']=='15'];rows=[]
 for i in chosen:
  item=meta[i];name=base(item['noisy_path']);inp=D/'noisy'/name;folder=O/'stage_compare'/name.removesuffix('.wav'); x,sr=d.load_audio(str(inp));a=analyze_audio(x,sr);decision=decide(a['snr_db'],a['stationary_noise'],a['speech_activity_ratio']);p=decision['params'];fp=d.frame_params(len(x),sr)
  pre=d.pre_emphasis(x,p['pre_emph_coeff']);vad,_,_=d.voice_activity_detection(pre,fp,p['vad_hangover_frames']);noise,_=d.estimate_noise_psd(pre,fp,vad);spec=d.spectral_subtract_wiener(pre,fp,noise,len(x),alpha=p['alpha'],beta=p['beta'],gain_smooth=p['gain_smooth'],use_spectral_subtraction=p['use_spectral_subtraction']);de=d.de_emphasis(spec,p['pre_emph_coeff']);eq=d.apply_eq(de,sr,p['eq_gain'] if p['use_eq'] else 0);comp=d.dynamic_range_compress(eq,sr,ratio=p['compression_ratio'],makeup_gain_db=p['compression_makeup_db']);norm,_=d.normalize_and_limit(comp,x)
  pipeline=d.run_pipeline(str(inp),str(folder/'python_final.wav'),use_agent=True,feedback_enabled=False)
  (folder/'python_stage_log.txt').write_text('\n'.join(str(s) for s in pipeline['stage_log']),encoding='utf8')
  stages=[('00_input',x),('01_preemph',pre),('02_vad',vad.astype(float)),('02_noise_psd',noise),('03_spectral',spec),('03_specsub_deemph',de),('04_eq',eq),('05_compressor',comp),('06_normalized',norm)]
  for stage,python in stages:
   sf.write(folder/f'python_{stage}.wav',python,sr,subtype='FLOAT');k,ksr=sf.read(folder/f'kotlin_{stage}.wav',dtype='float64');assert len(k)==len(python) and ksr==sr
   diff=k-python; rms=float(np.sqrt(np.mean(diff**2))); peak=float(np.max(np.abs(diff))); scale=float(np.sqrt(np.mean(python**2)))
   rows.append(dict(file=name,mode=decision['mode'],stage=stage,n=len(k),max_abs=peak,rms_error=rms,reference_rms=scale,error_relative_db=float(20*np.log10(max(rms,1e-300)/max(scale,1e-300)))))
   print(name,stage,'max',peak,'rms',rms,'relative_db',rows[-1]['error_relative_db'])
 with (O/'stage_comparison.csv').open('w',newline='',encoding='utf8') as f:
  w=csv.DictWriter(f,fieldnames=rows[0].keys());w.writeheader();w.writerows(rows)
if __name__=='__main__':main()
