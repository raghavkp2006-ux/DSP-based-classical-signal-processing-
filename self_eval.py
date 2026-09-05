"""Reference-based and reference-free enhancement evaluation utilities."""
from __future__ import annotations

import numpy as np


PESQ_AVAILABLE = False


def evaluate(clean_ref, enhanced, sr: int, baseline=None) -> dict:
    """Evaluate aligned signals using STOI only in this project environment."""
    if isinstance(clean_ref, (str, bytes)):
        import soundfile as sf
        clean_ref, ref_sr = sf.read(clean_ref)
        if clean_ref.ndim > 1:
            clean_ref = np.mean(clean_ref, axis=1)
        if ref_sr != sr:
            from scipy.signal import resample_poly
            from math import gcd
            g = gcd(ref_sr, sr)
            clean_ref = resample_poly(clean_ref, sr // g, ref_sr // g)
    clean = np.asarray(clean_ref, dtype=float)
    enhanced = np.asarray(enhanced, dtype=float)
    n = min(len(clean), len(enhanced))
    clean, enhanced = clean[:n], enhanced[:n]
    result = {"reference_available": True}
    try:
        from pystoi import stoi
        result["stoi"] = round(float(stoi(clean, enhanced, sr, extended=False)), 4)
    except ImportError:
        result["stoi"] = None
    result["metric_note"] = "PESQ unavailable in this environment; all reported quality metrics are STOI-based."
    return result
