"""
dsp_pipeline.py
================
Modular, callable version of the classical 9-stage speech-enhancement
pipeline (BECE301L autonomous speech-processing agent project).

Every stage that used to live inline in a single monolithic script is now
its own function with explicit inputs/outputs, so the pipeline can be:
  - unit tested in isolation,
  - called from Flask (app.py) for the web dashboard,
  - called from a notebook / batch script for evaluation on many files,
  - eventually wrapped by a decision agent that swaps parameters per stage.

Pipeline stages
----------------
1. load_audio            - read + mono-fold + normalize
2. pre_emphasis           - high-frequency pre-emphasis filter
3. frame_params            - derive frame/hop/FFT sizing for the signal
4. voice_activity_detection - energy + ZCR based speech/noise labelling
5. estimate_noise_psd      - average PSD over noise-only frames
6. spectral_subtract_wiener - spectral subtraction + adaptive Wiener gain
7. de_emphasis              - inverse of stage 2
8. apply_eq                 - voice-band boosts + rumble/hiss removal
9. dynamic_range_compress   - attack/release compressor + makeup gain
10. normalize_and_limit     - peak/RMS normalization + soft limiter
11. estimate_snr            - segmental SNR, before vs. after (new)

`run_pipeline(...)` orchestrates all of the above and returns a single
result dict consumed by app.py.
"""

import numpy as np
from scipy import signal
import io
import subprocess

# ─────────────────────────────────────────────────────────────────────────
# Stage 1: Load
# ─────────────────────────────────────────────────────────────────────────
def load_audio(filepath):
    """Read audio and recover damaged MP3/MPEG streams with FFmpeg if needed."""
    import soundfile as sf
    try:
        audio_raw, Fs = sf.read(filepath, always_2d=True)
    except Exception as soundfile_error:
        # WhatsApp MPEG audio can contain a damaged frame.  FFmpeg is more
        # tolerant and can discard that frame while retaining the recording.
        try:
            import imageio_ffmpeg
            command = [imageio_ffmpeg.get_ffmpeg_exe(), "-hide_banner", "-loglevel", "error",
                       "-fflags", "+discardcorrupt", "-err_detect", "ignore_err", "-i", str(filepath),
                       "-vn", "-ac", "1", "-f", "wav", "pipe:1"]
            decoded = subprocess.run(command, capture_output=True, check=True, timeout=120)
            audio_raw, Fs = sf.read(io.BytesIO(decoded.stdout), always_2d=True)
        except Exception as ffmpeg_error:
            raise RuntimeError(
                "Audio could not be decoded. The file may be damaged or unsupported. "
                f"FFmpeg recovery failed: {ffmpeg_error}"
            ) from soundfile_error
    audio_raw = audio_raw.mean(axis=1) if audio_raw.shape[1] == 2 else audio_raw[:, 0]
    audio_raw = audio_raw / (np.max(np.abs(audio_raw)) + 1e-10)
    return audio_raw, Fs


# ─────────────────────────────────────────────────────────────────────────
# Stage 2: Pre-emphasis
# ─────────────────────────────────────────────────────────────────────────
def pre_emphasis(audio, coeff=0.97):
    """y[n] = x[n] - coeff * x[n-1]  (flattens the natural -6dB/oct spectral tilt)."""
    return signal.lfilter([1, -coeff], [1], audio)


# ─────────────────────────────────────────────────────────────────────────
# Stage 3: Framing parameters
# ─────────────────────────────────────────────────────────────────────────
def frame_params(N, Fs, frame_duration=0.025, hop_duration=0.010):
    frame_len = int(round(frame_duration * Fs))
    hop_len = int(round(hop_duration * Fs))
    fft_size = int(2 ** np.ceil(np.log2(frame_len * 2)))
    num_frames = max(1, int(np.floor((N - frame_len) / hop_len)) + 1)
    win = np.hanning(frame_len + 1)[:-1]  # periodic Hann
    return {
        "frame_len": frame_len, "hop_len": hop_len, "fft_size": fft_size,
        "num_frames": num_frames, "win": win,
    }


