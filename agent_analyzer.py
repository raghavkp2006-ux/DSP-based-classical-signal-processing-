"""Measurements used by the transparent rule-based decision agent."""
from __future__ import annotations

import numpy as np

from dsp_pipeline import frame_params, pre_emphasis, voice_activity_detection


def _global_snr_estimate(audio: np.ndarray, fp: dict) -> float:
    """
    Global SNR estimate via minimum-statistics noise floor.

    Uses the bottom 15% of per-frame energies as a proxy for the noise floor
    (frames where only noise is present), then estimates signal power as
    (total_power - noise_power). This is much better correlated with true
    input SNR than the segmental speech/noise frame ratio, which fails when
    broadband noise inflates the energy of all frames equally.

    Output is clipped to [-20, 40] dB.
    """
    frame_len, hop_len, num_frames = fp["frame_len"], fp["hop_len"], fp["num_frames"]
    energies = np.zeros(num_frames)
    for i in range(num_frames):
        idx = i * hop_len
        frame = audio[idx: idx + frame_len]
        if len(frame) < frame_len:
            frame = np.pad(frame, (0, frame_len - len(frame)))
        energies[i] = np.sum(frame ** 2) / max(1, frame_len)

    sorted_e = np.sort(energies)
    n_noise = max(2, int(0.15 * num_frames))
    noise_floor = float(np.mean(sorted_e[:n_noise]))
    total_power = float(np.mean(energies))

    # Signal power = total - noise (floored so it can't go negative)
    signal_power = max(total_power - noise_floor, 1e-10)
    noise_power = max(noise_floor, 1e-10)

    snr_db = 10.0 * np.log10(signal_power / noise_power)
    return float(np.clip(snr_db, -20.0, 40.0))


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
        "snr_db": round(_global_snr_estimate(audio, fp), 2),
        "noise_stationarity_cv": round(stationarity_cv, 4),
        "stationary_noise": bool(stationarity_cv < 0.55),
        "speech_activity_ratio": round(float(np.mean(speech)), 4),
    }
