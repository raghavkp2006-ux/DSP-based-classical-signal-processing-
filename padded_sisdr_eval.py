"""Reference SI-SDR and padded noise-only diagnostics for both held-out sets."""
import csv
from pathlib import Path
from collections import Counter
import numpy as np
import soundfile as sf
from dsp_pipeline import run_pipeline
from scripts.stage_diagnostics import metrics
R=Path(__file__).resolve().parent

def readcsv(p):
 with p.open(newline='',encoding='utf8') as f:return list(csv.DictReader(f))
def writecsv(p,rows):
 with p.open('w',newline='',encoding='utf8') as f:
  w=csv.DictWriter(f,fieldnames=rows[0].keys());w.writeheader();w.writerows(rows)
def name(p):return p.replace('\\','/').split('/')[-1]
def sisdr(clean,estimate):
 clean=np.asarray(clean,dtype=np.float64); estimate=np.asarray(estimate,dtype=np.float64)
 if clean.shape!=estimate.shape:raise ValueError('SI-SDR requires equal lengths')
 s=clean-clean.mean();e=estimate-estimate.mean();den=np.dot(s,s)
 if den<=0:return float('nan')
 target=s*np.dot(e,s)/den; noise=e-target
 return float(10*np.log10(max(np.dot(target,target),1e-30)/max(np.dot(noise,noise),1e-30)))
def main():
 allresults=[]; allmetrics=[]; summaries=[]
 for dataset in ('original','padded'):
  data=R/('ablation_testset' if dataset=='original' else 'ablation_testset_padded');base=R/'ablation_output' if dataset=='original' else R/'ablation_output/padded'; py=base/'py_agent_regen';py.mkdir(parents=True,exist_ok=True)
  metadata=readcsv(data/'metadata.csv');assert len(metadata)==40
  for i,r in enumerate(metadata,1):
   filename=name(r['noisy_path']); clean,sr=sf.read(data/'clean'/name(r['clean_path'])); noisy,rate=sf.read(data/'noisy'/filename); assert sr==rate
   path=py/filename
   if dataset=='padded':
    print(f'{dataset} Python {i}/40',flush=True);result=run_pipeline(str(data/'noisy'/filename),str(path),use_agent=True,clean_reference=clean,feedback_enabled=False)
    if result['params']['use_ml_postfilter'] and not result['metrics']['ml_postfilter_used']:raise RuntimeError(f'Python ML fallback: {filename}')
   arms={'noisy':noisy,'python':sf.read(path)[0],'kotlin_legacy':sf.read(base/'kotlin_legacy'/filename)[0],'kotlin_aligned':sf.read(base/'kotlin'/filename)[0]}
   baseline=sisdr(clean,noisy); row={'dataset':dataset,'file_id':r['file_id'],'noise_type':r['noise_type'],'snr_db_condition':r['snr_db']}
   base_m=metrics(clean,noisy,sr)
   for arm,a in arms.items():
    score=sisdr(clean,a); row[arm+'_sisdr_db']=score;row[arm+'_improvement_db']=score-baseline
    if dataset=='padded':
     m=metrics(clean,a,sr)
     for key,src in [('modulation_ratio','modulation_2_10_hz'),('musical_kurtosis_ratio','musical_power_kurtosis')]:
      den=base_m[src];m[key]=m[src]/den if den>0 else float('nan')
     allmetrics.append({'dataset':dataset,'file_id':r['file_id'],'noise_type':r['noise_type'],'snr_db_condition':r['snr_db'],'version':arm,**m})
   allresults.append(row)
  for snr in ('-5','0','5','10','15','all'):
   group=[x for x in allresults if x['dataset']==dataset and (snr=='all' or x['snr_db_condition']==snr)]
   summaries.append({'dataset':dataset,'snr_db_condition':snr,'runs':len(group),**{a+'_improvement_db':float(np.mean([x[a+'_improvement_db'] for x in group])) for a in ('noisy','python','kotlin_legacy','kotlin_aligned')}})
 writecsv(R/'ablation_output/sisdr_conditions.csv',allresults);writecsv(R/'ablation_output/sisdr_summary.csv',summaries);writecsv(R/'ablation_output/padded_diagnostics.csv',allmetrics)
 lines=['# SI-SDR and padded-set artifact metrics','','Clean-referenced SI-SDR after mean removal and least-squares scaling; improvement is relative to the noisy baseline. Padding is three seconds per side, with noise scaled to the original unpadded clip power and the generator seed unchanged; mixing still clips to [-1,1].','', '| Dataset | SNR | n | Python dB | Kotlin legacy dB | Kotlin aligned dB |','|---|---|---:|---:|---:|---:|']
 for x in summaries:lines.append(f"| {x['dataset']} | {x['snr_db_condition']} | {x['runs']} | {x['python_improvement_db']:+.3f} | {x['kotlin_legacy_improvement_db']:+.3f} | {x['kotlin_aligned_improvement_db']:+.3f} |")
 lines+=['','Padded noise-only metrics (arithmetic mean over finite values; n explicitly shown):','','| Arm | Modulation ratio (n) | Musical kurtosis ratio (n) | Echo ACF (n) |','|---|---:|---:|---:|']
 for arm in ('noisy','python','kotlin_legacy','kotlin_aligned'):
  data=[x for x in allmetrics if x['version']==arm]; cells=[]
  for key in ('modulation_ratio','musical_kurtosis_ratio','echo_acf'):
   vals=[float(x[key]) for x in data if np.isfinite(float(x[key]))];cells.append(f'{np.mean(vals):.4f} ({len(vals)})' if vals else 'unavailable (0)')
  lines.append(f'| {arm} | '+ ' | '.join(cells)+' |')
 lines+=['','The padded set is distinct from the original; values across the two are not paired measurements of the same mixture. Proxies are descriptive, not perceptual thresholds.']
 (R/'ablation_output/PADDED_EVAL.md').write_text('\n'.join(lines)+'\n',encoding='utf8');print('\n'.join(lines))
if __name__=='__main__':main()