# ─────────────────────────────────────────────────────────────────────────
# Stage 4: Voice Activity Detection
# ─────────────────────────────────────────────────────────────────────────
def voice_activity_detection(audio_preemph, fp, hangover_frames=0):
    frame_len, hop_len, num_frames = fp["frame_len"], fp["hop_len"], fp["num_frames"]
    frame_energy = np.zeros(num_frames)
    frame_zcr = np.zeros(num_frames)

    for i in range(num_frames):
        idx = i * hop_len
        frame = audio_preemph[idx: idx + frame_len]
        if len(frame) < frame_len:
            frame = np.pad(frame, (0, frame_len - len(frame)))
        frame_energy[i] = np.sum(frame ** 2) / frame_len
        frame_zcr[i] = np.sum(np.abs(np.diff(np.sign(frame)))) / (2 * frame_len)

    sorted_energy = np.sort(frame_energy)
    n_noise = max(1, int(round(num_frames * 0.2)))
    noise_floor_energy = np.mean(sorted_energy[:n_noise])

    energy_threshold = noise_floor_energy * 3.0
    zcr_threshold = np.mean(frame_zcr) * 2.0
    is_speech = (frame_energy > energy_threshold) & (frame_zcr < zcr_threshold)

    if np.sum(~is_speech) < round(num_frames * 0.20):
        sorted_idx = np.argsort(frame_energy)
        is_speech[:] = True
        is_speech[sorted_idx[: int(round(num_frames * 0.20))]] = False

    if hangover_frames:
        for offset in range(1, int(hangover_frames) + 1):
            is_speech[offset:] |= is_speech[:-offset]

    return is_speech, frame_energy, frame_zcr


# ─────────────────────────────────────────────────────────────────────────
# Stage 5: Noise PSD estimation
# ─────────────────────────────────────────────────────────────────────────
def estimate_noise_psd(audio_preemph, fp, is_speech):
    frame_len, hop_len, fft_size, num_frames = (
        fp["frame_len"], fp["hop_len"], fp["fft_size"], fp["num_frames"])
    win = fp["win"]
    half_size = fft_size // 2 + 1
    noise_psd = np.zeros(half_size)
    noise_count = 0

    for i in range(num_frames):
        if not is_speech[i]:
            idx = i * hop_len
            frame = audio_preemph[idx: idx + frame_len] * win
            padded = np.concatenate([frame, np.zeros(fft_size - frame_len)])
            S = np.fft.rfft(padded, fft_size)
            noise_psd += np.abs(S) ** 2
            noise_count += 1

    if noise_count > 0:
        noise_psd /= noise_count
    else:
        for i in range(min(10, num_frames)):
            idx = i * hop_len
            frame = audio_preemph[idx: idx + frame_len] * win
            padded = np.concatenate([frame, np.zeros(fft_size - frame_len)])
            S = np.fft.rfft(padded, fft_size)
            noise_psd += np.abs(S) ** 2
        noise_psd /= max(1, min(10, num_frames))

    kernel = np.ones(5) / 5
    noise_psd = np.convolve(noise_psd, kernel, mode="same")
    return noise_psd, noise_count


# ─────────────────────────────────────────────────────────────────────────
# Stage 6: Spectral subtraction + Wiener post-filter
# ─────────────────────────────────────────────────────────────────────────
def spectral_subtract_wiener(audio_preemph, fp, noise_psd, N,
                              alpha=1.2, beta=0.10, gain_smooth=0.7,
                              use_spectral_subtraction=True):
    frame_len, hop_len, fft_size, num_frames = (
        fp["frame_len"], fp["hop_len"], fp["fft_size"], fp["num_frames"])
    win = fp["win"]
    half_size = fft_size // 2 + 1

    output_spec = np.zeros(N + fft_size)
    output_weight = np.zeros(N + fft_size)
    prev_gain = np.ones(half_size)
    win_padded = np.concatenate([win, np.zeros(fft_size - frame_len)])

    for i in range(num_frames):
        idx = i * hop_len
        frame = audio_preemph[idx: idx + frame_len] * win
        padded = np.concatenate([frame, np.zeros(fft_size - frame_len)])
        S_half = np.fft.rfft(padded, fft_size)

        mag = np.abs(S_half)
        phase = np.angle(S_half)
        mag2 = mag ** 2

        mag2_clean = (np.maximum(mag2 - alpha * noise_psd, beta * mag2)
                      if use_spectral_subtraction else mag2)
        mag_clean = np.sqrt(mag2_clean)

        snr_post = np.maximum(mag2 / (noise_psd + 1e-10) - 1, 0)
        wiener_gain = snr_post / (snr_post + 1)
        wiener_gain = gain_smooth * prev_gain + (1 - gain_smooth) * wiener_gain
        wiener_gain = np.maximum(wiener_gain, 0.25)
        prev_gain = wiener_gain

        mag_final = mag_clean * wiener_gain
        S_clean = mag_final * np.exp(1j * phase)
        frame_out = np.fft.irfft(S_clean, fft_size)

        out_idx = slice(idx, idx + fft_size)
        output_spec[out_idx] += frame_out * win_padded
        output_weight[out_idx] += win_padded ** 2

    return output_spec[:N] / (output_weight[:N] + 1e-10)


