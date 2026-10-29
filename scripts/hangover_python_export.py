from pathlib import Path
import sys,csv,shutil
R=Path(__file__).resolve().parent.parent;sys.path.insert(0,str(R))
import dsp_pipeline as d
from agent_analyzer import analyze_audio
from decision_agent import decide
for ds in ('original','padded'):
 data=R/('ablation_testset' if ds=='original' else 'ablation_testset_padded');base=R/'ablation_output'/('sensitivity_'+ds)
 for i,item in enumerate(csv.DictReader((data/'metadata.csv').open(encoding='utf8'))):
  name=item['noisy_path'].replace('\\','/').split('/')[-1]; inp=data/'noisy'/name;x,sr=d.load_audio(str(inp));a=analyze_audio(x,sr);p=decide(a['snr_db'],a['stationary_noise'],a['speech_activity_ratio'])['params'];nom=p['vad_hangover_frames'];fp=d.frame_params(len(x),sr);pre=d.pre_emphasis(x,p['pre_emph_coeff'])
  for m in range(1,5):
   out=base/('python_'+str(m));out.mkdir(parents=True,exist_ok=True);speech,_,_=d.voice_activity_detection(pre,fp,nom*m);(out/(name+'.vad')).write_text(''.join('1' if v else '0' for v in speech))
   if m==1 or nom==0:shutil.copyfile(R/'ablation_output'/('postfix_'+ds)/'python_legacy'/name,out/name)
   else:
    res=d.run_pipeline(str(inp),str(out/name),params={'vad_hangover_frames':nom*m},feedback_enabled=False)
    if res['params']['use_ml_postfilter'] and not res['metrics']['ml_postfilter_used']:raise RuntimeError('ML fallback '+name)
  print(ds,i+1,'nominal',nom,flush=True)
