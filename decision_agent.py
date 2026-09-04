"""Explainable parameter policy for the autonomous speech agent."""
from __future__ import annotations


def decide(snr_db: float, stationary_noise: bool, activity_ratio: float) -> dict:
    """Map signal measurements to a pipeline configuration and explanation."""
    params = {
        "pre_emph_coeff": 0.97, "alpha": 1.2, "beta": 0.10,
        "gain_smooth": 0.7, "use_spectral_subtraction": True,
        "use_ml_postfilter": False, "vad_hangover_frames": 0,
        # EQ / compression defaults (may be overridden per mode below)
        "use_eq": True, "eq_gain": 0.6,
        "compression_ratio": 1.8, "compression_makeup_db": 6.0,
    }
    if snr_db > 15:
        # Light touch: high-SNR input — preserve it. Skip all coloration.
        params.update(
            use_spectral_subtraction=False, alpha=1.0, beta=1.0,
            use_eq=False, eq_gain=0.0,
            compression_ratio=1.0, compression_makeup_db=0.0,
        )
        mode = "Light touch"
        rationale = ("High estimated SNR: spectral subtraction, EQ, and compression are "
                     "all bypassed to preserve the original signal.")
    elif snr_db >= 5 and stationary_noise:
        # Classical DSP: stationary noise, moderate processing.
        params.update(
            use_eq=True, eq_gain=0.5,
            compression_ratio=1.8, compression_makeup_db=6.0,
        )
        mode = "Classical DSP"
        rationale = ("Moderate SNR with stationary noise: spectral subtraction + Wiener "
                     "filtering with light EQ and mild compression.")
    else:
        # Full adaptive: low SNR or non-stationary — use stronger processing.
        params.update(
            alpha=1.45, beta=0.07, gain_smooth=0.82, use_ml_postfilter=True,
            use_eq=True, eq_gain=1.0,
            compression_ratio=2.0, compression_makeup_db=10.0,
        )
        mode = "Full adaptive"
        rationale = ("Low SNR or non-stationary noise: stronger adaptive filtering, "
                     "ML post-filter, full EQ, and moderate compression applied.")
    if activity_ratio < 0.30:
        params["vad_hangover_frames"] = 5
        rationale += " Sparse speech detected; VAD hangover is widened."
    return {"mode": mode, "rationale": rationale, "params": params}
