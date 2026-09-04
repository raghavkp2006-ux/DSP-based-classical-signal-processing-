"""Small trained spectral-mask denoiser used after the classical DSP chain."""
from __future__ import annotations

from pathlib import Path
import numpy as np
import torch
from torch import nn
from scipy.signal import resample_poly

DEFAULT_CHECKPOINT = Path(__file__).with_name("models") / "spectral_mask_denoiser.pt"


class SpectralMaskDenoiser(nn.Module):
    """Compact 2D CNN that estimates an ideal-ratio mask per STFT bin."""
    def __init__(self, channels: int = 24):
        super().__init__()
        self.net = nn.Sequential(
            nn.Conv2d(1, channels, 5, padding=2), nn.BatchNorm2d(channels), nn.SiLU(),
            nn.Conv2d(channels, channels, 3, padding=1), nn.BatchNorm2d(channels), nn.SiLU(),
            nn.Conv2d(channels, channels, 3, padding=1), nn.SiLU(),
            nn.Conv2d(channels, 1, 1), nn.Sigmoid(),
        )

    def forward(self, x):
        return self.net(x)


def load_model(checkpoint: str | Path = DEFAULT_CHECKPOINT, device: str | None = None):
    checkpoint = Path(checkpoint)
    if not checkpoint.exists():
        raise FileNotFoundError(f"No trained model at {checkpoint}")
    dev = torch.device(device or ("cuda" if torch.cuda.is_available() else "cpu"))
    payload = torch.load(checkpoint, map_location=dev, weights_only=False)
    model = SpectralMaskDenoiser(payload.get("channels", 24)).to(dev)
    model.load_state_dict(payload["model_state"])
    model.eval()
    return model, payload, dev


def apply_trained_denoise(audio: np.ndarray, sr: int,
                          checkpoint: str | Path = DEFAULT_CHECKPOINT) -> np.ndarray:
    """Enhance arbitrary-rate mono audio with the trained spectral mask."""
    model, payload, device = load_model(checkpoint)
    target_sr = int(payload.get("sample_rate", 16000))
    x = np.asarray(audio, dtype=np.float32)
    peak = max(float(np.max(np.abs(x))), 1e-7)
    x = x / peak
    if sr != target_sr:
        from math import gcd
        divisor = gcd(sr, target_sr)
        x16 = resample_poly(x, target_sr // divisor, sr // divisor).astype(np.float32)
    else:
        x16 = x
    n_fft, hop = int(payload.get("n_fft", 512)), int(payload.get("hop", 128))
    window = torch.hann_window(n_fft, device=device)
    with torch.no_grad():
        wave = torch.from_numpy(x16).to(device)
        spec = torch.stft(wave, n_fft=n_fft, hop_length=hop, window=window,
                          return_complex=True, center=True)
        mag = spec.abs()
        features = torch.log1p(mag).unsqueeze(0).unsqueeze(0)
        mask = model(features).squeeze(0).squeeze(0)
        enhanced = torch.istft(spec * mask, n_fft=n_fft, hop_length=hop,
                               window=window, length=len(x16), center=True)
    out = enhanced.detach().cpu().numpy() * peak
    if sr != target_sr:
        from math import gcd
        divisor = gcd(sr, target_sr)
        out = resample_poly(out, sr // divisor, target_sr // divisor)
    return np.asarray(out[:len(audio)], dtype=float)