# ─────────────────────────────────────────────────────────────────────────
# Stage 7: De-emphasis
# ─────────────────────────────────────────────────────────────────────────
def de_emphasis(audio, coeff=0.97):
    return signal.lfilter([1], [1, -coeff], audio)


# ─────────────────────────────────────────────────────────────────────────
# Stage 8: Voice-band EQ
# ─────────────────────────────────────────────────────────────────────────
def apply_eq(audio, Fs):
    out = audio.copy()

    f1_low, f1_high = 300 / (Fs / 2), 500 / (Fs / 2)
    if f1_high < 1.0:
        b1 = signal.firwin(65, [f1_low, f1_high], pass_zero=False)
        out = out + 0.6 * signal.lfilter(b1, 1, out)

    f2_low, f2_high = 1000 / (Fs / 2), min(2500 / (Fs / 2), 0.99)
    b2 = signal.firwin(65, [f2_low, f2_high], pass_zero=False)
    out = out + 0.9 * signal.lfilter(b2, 1, out)

    f3_low, f3_high = 2500 / (Fs / 2), min(4000 / (Fs / 2), 0.99)
    if f3_low < 0.99 and f3_low < f3_high:
        b3 = signal.firwin(65, [f3_low, f3_high], pass_zero=False)
        out = out + 0.4 * signal.lfilter(b3, 1, out)

    b_hp = signal.firwin(129, 80 / (Fs / 2), pass_zero=False)
    out = signal.lfilter(b_hp, 1, out)
    b_lp = signal.firwin(65, min(8000 / (Fs / 2), 0.99))
    out = signal.lfilter(b_lp, 1, out)
    return out


# ─────────────────────────────────────────────────────────────────────────
# Stage 9: Dynamic range compression
# ─────────────────────────────────────────────────────────────────────────
def dynamic_range_compress(audio, Fs, threshold_db=-24.0, ratio=2.5,
                            attack_ms=10.0, release_ms=120.0, makeup_gain=20.0):
    attack_coeff = np.exp(-1.0 / (attack_ms * Fs / 1000.0))
    release_coeff = np.exp(-1.0 / (release_ms * Fs / 1000.0))
    threshold_lin = 10 ** (threshold_db / 20.0)

    N = len(audio)
    out = np.zeros(N)
    gain_smooth = 1.0

    for n in range(N):
        level = abs(audio[n])
        if level > threshold_lin:
            target_gain = (threshold_lin * (level / threshold_lin) ** (1.0 / ratio)
                           / (level + 1e-10))
        else:
            target_gain = 1.0

        if target_gain < gain_smooth:
            gain_smooth = attack_coeff * gain_smooth + (1 - attack_coeff) * target_gain
        else:
            gain_smooth = release_coeff * gain_smooth + (1 - release_coeff) * target_gain

        out[n] = audio[n] * gain_smooth

    out *= 10 ** (makeup_gain / 20.0)
    return out


