"""Train a small spectral-mask denoiser from LibriSpeech clean audio.

Example:
python train_denoiser.py --data "C:\\...\\LibriSpeech\\dev-clean" --epochs 8 --steps-per-epoch 500
"""
from __future__ import annotations

import argparse
import random
from pathlib import Path
from math import gcd
import numpy as np
import soundfile as sf
from scipy.signal import resample_poly
import torch
from torch import nn
from torch.utils.data import Dataset, DataLoader
from trained_ml_denoiser import SpectralMaskDenoiser, DEFAULT_CHECKPOINT


class LibriSyntheticNoise(Dataset):
    def __init__(self, files, items=1000, sr=16000, seconds=1.6):
        self.files, self.items, self.sr, self.n = files, items, sr, int(sr * seconds)

    def __len__(self): return self.items

    def __getitem__(self, index):
        path = self.files[index % len(self.files)]
        clean, original_sr = sf.read(path, dtype="float32", always_2d=True)
        clean = clean.mean(axis=1)
        if original_sr != self.sr:
            factor = gcd(original_sr, self.sr)
            clean = resample_poly(clean, self.sr // factor, original_sr // factor).astype(np.float32)
        if len(clean) < self.n:
            clean = np.pad(clean, (0, self.n - len(clean)))
        start = random.randint(0, max(0, len(clean) - self.n))
        clean = clean[start:start + self.n]
        clean = clean / (np.max(np.abs(clean)) + 1e-7) * random.uniform(.25, .85)
        noise = self._noise(self.n)
        snr = random.uniform(-5, 15)
        noise *= np.sqrt(np.mean(clean**2) / (np.mean(noise**2) + 1e-9)) / (10 ** (snr / 20))
        return (torch.from_numpy((clean + noise).astype(np.float32)),
                torch.from_numpy(clean.astype(np.float32)))

    @staticmethod
    def _noise(n):
        mode = random.randrange(4)
        white = np.random.randn(n).astype(np.float32)
        if mode == 0: return white
        if mode == 1: return (np.cumsum(white) / np.sqrt(n) * 15).astype(np.float32)  # low-frequency hum / brown noise
        if mode == 2:
            freq = random.uniform(80, 2200); t = np.arange(n) / 16000
            return white * .45 + np.sin(2 * np.pi * freq * t).astype(np.float32) * .55
        envelope = (np.random.rand(n) > .90).astype(np.float32)
        envelope = np.convolve(envelope, np.ones(300), mode="same") / 300
        return (white * (.15 + 3 * envelope)).astype(np.float32)  # non-stationary bursts


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--data", required=True, help="Folder containing LibriSpeech .flac files")
    parser.add_argument("--epochs", type=int, default=8)
    parser.add_argument("--steps-per-epoch", type=int, default=500)
    parser.add_argument("--batch-size", type=int, default=12)
    parser.add_argument("--output", default=str(DEFAULT_CHECKPOINT))
    parser.add_argument("--holdout-fraction", type=float, default=0.10,
                        help="Reserve the final lexical fraction for ablation_testset evaluation")
    args = parser.parse_args()
    files = sorted(Path(args.data).rglob("*.flac"))
    if not files: raise SystemExit("No FLAC files found under --data")
    split = max(1, int(len(files) * (1 - args.holdout_fraction)))
    files = files[:split]
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    print(f"Training on {device}; {len(files)} training clips (final {args.holdout_fraction:.0%} held out)")
    loader = DataLoader(LibriSyntheticNoise(files, args.steps_per_epoch * args.batch_size),
                        batch_size=args.batch_size, shuffle=True, num_workers=0, pin_memory=device.type == "cuda")
    model = SpectralMaskDenoiser().to(device)
    opt = torch.optim.AdamW(model.parameters(), lr=2e-3, weight_decay=1e-5)
    loss_fn = nn.L1Loss(); window = torch.hann_window(512, device=device)
    for epoch in range(args.epochs):
        model.train(); running = 0.0
        for step, (noisy, clean) in enumerate(loader, 1):
            noisy, clean = noisy.to(device), clean.to(device)
            ns = torch.stft(noisy, 512, 128, window=window, return_complex=True)
            cs = torch.stft(clean, 512, 128, window=window, return_complex=True)
            target = (cs.abs() / (ns.abs() + 1e-5)).clamp(0, 1)
            pred = model(torch.log1p(ns.abs()).unsqueeze(1)).squeeze(1)
            loss = loss_fn(pred, target)
            opt.zero_grad(); loss.backward(); torch.nn.utils.clip_grad_norm_(model.parameters(), 1.0); opt.step()
            running += loss.item()
            if step % 50 == 0: print(f"epoch {epoch+1}/{args.epochs} step {step}: mask L1 {running/step:.4f}", flush=True)
        print(f"epoch {epoch+1} complete: {running/len(loader):.4f}")
    output = Path(args.output); output.parent.mkdir(parents=True, exist_ok=True)
    torch.save({"model_state": model.state_dict(), "channels": 24, "sample_rate": 16000,
                "n_fft": 512, "hop": 128, "epochs": args.epochs,
                "training_files": len(files)}, output)
    print(f"Saved trained checkpoint: {output}")

if __name__ == "__main__": main()
