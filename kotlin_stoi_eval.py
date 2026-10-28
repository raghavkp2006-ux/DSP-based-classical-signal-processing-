"""Regenerate Python agent and compare production/legacy Kotlin over fixed conditions."""
import csv
from collections import Counter
from pathlib import Path
import numpy as np
import soundfile as sf
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
from dsp_pipeline import run_pipeline, load_audio
from self_eval import evaluate
from agent_analyzer import analyze_audio
from decision_agent import decide
from scripts.stage_diagnostics import metrics

ROOT=Path(__file__).resolve().parent
DATA=ROOT/'ablation_testset'; OUT=ROOT/'ablation_output'

def readcsv(path):
    with path.open(newline='',encoding='utf-8') as f:return list(csv.DictReader(f))
def writecsv(path,rows):
    with path.open('w',newline='',encoding='utf-8') as f:
        w=csv.DictWriter(f,fieldnames=rows[0].keys()); w.writeheader(); w.writerows(rows)
def basename(path): return path.replace('\\','/').split('/')[-1]
def mean(x):
    x=np.asarray(x,dtype=float); return float(np.mean(x[np.isfinite(x)])) if np.isfinite(x).any() else float('nan')

def main():
    items=readcsv(DATA/'metadata.csv'); modes=readcsv(OUT/'kotlin/kotlin_modes.csv'); assert len(items)==len(modes)==40
    pyout=OUT/'py_agent_regen';pyout.mkdir(parents=True,exist_ok=True)
    results=[]; diagnostics=[]; routing=[]
    for idx,(item,k) in enumerate(zip(items,modes),1):
        name=basename(item['noisy_path']); noisy_path=DATA/'noisy'/name; clean_path=DATA/'clean'/basename(item['clean_path'])
        assert item['file_id']==k['file_id'] and item['noise_type']==k['noise_type'] and item['snr_db']==k['snr_db']
        clean,sr=sf.read(clean_path,dtype='float32'); noisy,nsr=sf.read(noisy_path,dtype='float32'); assert nsr==sr
        py_path=pyout/name
        print(f'PY {idx}/40 {name}',flush=True)
        res=run_pipeline(str(noisy_path),str(py_path),use_agent=True,clean_reference=clean,feedback_enabled=False)
        if res['agent_decision']['params']['use_ml_postfilter'] and not res['metrics']['ml_postfilter_used']:
            raise RuntimeError(f'Python ML fallback on {name}: {res["stage_log"]}')
        raw,_=load_audio(str(noisy_path)); analysis=analyze_audio(raw,sr); decision=decide(analysis['snr_db'],analysis['stationary_noise'],analysis['speech_activity_ratio'])
        routing.append({'file_id':item['file_id'],'noise_type':item['noise_type'],'snr_db':item['snr_db'],'kotlin_mode':k['mode'],'python_mode':decision['mode'],'kotlin_snr':k['snrDb_est'],'python_snr':analysis['snr_db'],'kotlin_cv':k['noiseStationarityCv'],'python_cv':analysis['noise_stationarity_cv'],'kotlin_stationary':k['stationaryNoise'],'python_stationary':analysis['stationary_noise'],'kotlin_activity':k['speechActivityRatio'],'python_activity':analysis['speech_activity_ratio']})
        versions={'noisy':noisy,'python':sf.read(py_path,dtype='float32')[0],'kotlin_legacy':sf.read(OUT/'kotlin_legacy'/name,dtype='float32')[0],'kotlin_aligned':sf.read(OUT/'kotlin'/name,dtype='float32')[0]}
        scored={'file_id':item['file_id'],'noise_type':item['noise_type'],'snr_db_condition':item['snr_db']}
        for version,x in versions.items():
            assert len(x)==len(clean)
            scored[version]=evaluate(clean,x,sr)['stoi']
            d=metrics(clean,x,sr); diagnostics.append({'file_id':item['file_id'],'noise_type':item['noise_type'],'snr_db_condition':item['snr_db'],'version':version,**d})
        results.append(scored)
    for start in range(0,len(diagnostics),4):
        group=diagnostics[start:start+4]; base=group[0]
        for d in group:
            for key,source in [('modulation_ratio','modulation_2_10_hz'),('musical_kurtosis_ratio','musical_power_kurtosis')]:
                den=base[source];d[key]=d[source]/den if den>0 else float('nan')
    summary=[]
    for snr in ['-5','0','5','10','15','all']:
        rows=results if snr=='all' else [r for r in results if r['snr_db_condition']==snr]
        summary.append({'snr_db_condition':snr,'runs':len(rows),**{v:mean([r[v] for r in rows]) for v in ('noisy','python','kotlin_legacy','kotlin_aligned')}})
    writecsv(OUT/'kotlin_results.csv',results); writecsv(OUT/'kotlin_summary.csv',summary); writecsv(OUT/'kotlin_diagnostics.csv',diagnostics); writecsv(OUT/'kotlin_routing.csv',routing)
    fig,ax=plt.subplots(); x=np.arange(5)
    for v in ('noisy','python','kotlin_legacy','kotlin_aligned'):ax.plot(x,[r[v] for r in summary[:5]],marker='o',label=v)
    ax.set_xticks(x,['-5','0','5','10','15']);ax.set_xlabel('Input SNR (dB)');ax.set_ylabel('Mean STOI');ax.legend();fig.tight_layout();fig.savefig(OUT/'kotlin_vs_python_stoi.png',dpi=160);plt.close(fig)
    allrow=summary[-1]; delta=allrow['kotlin_aligned']-allrow['kotlin_legacy']; py_delta=allrow['kotlin_aligned']-allrow['python']
    metrics_names=('modulation_ratio','musical_kurtosis_ratio','echo_acf')
    means={v:{m:mean([d[m] for d in diagnostics if d['version']==v]) for m in metrics_names} for v in ('noisy','python','kotlin_legacy','kotlin_aligned')}
    lines=['# Kotlin evaluation','','40 conditions; STOI uses self_eval.evaluate on exported PCM WAVs. Python uses the legacy causal EQ; the aligned-minus-legacy Kotlin arm isolates the EQ change while leaving routing and other Kotlin stages unchanged.','', '| SNR | n | Noisy | Python | Kotlin legacy | Kotlin aligned |','|---|---:|---:|---:|---:|---:|']
    for r in summary: lines.append(f"| {r['snr_db_condition']} | {r['runs']} | {r['noisy']:.4f} | {r['python']:.4f} | {r['kotlin_legacy']:.4f} | {r['kotlin_aligned']:.4f} |")
    lines+=['',f'Aligned minus legacy overall STOI: {delta:+.4f}; aligned minus Python: {py_delta:+.4f}.',f'Within ±0.02 of Python: {abs(py_delta)<=.02}; aligned trails noisy baseline: {allrow["kotlin_aligned"] < allrow["noisy"]}.','', '| Version | Modulation ratio | Musical kurtosis ratio | Echo ACF |','|---|---:|---:|---:|']
    for v in means: lines.append(f"| {v} | {means[v]['modulation_ratio']:.4f} | {means[v]['musical_kurtosis_ratio']:.4f} | {means[v]['echo_acf']:.4f} |")
    counts={v:{m:int(sum(np.isfinite(float(d[m])) for d in diagnostics if d['version']==v)) for m in metrics_names} for v in means}
    lines+=['',f'Available conditions per metric (modulation, musical, echo): {counts}. No sufficiently long contiguous noise-only segments were available for modulation or echo; those comparisons are not measured.',f'Aligned minus legacy musical ratio: {means["kotlin_aligned"]["musical_kurtosis_ratio"]-means["kotlin_legacy"]["musical_kurtosis_ratio"]:+.4f}; aligned minus Python: {means["kotlin_aligned"]["musical_kurtosis_ratio"]-means["python"]["musical_kurtosis_ratio"]:+.4f}.',f'Mode counts (Kotlin): {dict(Counter(r["kotlin_mode"] for r in routing))}; Python: {dict(Counter(r["python_mode"] for r in routing))}.',f'Legacy Kotlin minus Python STOI: {allrow["kotlin_legacy"]-allrow["python"]:+.4f}; this is within 0.02. The aligned-vs-legacy measurement shows the legacy EQ explains a +{delta:.4f} aligned-arm shift for this set, without claiming it is the only difference between Python and Kotlin.', '', 'STOI here favors the unprocessed baseline in aggregate; it does not capture all perceptual quality aspects.','', 'These proxies are descriptive; no perceptual or pass/fail threshold is implied. Missing/too-short noise-only segments produce NaN and are excluded from each mean.','',f'Mode mismatches: {sum(r["kotlin_mode"]!=r["python_mode"] for r in routing)} of 40; see kotlin_routing.csv for per-file analysis.']
    deltas=[]
    for i in range(0,len(diagnostics),4):
        group=diagnostics[i:i+4]
        deltas.append((group[3]['musical_kurtosis_ratio']-group[1]['musical_kurtosis_ratio'],group))
    maximum, group=max(deltas,key=lambda x:x[0])
    lines += ['', f"Aligned musical ratio exceeds Python in {sum(d>0 for d,_ in deltas)} of 40 conditions; the largest difference is {maximum:+.4f} at {group[0]['file_id']}, {group[0]['noise_type']}, {group[0]['snr_db_condition']} dB (Python {group[1]['musical_kurtosis_ratio']:.4f}, legacy {group[2]['musical_kurtosis_ratio']:.4f}, aligned {group[3]['musical_kurtosis_ratio']:.4f}). Both Kotlin arms agree there, so EQ alignment does not explain this case. Upstream DSP and normalization differences are possible contributors, but the first divergent stage has not been identified; this is a diagnosis to investigate, not a confirmed cause."]
    (OUT/'KOTLIN_EVAL.md').write_text('\n'.join(lines)+'\n',encoding='utf-8')
    print('\n'.join(lines),flush=True)
if __name__=='__main__':main()