# ─────────────────────────────────────────────────────────────────────────
# Stage 10: Normalize + limiter
# ─────────────────────────────────────────────────────────────────────────
def normalize_and_limit(audio, audio_raw, target_level=0.99, clip_thresh=0.98):
    out = audio / (np.max(np.abs(audio)) + 1e-10) * target_level
    out = out / np.maximum(1.0, np.abs(out) / clip_thresh)

    original_rms = np.sqrt(np.mean(audio_raw ** 2))
    current_rms = np.sqrt(np.mean(out ** 2))
    if current_rms > 0:
        rms_gain = min(original_rms / current_rms, 10.0)
        out = out * rms_gain
        out = out / (np.max(np.abs(out)) + 1e-10) * target_level
    return out


# ─────────────────────────────────────────────────────────────────────────
# Stage 11 (new): Segmental SNR estimator, driven by the VAD labels
# ─────────────────────────────────────────────────────────────────────────
def estimate_snr(audio, fp, is_speech):
    """
    Segmental SNR in dB: mean speech-frame energy vs. mean noise-frame
    energy, using the VAD's own frame boundaries so it stays consistent
    with the labels the rest of the pipeline already computed.
    """
    frame_len, hop_len, num_frames = fp["frame_len"], fp["hop_len"], fp["num_frames"]
    speech_energies, noise_energies = [], []

    for i in range(num_frames):
        idx = i * hop_len
        frame = audio[idx: idx + frame_len]
        e = np.sum(frame ** 2) / max(1, frame_len)
        (speech_energies if is_speech[i] else noise_energies).append(e)

    speech_power = np.mean(speech_energies) if speech_energies else 1e-10
    noise_power = np.mean(noise_energies) if noise_energies else 1e-10
    snr_db = 10 * np.log10((speech_power + 1e-12) / (noise_power + 1e-12))
    return float(snr_db)


