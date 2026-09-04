"""Explainable parameter policy for the autonomous speech agent."""
from __future__ import annotations


def decide(snr_db: float, stationary_noise: bool, activity_ratio: float) -> dict:
    """Map signal measurements to a pipeline configuration and explanation."""
    params = {
        "pre_emph_coeff": 0.97, "alpha": 1.2, "beta": 0.10,
        "gain_smooth": 0.7, "use_spectral_subtraction": True,
        "use_ml_postfilter": False, "vad_hangover_frames": 0,
    }
    if snr_db > 15:
        params.update(use_spectral_subtraction=False, alpha=1.0, beta=1.0)
        mode = "Light touch"
        rationale = "High estimated SNR: preserve the original signal and apply only finishing stages."
    elif snr_db >= 5 and stationary_noise:
        mode = "Classical DSP"
        rationale = "Moderate SNR with stationary noise: spectral subtraction and Wiener filtering are appropriate."
    else:
        params.update(alpha=1.45, beta=0.07, gain_smooth=0.82, use_ml_postfilter=True)
        mode = "Full adaptive"
        rationale = "Low SNR or non-stationary noise: use stronger adaptive filtering and the optional ML post-filter."
    if activity_ratio < 0.30:
        params["vad_hangover_frames"] = 5
        rationale += " Sparse speech detected; VAD hangover is widened."
    return {"mode": mode, "rationale": rationale, "params": params}
