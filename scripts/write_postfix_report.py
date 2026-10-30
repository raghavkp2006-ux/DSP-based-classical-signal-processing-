from pathlib import Path
import csv,numpy as np
R=Path(__file__).resolve().parent.parent;O=R/'ablation_output'
def rd(p):
 with p.open(newline='',encoding='utf8') as f:return list(csv.DictReader(f))
def mean(a,k):
 vals=np.array([float(x[k]) for x in a]); vals=vals[np.isfinite(vals)];return (f'{vals.mean():.4f}' if len(vals) else '—')+f' ({len(vals)})'
def av(a,k):return f'{np.mean([float(x[k]) for x in a]):+.3f}'
r=rd(O/'postfix_evaluation.csv');h=rd(O/'hangover_sensitivity.csv');v=rd(O/'vad_fix_isolated_stoi.csv')
L=['# Post-fix evaluation and hangover sensitivity','','Original and padded sets each contain 40 conditions derived from two clean clips. Kotlin aligned EQ is the **current production configuration**; Kotlin legacy EQ matches the EQ mode shipped in v1.0.0. Python uses legacy EQ. Each pipeline uses its own routing; EQ is bypassed in Light touch. Full SI-SDR improvement uses the original unshifted clean and output over the complete file; interior uses the ±256-sample local-correlation lag and excludes the first and last 100 ms of the clean window, with the same window for the noisy baseline. See ALIGNMENT_CHECK.md for the boundary defect and lag limits. STOI is unshifted, as in the earlier report. Proxies are descriptive, not perceptual thresholds.','', '## Overall metrics','','| Set | Arm | STOI (n) | Full SI-SDR Δ dB (n) | Interior SI-SDR Δ dB (n) | Modulation ratio (n) | Musical kurtosis ratio (n) | Echo ACF (n) |','|---|---|---:|---:|---:|---:|---:|---:|']
for ds in ['original','padded']:
 for arm in ['Noisy input','Python legacy EQ','Kotlin legacy EQ','Kotlin aligned EQ (production)']:
  a=[x for x in r if x['dataset']==ds and x['arm']==arm];L.append('| '+ds+' | '+arm+' | '+' | '.join(mean(a,k) for k in ['stoi','full_sisdr_improvement','interior_improvement','modulation_ratio','musical_kurtosis_ratio','echo_acf'])+' |')
L+=['','Original noise-only segments are too short for modulation and echo metrics; these entries are unavailable, not zero. The padded set provides 40 values per proxy.','', '## SI-SDR and STOI by SNR','','| Set | Input SNR dB | n | Arm | Mean STOI | Full SI-SDR Δ dB | Interior SI-SDR Δ dB |','|---|---:|---:|---|---:|---:|---:|']
for ds in ['original','padded']:
 data=R/('ablation_testset' if ds=='original' else 'ablation_testset_padded'); m={x['noisy_path'].replace('\\','/').split('/')[-1]:x['snr_db'] for x in rd(data/'metadata.csv')}
 for snr in ['-5','0','5','10','15','all']:
  for arm in ['Python legacy EQ','Kotlin legacy EQ','Kotlin aligned EQ (production)']:
   a=[x for x in r if x['dataset']==ds and x['arm']==arm and (snr=='all' or m[x['file']]==snr)]
   L.append(f"| {ds} | {snr} | {len(a)} | {arm} | {np.mean([float(x['stoi']) for x in a]):.4f} | {av(a,'full_sisdr_improvement')} | {av(a,'interior_improvement')} |")
L+=['','## Hangover sensitivity','','Nominal is the agent choice: 0 frames for non-sparse inputs and 5 frames for sparse inputs. Multipliers 2–4 therefore test 0/10, 0/15 and 0/20 frames; each run changes only that choice. Python uses legacy EQ; Kotlin uses aligned production EQ. The Kotlin test harness was checked against production for every nominal file. VAD mismatch counts compare the two languages at the same multiplier.','', '| Set | Multiplier | Arm | STOI | Full SI-SDR Δ dB | Interior SI-SDR Δ dB | VAD mismatches / frames |','|---|---:|---|---:|---:|---:|---:|']
for ds in ['original','padded']:
 for mult in range(1,5):
  for arm in ['Python legacy EQ','Kotlin aligned EQ (production)']:
   a=[x for x in h if x['dataset']==ds and x['multiplier']==str(mult) and x['arm']==arm]
   L.append(f"| {ds} | {mult}× | {arm} | {np.mean([float(x['stoi']) for x in a]):.4f} | {av(a,'full_sisdr_improvement')} | {av(a,'interior_improvement')} | {sum(int(x['vad_mismatch']) for x in a)} / {sum(int(x['frames']) for x in a)} |")
L+=['','4× (20 frames when sparse) has the highest measured mean STOI and interior SI-SDR in both sets: versus nominal, production Kotlin STOI rises by +0.0031 original and +0.0029 padded, while interior SI-SDR rises by +0.320 and +0.195 dB. This adds up to 150 ms of hangover relative to 5 frames and changes which frames are treated as noise; effects on other speech and noise conditions are unmeasured. **Recommendation: retain nominal 5 as the default for now; propose 20 frames as a candidate for broader listening and noise-tracking tests after the boundary defect is addressed.** No setting was changed.','', '## Isolated VAD-fix STOI change','','Both before and after runs include the spectral bypass fix. The before runs reproduce the original language-specific hangover operations in evaluation-only harnesses; after uses fixed-length hangover. Routing categories are Kotlin agent routes, which agree with Python on these sets. Each value is a mean of per-file STOI rounded to four decimals.','', '| Set | Routing | n | Arm | Before | After | Change |','|---|---|---:|---|---:|---:|---:|']
for ds in ['original','padded']:
 for mode in ['Light touch','Classical DSP','Full adaptive']:
  for arm in ['python','kotlin']:
   a=[x for x in v if x['dataset']==ds and x['mode']==mode and x['arm']==arm]
   label='Python legacy EQ' if arm=='python' else 'Kotlin aligned EQ (production)'
   L.append(f"| {ds} | {mode} | {len(a)} | {label} | {np.mean([float(x['stoi_before_vad_fix']) for x in a]):.4f} | {np.mean([float(x['stoi_after_vad_fix']) for x in a]):.4f} | {np.mean([float(x['delta']) for x in a]):+.4f} |")
L+=['','Light touch is unaffected by VAD-only changes because spectral processing is bypassed. Earlier before-both-fixes comparisons also include the bypass fix; they are in stoi_before_after.csv.','', '## EQ recommendation','','Keep the current Kotlin aligned EQ as the production configuration: compared with Kotlin legacy, mean STOI rises from 0.8385 to 0.8678 on original and 0.8558 to 0.8867 on padded; mean interior SI-SDR improvement rises from +0.057 to +1.612 dB and +2.752 to +5.055 dB. Musical-kurtosis ratio drops from 4.523 to 2.390 and 24.575 to 6.808, respectively. These are limited synthetic-set measures; the tagged release used legacy, and the enabled-STFT boundary defect remains. No EQ default was changed.','', 'Per-file data: postfix_evaluation.csv, hangover_sensitivity.csv and vad_fix_isolated_stoi.csv. Export and report scripts are in scripts/; WAV files and traces remain local and ignored.']
(O/'POSTFIX_EVALUATION.md').write_text('\n'.join(L)+'\n',encoding='utf8')