# ─────────────────────────────────────────────────────────────────────────
# Orchestrator
# ─────────────────────────────────────────────────────────────────────────
def run_pipeline(input_filepath, output_filepath, params=None, use_agent=True,
                 pre_emph_coeff=0.97, alpha=1.2, beta=0.10, gain_smooth=0.7,
                 clean_reference=None, feedback_enabled=True,
                 feedback_stoi_threshold=0.80, feedback_snr_threshold=3.0,
                 use_stt_feedback=False):
    """
    Runs all 10 stages end to end and returns a metrics dict plus the
    intermediate arrays the web UI / evaluation scripts need. Writes the
    enhanced audio to output_filepath.
    """
    import soundfile as sf

    stage_log = []

    audio_raw, Fs = load_audio(input_filepath)
    N = len(audio_raw)
    if N < max(8, int(0.03 * Fs)):
        raise ValueError("Audio is too short; upload at least 30 ms of audio.")
    stage_log.append({"stage": "Load & Normalize", "detail": f"{N/Fs:.2f}s @ {Fs} Hz"})

    defaults = {"pre_emph_coeff": pre_emph_coeff, "alpha": alpha, "beta": beta,
                "gain_smooth": gain_smooth, "use_spectral_subtraction": True,
                "use_ml_postfilter": False, "vad_hangover_frames": 0}
    analysis, agent_decision = {}, None
    if use_agent:
        from agent_analyzer import analyze_audio
        from decision_agent import decide
        analysis = analyze_audio(audio_raw, Fs)
        agent_decision = decide(analysis["snr_db"], analysis["stationary_noise"],
                                analysis["speech_activity_ratio"])
        defaults.update(agent_decision["params"])
        stage_log.append({"stage": "Agent Analysis", "detail":
                          f"SNR={analysis['snr_db']:.1f} dB, "
                          f"{'stationary' if analysis['stationary_noise'] else 'non-stationary'} noise"})
        stage_log.append({"stage": f"Agent Decision: {agent_decision['mode']}",
                          "detail": agent_decision["rationale"]})
    if params:
        defaults.update(params)

    audio_preemph = pre_emphasis(audio_raw, defaults["pre_emph_coeff"])
    stage_log.append({"stage": "Pre-emphasis", "detail": f"coeff={defaults['pre_emph_coeff']}"})

    fp = frame_params(N, Fs)
    stage_log.append({"stage": "Framing", "detail":
                       f"{fp['num_frames']} frames, {fp['frame_len']} samples/frame"})

    is_speech, frame_energy, frame_zcr = voice_activity_detection(
        audio_preemph, fp, defaults["vad_hangover_frames"])
    speech_ratio = float(np.sum(is_speech) / fp["num_frames"] * 100)
    stage_log.append({"stage": "Voice Activity Detection",
                       "detail": f"{speech_ratio:.1f}% speech frames"})

    noise_psd, noise_count = estimate_noise_psd(audio_preemph, fp, is_speech)
    stage_log.append({"stage": "Noise PSD Estimation",
                       "detail": f"from {noise_count} noise frames"})

    output_spec = spectral_subtract_wiener(
        audio_preemph, fp, noise_psd, N, alpha=defaults["alpha"],
        beta=defaults["beta"], gain_smooth=defaults["gain_smooth"],
        use_spectral_subtraction=defaults["use_spectral_subtraction"])
    stage_log.append({"stage": "Spectral Subtraction + Wiener Filter",
                       "detail": f"alpha={defaults['alpha']}, beta={defaults['beta']}"})

    audio_deemph = de_emphasis(output_spec, defaults["pre_emph_coeff"])
    stage_log.append({"stage": "De-emphasis", "detail": "inverse pre-emphasis"})

    audio_eq = apply_eq(audio_deemph, Fs)
    stage_log.append({"stage": "Voice-band EQ", "detail": "warmth + presence + crispness"})

    audio_comp = dynamic_range_compress(audio_eq, Fs)
    stage_log.append({"stage": "Dynamic Range Compression", "detail": "2.5:1, +20dB makeup"})

    audio_final = normalize_and_limit(audio_comp, audio_raw)
    stage_log.append({"stage": "Normalize & Limit", "detail": "peak + RMS matched"})

    ml_postfilter_applied = False
    if defaults["use_ml_postfilter"]:
        try:
            from ml_postfilter import apply_ml_denoise
            audio_final = normalize_and_limit(apply_ml_denoise(audio_final, Fs), audio_raw)
            ml_postfilter_applied = True
            stage_log.append({"stage": "ML Post-filter", "detail": "adaptive spectral gating applied"})
        except RuntimeError as exc:
            stage_log.append({"stage": "ML Post-filter", "detail": f"skipped: {exc}"})

    snr_before = estimate_snr(audio_raw, fp, is_speech)
    snr_after = estimate_snr(audio_final, fp, is_speech)
    stage_log.append({"stage": "SNR Estimation",
                       "detail": f"{snr_before:.1f} dB -> {snr_after:.1f} dB"})

    sf.write(output_filepath, audio_final, Fs)

    in_rms = float(np.sqrt(np.mean(audio_raw ** 2)))
    out_rms = float(np.sqrt(np.mean(audio_final ** 2)))
    rms_gain_db = float(20 * np.log10((out_rms + 1e-12) / (in_rms + 1e-12)))

    metrics = {
        "Fs": int(Fs), "duration_sec": float(N / Fs), "num_frames": int(fp["num_frames"]),
        "speech_ratio_pct": speech_ratio, "noise_frames": int(noise_count),
        "snr_before_db": round(snr_before, 2), "snr_after_db": round(snr_after, 2),
        "snr_improvement_db": round(snr_after - snr_before, 2),
        "in_rms": round(in_rms, 4), "out_rms": round(out_rms, 4),
        "rms_gain_db": round(rms_gain_db, 2),
        "in_peak": round(float(np.max(np.abs(audio_raw))), 4),
        "out_peak": round(float(np.max(np.abs(audio_final))), 4),
        "ml_postfilter_used": ml_postfilter_applied,
    }

    result = {
        "audio_raw": audio_raw, "audio_final": audio_final, "Fs": Fs, "N": N,
        "fp": fp, "is_speech": is_speech, "frame_energy": frame_energy,
        "metrics": metrics, "stage_log": stage_log,
        "analysis": analysis, "agent_decision": agent_decision, "params": defaults,
    }
    if not feedback_enabled:
        return result

    # ── Task 3: Self-Evaluation Feedback Loop ────────────────────────────
    reference_scores = None
    quality_name = "snr_improvement_db"
    quality_value = metrics["snr_improvement_db"]
    threshold = feedback_snr_threshold

    if clean_reference is not None:
        from self_eval import evaluate
        reference_scores = evaluate(clean_reference, audio_final, Fs)
        if reference_scores.get("stoi") is not None:
            quality_name = "stoi"
            quality_value = reference_scores["stoi"]
            threshold = feedback_stoi_threshold

    quality_failed = quality_value < threshold
    stt_word_loss_detected = False

    if use_stt_feedback and clean_reference is None:
        try:
            from stt_module import transcribe
            raw_t = transcribe(input_filepath)
            enh_t = transcribe(output_filepath)
            if raw_t.get("available") and enh_t.get("available"):
                raw_words = len(raw_t.get("text", "").split())
                enh_words = len(enh_t.get("text", "").split())
                if raw_words > 3 and enh_words < int(raw_words * 0.75):
                    stt_word_loss_detected = True
                    quality_failed = True
        except Exception:
            pass

    current_mode = (agent_decision or {}).get("mode", "Manual")
    attempt = {
        "mode": current_mode,
        "quality_metric": quality_name,
        "quality_value": quality_value,
        "metrics": metrics,
        "reference_scores": reference_scores,
    }

    feedback = {
        "corrected": False,
        "kept": "initial",
        "initial_mode": current_mode,
        "retry_mode": None,
        "quality_metric": quality_name,
        "initial_quality": quality_value,
        "retry_quality": None,
        "threshold": threshold,
        "attempts": [attempt],
        "reason": f"{quality_name}={quality_value} (threshold {threshold})"
                  + (" [STT word loss]" if stt_word_loss_detected else ""),
    }

    # Tier escalation: Light touch -> Classical DSP -> Full adaptive
    if quality_failed and current_mode != "Full adaptive":
        if current_mode == "Light touch":
            next_mode = "Classical DSP"
            next_params = {
                "pre_emph_coeff": defaults.get("pre_emph_coeff", 0.97),
                "alpha": 1.2, "beta": 0.10, "gain_smooth": 0.7,
                "use_spectral_subtraction": True, "use_ml_postfilter": False,
                "vad_hangover_frames": defaults.get("vad_hangover_frames", 0),
            }
        else:
            next_mode = "Full adaptive"
            next_params = {
                "pre_emph_coeff": defaults.get("pre_emph_coeff", 0.97),
                "alpha": 1.45, "beta": 0.07, "gain_smooth": 0.82,
                "use_spectral_subtraction": True, "use_ml_postfilter": True,
                "vad_hangover_frames": 5,
            }

        retry = run_pipeline(
            input_filepath, output_filepath, params=next_params, use_agent=False,
            clean_reference=clean_reference, feedback_enabled=False,
        )

        retry_scores = None
        retry_quality = retry["metrics"]["snr_improvement_db"]
        if clean_reference is not None:
            from self_eval import evaluate
            retry_scores = evaluate(clean_reference, retry["audio_final"], retry["Fs"])
            if retry_scores.get("stoi") is not None:
                retry_quality = retry_scores["stoi"]

        retry_attempt = {
            "mode": next_mode,
            "quality_metric": quality_name,
            "quality_value": retry_quality,
            "metrics": retry["metrics"],
            "reference_scores": retry_scores,
        }
        feedback["attempts"].append(retry_attempt)
        feedback["corrected"] = True
        feedback["retry_mode"] = next_mode
        feedback["retry_quality"] = retry_quality

        if retry_quality >= quality_value:
            result = retry
            feedback["kept"] = "retry"
            result["agent_decision"] = {
                "mode": next_mode,
                "rationale": f"Self-correction escalated from {current_mode} to {next_mode} ({quality_name}: {quality_value:.3f} -> {retry_quality:.3f}).",
                "params": next_params,
            }
        else:
            feedback["kept"] = "initial"
            sf.write(output_filepath, audio_final, Fs)
            if result.get("agent_decision"):
                result["agent_decision"]["rationale"] += (
                    f" Self-correction tested {next_mode}, but {current_mode} was retained "
                    f"({quality_name} {quality_value:.3f} vs {retry_quality:.3f})."
                )

        result["stage_log"].append({
            "stage": "Self-evaluation feedback",
            "detail": f"{quality_name}: {quality_value:.3f} -> {retry_quality:.3f}; escalated {current_mode} -> {next_mode}; kept {feedback['kept']}",
        })

    result["feedback"] = feedback
    return result
