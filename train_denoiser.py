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


def classical_frontend(audio: np.ndarray, sr: int) -> np.ndarray:
    """Run the same classical stages used before the ML post-filter."""
    from dsp_pipeline import (apply_eq, de_emphasis, dynamic_range_compress,
                              estimate_noise_psd, frame_params, normalize_and_limit,
                              pre_emphasis, spectral_subtract_wiener,
                              voice_activity_detection)
    audio = np.asarray(audio, dtype=float)
    pre = pre_emphasis(audio)
    fp = frame_params(len(audio), sr)
    speech, _, _ = voice_activity_detection(pre, fp)
    noise_psd, _ = estimate_noise_psd(pre, fp, speech)
    enhanced = spectral_subtract_wiener(pre, fp, noise_psd, len(audio),
                                        alpha=1.2, beta=0.10, gain_smooth=0.7,
                                        use_spectral_subtraction=True)
    enhanced = de_emphasis(enhanced)
    enhanced = apply_eq(enhanced, sr, eq_gain=1.0)
    enhanced = dynamic_range_compress(enhanced, sr, ratio=2.0, makeup_gain_db=10.0)
    enhanced, _ = normalize_and_limit(enhanced, audio)
    return enhanced


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
        noisy = (clean + noise).astype(np.float32)
        # Critical distribution match: the CNN sees the DSP output it will
        # receive at inference, while the target remains clean speech.
        noisy = classical_frontend(noisy, self.sr).astype(np.float32)
        return (torch.from_numpy(noisy),
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
    parser.add_argument("--validation-fraction", type=float, default=0.05,
                        help="Middle lexical slice reserved for validation")
    parser.add_argument("--validation-items", type=int, default=24)
    args = parser.parse_args()
    files = sorted(Path(args.data).rglob("*.flac"))
    if not files: raise SystemExit("No FLAC files found under --data")
    test_split = max(1, int(len(files) * (1 - args.holdout_fraction)))
    val_start = max(1, int(test_split * (1 - args.validation_fraction)))
    val_files = files[val_start:test_split]
    files = files[:val_start]
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    print(f"Training on {device}; {len(files)} training clips (final {args.holdout_fraction:.0%} held out)")
    loader = DataLoader(LibriSyntheticNoise(files, args.steps_per_epoch * args.batch_size),
                        batch_size=args.batch_size, shuffle=True, num_workers=0, pin_memory=device.type == "cuda")
    val_set = LibriSyntheticNoise(val_files, args.validation_items)
    model = SpectralMaskDenoiser().to(device)
    opt = torch.optim.AdamW(model.parameters(), lr=2e-3, weight_decay=1e-5)
    scheduler = torch.optim.lr_scheduler.CosineAnnealingLR(opt, T_max=args.epochs, eta_min=2e-5)
    loss_fn = nn.L1Loss(); window = torch.hann_window(512, device=device)
    best_stoi = -float("inf")
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
        scheduler.step()
        model.eval(); stoi_scores = []
        val_loader = DataLoader(val_set, batch_size=args.batch_size, shuffle=False)
        with torch.no_grad():
            for val_noisy, val_clean in val_loader:
                val_noisy, val_clean = val_noisy.to(device), val_clean.to(device)
                val_spec = torch.stft(val_noisy, 512, 128, window=window, return_complex=True)
                val_mask = model(torch.log1p(val_spec.abs()).unsqueeze(1)).squeeze(1)
                val_out = torch.istft(val_spec * val_mask, 512, 128, window=window,
                                      length=val_clean.shape[-1])
                from pystoi import stoi
                for pred, target_clean in zip(val_out.cpu().numpy(), val_clean.cpu().numpy()):
                    stoi_scores.append(float(stoi(target_clean, pred, 16000, extended=False)))
        mean_stoi = float(np.mean(stoi_scores)) if stoi_scores else 0.0
        print(f"epoch {epoch+1} complete: mask L1 {running/len(loader):.4f}; validation STOI {mean_stoi:.4f}; lr {scheduler.get_last_lr()[0]:.6f}", flush=True)
        if mean_stoi > best_stoi:
            best_stoi = mean_stoi
            output = Path(args.output); output.parent.mkdir(parents=True, exist_ok=True)
            torch.save({"model_state": model.state_dict(), "channels": 24, "sample_rate": 16000,
                        "n_fft": 512, "hop": 128, "epochs": epoch + 1,
                        "training_files": len(files), "validation_files": len(val_files),
                        "best_validation_stoi": best_stoi, "input_distribution": "classical_dsp_output"}, output)
            print(f"  saved new best checkpoint (STOI {best_stoi:.4f})", flush=True)
    print(f"Saved best checkpoint: {args.output} (validation STOI {best_stoi:.4f})")

if __name__ == "__main__": main()
