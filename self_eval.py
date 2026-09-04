"""Reference-based and reference-free enhancement evaluation utilities."""
from __future__ import annotations

import numpy as np


def evaluate(clean_ref, enhanced, sr: int, baseline=None) -> dict:
    """Evaluate aligned signals; PESQ/STOI are reported when installed."""
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
    try:
        from pesq import pesq
        mode = "wb" if sr >= 16000 else "nb"
        rate = 16000 if mode == "wb" else 8000
        if sr != rate:
            from scipy.signal import resample_poly
            from math import gcd
            factor = gcd(sr, rate)
            clean_eval = resample_poly(clean, rate // factor, sr // factor)
            enhanced_eval = resample_poly(enhanced, rate // factor, sr // factor)
        else:
            clean_eval, enhanced_eval = clean, enhanced
        result["pesq"] = round(float(pesq(rate, clean_eval, enhanced_eval, mode)), 3)
    except ImportError:
        result["pesq"] = None
    return result
