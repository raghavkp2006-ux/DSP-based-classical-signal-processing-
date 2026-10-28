"""Noise-only and speech-segment diagnostics; no quality thresholds implied."""
from __future__ import annotations
import argparse
import csv
from pathlib import Path
import numpy as np
import soundfile as sf
from scipy import signal, stats


def metrics(clean, audio, sr):
    clean=np.asarray(clean, dtype=np.float64); audio=np.asarray(audio, dtype=np.float64)
    if clean.shape != audio.shape or clean.ndim != 1: raise ValueError('Inputs must be equal-length mono signals')
    frame=max(1,round(.025*sr)); hop=max(1,round(.010*sr))
    if len(clean)<frame: raise ValueError('Audio too short for analysis')
    starts=np.arange(0,len(clean)-frame+1,hop)
    energies=np.array([np.mean(clean[i:i+frame]**2) for i in starts])
    speech=energies >= 10**(-45/10)
    out={}
    for label,mask in [('noise',~speech),('speech',speech)]:
        # Count samples covered by qualifying frames only once; preserve gaps.
        covered=np.zeros(len(audio),dtype=bool)
        for i in starts[mask]: covered[i:i+frame]=True
        a=audio[covered]
        out[label+'_frames']=int(mask.sum())
        out[label+'_rms_dbfs']=float(20*np.log10(max(np.sqrt(np.mean(a*a)),1e-15))) if a.size else float('nan')
        out[label+'_peak']=float(np.max(np.abs(a))) if a.size else float('nan')
        out[label+'_clip_pct']=float(100*np.mean(np.abs(a)>=1.0)) if a.size else float('nan')
        out[label+'_dc']=float(np.mean(a)) if a.size else float('nan')
    # Independent time-frequency frames avoid merging isolated noise windows.
    frames=np.array([audio[i:i+frame]*np.hanning(frame) for i in starts[~speech]])
    if len(frames)<2:
        out.update(modulation_2_10_hz=float('nan'), musical_power_kurtosis=float('nan'),echo_acf=float('nan')); return out
    power=np.abs(np.fft.rfft(frames,axis=1))**2
    kurt=stats.kurtosis(power,axis=0,fisher=False,nan_policy='omit')
    out['musical_power_kurtosis']=float(np.nanmean(kurt)) if np.any(np.isfinite(kurt)) else float('nan')
    # Modulation energy is averaged over contiguous runs of noise frames; no time compression across speech.
    ids=np.flatnonzero(~speech); groups=np.split(ids,np.flatnonzero(np.diff(ids)>1)+1)
    mod=[]; weights=[]; echo=[]
    for group in groups:
        if len(group)<max(8,round(sr/hop/2)): continue
        segment=audio[starts[group[0]]:min(len(audio),starts[group[-1]]+frame)]
        _,_,z=signal.stft(segment,fs=sr,nperseg=frame,noverlap=frame-hop,boundary=None,padded=False)
        env=np.abs(z); env=env-np.mean(env,axis=1,keepdims=True)
        freqs=np.fft.rfftfreq(env.shape[1],d=hop/sr)
        power=np.abs(np.fft.rfft(env,axis=1))**2/max(1,env.shape[1]**2)
        mod.append(float(np.sum(power[:,(freqs>=2)&(freqs<=10)]))); weights.append(len(segment))
        x=segment-np.mean(segment); ac=signal.fftconvolve(x,x[::-1],mode='full')[len(x)-1:]; den=np.dot(x,x)
        lo=max(1,round(.03*sr)); hi=min(len(ac)-1,round(.3*sr))
        if hi>=lo and den>0: echo.append(float(np.max(ac[lo:hi+1]/den)))
    out['modulation_2_10_hz']=float(np.average(mod,weights=weights)) if mod else float('nan')
    out['echo_acf']=max(echo) if echo else float('nan')
    return out


def selftest():
    sr=16000; rng=np.random.default_rng(410); n=sr*8; x=rng.normal(0,.03,n); clean=np.zeros(n)
    mod=x*(1+.8*np.sin(2*np.pi*4*np.arange(n)/sr)); delayed=x.copy(); delayed[1600:]+=.65*x[:-1600]
    frame=round(.025*sr); hop=round(.010*sr)
    _,_,stft=signal.stft(x,fs=sr,nperseg=frame,noverlap=frame-hop,boundary='zeros',padded=True)
    masked=stft*(rng.random(stft.shape)<.05)
    _,sparse=signal.istft(masked,fs=sr,nperseg=frame,noverlap=frame-hop,boundary=True)
    sparse=sparse[:len(x)]
    a,b,c,d=(metrics(clean,z,sr) for z in (x,mod,delayed,sparse))
    same=metrics(clean,x.copy(),sr)['musical_power_kurtosis']/a['musical_power_kurtosis']
    ratio=d['musical_power_kurtosis']/a['musical_power_kurtosis']
    print('SELFTEST same_ratio=',same,'masked_ratio=',ratio,'4Hz_noise=',a['modulation_2_10_hz'],'4Hz_modulated=',b['modulation_2_10_hz'],'echo_noise=',a['echo_acf'],'echo_delayed=',c['echo_acf'])
    assert abs(same-1.0)<=0.15
    assert ratio>1.5
    assert b['modulation_2_10_hz']>a['modulation_2_10_hz']
    assert c['echo_acf']>a['echo_acf']


def main():
    p=argparse.ArgumentParser(); p.add_argument('--self-test',action='store_true'); p.add_argument('--clean'); p.add_argument('--noisy'); p.add_argument('--python'); p.add_argument('--kotlin'); p.add_argument('--output'); args=p.parse_args()
    if args.self_test: selftest(); return
    if not all((args.clean,args.noisy,args.python,args.kotlin,args.output)): p.error('provide --clean --noisy --python --kotlin --output')
    clean,sr=sf.read(args.clean,dtype='float64'); rows=[]
    for name,path in [('noisy',args.noisy),('python',args.python),('kotlin',args.kotlin)]:
        x,rate=sf.read(path,dtype='float64')
        if rate!=sr: raise ValueError('Sample rates differ')
        rows.append({'version':name,**metrics(clean,x,sr)})
    base=rows[0]['modulation_2_10_hz']
    base_kurtosis=rows[0]['musical_power_kurtosis']
    for row in rows:
        row['modulation_relative_to_noisy']=row['modulation_2_10_hz']/base if base>0 else float('nan')
        row['musical_kurtosis_ratio']=row['musical_power_kurtosis']/base_kurtosis if base_kurtosis>0 else float('nan')
    with Path(args.output).open('w',newline='',encoding='utf-8') as f:
        w=csv.DictWriter(f,fieldnames=rows[0].keys()); w.writeheader(); w.writerows(rows)
if __name__=='__main__': main()
