"""Measurements used by the transparent rule-based decision agent."""
from __future__ import annotations

import numpy as np

from dsp_pipeline import (estimate_snr, frame_params, pre_emphasis,
                          voice_activity_detection)


def analyze_audio(audio: np.ndarray, sr: int) -> dict:
    """Return SNR, noise stationarity and speech activity for one recording."""
    audio = np.asarray(audio, dtype=float)
    fp = frame_params(len(audio), sr)
    emphasized = pre_emphasis(audio)
    speech, energies, _ = voice_activity_detection(emphasized, fp)
    noise_energies = energies[~speech]
    # Coefficient of variation: 0 means perfectly stationary noise.
    stationarity_cv = float(np.std(noise_energies) / (np.mean(noise_energies) + 1e-12))
    return {
        "snr_db": round(estimate_snr(audio, fp, speech), 2),
        "noise_stationarity_cv": round(stationarity_cv, 4),
        "stationary_noise": bool(stationarity_cv < 0.55),
        "speech_activity_ratio": round(float(np.mean(speech)), 4),
    }
