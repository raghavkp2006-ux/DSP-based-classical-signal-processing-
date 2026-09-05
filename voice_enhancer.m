% VOICE_ENHANCER  Classical 10-stage speech enhancement reference script.
% Mirrors the Python pipeline: pre-emphasis, framing/VAD, noise PSD,
% spectral subtraction + Wiener, de-emphasis, EQ, compression, limiting.
clear; close all; clc;
[x, Fs] = audioread('input.wav');
if size(x,2) > 1, x = mean(x,2); end
x = x ./ (max(abs(x)) + eps); N = length(x);

% 1-2. Pre-emphasis
alpha = 0.97; xp = filter([1 -alpha], 1, x);
% 3. 25 ms frames / 10 ms hop / Hann window
frameLen = round(0.025*Fs); hop = round(0.010*Fs); nfft = 2^nextpow2(2*frameLen);
numFrames = max(1, floor((N-frameLen)/hop)+1); win = hann(frameLen,'periodic');
E = zeros(numFrames,1); Z = zeros(numFrames,1);
for k=1:numFrames
    ix=(k-1)*hop+1; fr=xp(ix:min(ix+frameLen-1,N)); fr(end+1:ix+frameLen-1)=0;
    E(k)=mean(fr.^2); Z(k)=sum(abs(diff(sign(fr))))/(2*frameLen);
end
% 4. Energy + ZCR VAD
sortedE=sort(E); floorE=mean(sortedE(1:max(1,round(.2*numFrames)))); speech=(E>3*floorE)&(Z<2*mean(Z));
if sum(~speech)<round(.2*numFrames), [~,ord]=sort(E); speech(:)=true; speech(ord(1:round(.2*numFrames)))=false; end
% 5. Noise PSD
noisePSD=zeros(nfft/2+1,1); count=0;
for k=1:numFrames
    if ~speech(k), ix=(k-1)*hop+1; fr=xp(ix:min(ix+frameLen-1,N)); fr(end+1:ix+frameLen-1)=0; S=fft(fr.*win,nfft); noisePSD=noisePSD+abs(S(1:nfft/2+1)).^2; count=count+1; end
end
if count==0, noisePSD=ones(nfft/2+1,1)*mean(abs(fft(xp(1:min(N,frameLen)),nfft)).^2); else, noisePSD=noisePSD/count; end
noisePSD=movmean(noisePSD,5);
% 6. Spectral subtraction + adaptive Wiener reconstruction
y=zeros(N+nfft,1); wsum=zeros(N+nfft,1); prev=ones(nfft/2+1,1); wp=[win; zeros(nfft-frameLen,1)];
for k=1:numFrames
    ix=(k-1)*hop+1; fr=xp(ix:min(ix+frameLen-1,N)); fr(end+1:ix+frameLen-1)=0; S=fft(fr.*win,nfft); H=S(1:nfft/2+1); mag=abs(H); ph=angle(H); P=mag.^2;
    cleanP=max(P-1.2*noisePSD,.1*P); post=max(P./(noisePSD+eps)-1,0); g=.7*prev+.3*(post./(post+1)); g=max(g,.25); prev=g;
    outMag= sqrt(cleanP).*g; full=[outMag.*exp(1i*ph); conj(outMag(end-1:-1:2).*exp(1i*ph(end-1:-1:2)))]; frOut=real(ifft(full,nfft)); y(ix:ix+nfft-1)=y(ix:ix+nfft-1)+frOut.*wp; wsum(ix:ix+nfft-1)=wsum(ix:ix+nfft-1)+wp.^2;
end
y=y(1:N)./(wsum(1:N)+eps);
% 7. De-emphasis
y=filter(1,[1 -alpha],y);
% 8. Voice-band EQ and rumble/hiss filtering
y=filter(fir1(64,[300 2500]/(Fs/2),'bandpass'),1,y);
% 9. Gentle compression
thr=10^(-24/20); env=movmean(abs(y),max(1,round(.01*Fs))); gain=min(1,(thr./(env+eps)).^(1-1/2.5)); y=y.*gain*10^(10/20);
% 10. Normalize and soft-limit
y=.99*y/(max(abs(y))+eps); y=y./max(1,abs(y)/.98); audiowrite('enhanced_output.wav',y,Fs);
fprintf('Wrote enhanced_output.wav at %d Hz\n',Fs);
