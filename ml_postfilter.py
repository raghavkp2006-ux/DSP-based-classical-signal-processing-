"""ML post-filter with a trained spectral-mask model and safe fallback."""
from __future__ import annotations

import numpy as np


def apply_ml_denoise(audio: np.ndarray, sr: int) -> np.ndarray:
    """Apply the trained CNN when available; otherwise use spectral gating."""
    try:
        from trained_ml_denoiser import DEFAULT_CHECKPOINT, apply_trained_denoise
        if DEFAULT_CHECKPOINT.exists():
            return apply_trained_denoise(audio, sr, DEFAULT_CHECKPOINT)
    except Exception:
        # A model failure must never make the base DSP enhancement unusable.
        pass
    try:
        import noisereduce as nr
    except ImportError as exc:
        raise RuntimeError(
            "ML post-filter needs a trained checkpoint or the optional package 'noisereduce'. "
            "Install it with: pip install noisereduce"
        ) from exc

    result = nr.reduce_noise(y=np.asarray(audio, dtype=np.float32), sr=int(sr),
                             stationary=False, prop_decrease=0.65)
    return np.asarray(result, dtype=float)
