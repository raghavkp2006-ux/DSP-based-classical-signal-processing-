"""Mix one clean recording with synthetic noise at a requested SNR."""
from __future__ import annotations

import argparse
import random
from pathlib import Path
import numpy as np
import soundfile as sf
from train_denoiser import LibriSyntheticNoise


def main():
    parser = argparse.ArgumentParser(description="Create one noisy speech WAV at a target SNR.")
    parser.add_argument("clean", help="Clean input audio")
    parser.add_argument("output", help="Output WAV")
    parser.add_argument("--noise", choices=("white", "brown", "tonal", "bursty"), default="white")
    parser.add_argument("--snr", type=float, default=5.0, help="Target SNR in dB")
    parser.add_argument("--seed", type=int, default=301)
    args = parser.parse_args()
    clean, sr = sf.read(args.clean, dtype="float32", always_2d=True)
    clean = clean.mean(axis=1)
    random.seed(args.seed); np.random.seed(args.seed)
    mode = {"white": 0, "brown": 1, "tonal": 2, "bursty": 3}[args.noise]
    state = random.randrange; random.randrange = lambda *a, **k: mode
    try: noise = LibriSyntheticNoise._noise(len(clean)).astype(np.float32)
    finally: random.randrange = state
    noise *= np.sqrt(np.mean(clean**2) / (np.mean(noise**2) + 1e-12)) / (10 ** (args.snr / 20))
    mixed = np.clip(clean + noise, -1, 1)
    Path(args.output).parent.mkdir(parents=True, exist_ok=True)
    sf.write(args.output, mixed, sr)
    print(f"Wrote {args.output} ({args.noise}, {args.snr:g} dB, {sr} Hz)")


if __name__ == "__main__": main()
