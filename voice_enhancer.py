import numpy as np
from scipy import signal

import soundfile as sf
import matplotlib.pyplot as plt
import warnings
warnings.filterwarnings('ignore')

# ── Try to import sounddevice for playback (optional) ──────────────────────
try:
    import sounddevice as sd
    PLAYBACK_AVAILABLE = True
except ImportError:
    PLAYBACK_AVAILABLE = False
    print("[WARN] sounddevice not installed — audio playback disabled.")
    print("       Install with: pip install sounddevice\n")

print("=" * 50)
print("   PROFESSIONAL VOICE ENHANCER - PYTHON")
print("=" * 50)
print()

# ─────────────────────────────────────────────────────────────────────────────
# STEP 1: LOAD AUDIO
# ─────────────────────────────────────────────────────────────────────────────
audio_raw, Fs = sf.read("your_audio.wav", always_2d=True)

if audio_raw.shape[1] == 2:
    print("[INFO] Stereo detected - converting to mono...")
    audio_raw = audio_raw.mean(axis=1)
else:
    audio_raw = audio_raw[:, 0]

# Normalize
audio_raw = audio_raw / (np.max(np.abs(audio_raw)) + 1e-10)
N = len(audio_raw)
t = np.arange(N) / Fs

print(f"[INFO] Audio loaded: {N/Fs:.2f} seconds at {Fs} Hz")
print(f"[INFO] Total samples: {N}\n")

# ─────────────────────────────────────────────────────────────────────────────
# STEP 2: PRE-EMPHASIS FILTER
# ─────────────────────────────────────────────────────────────────────────────
print("[STEP 1] Applying Pre-emphasis filter...")
pre_emph_coeff = 0.97
# MATLAB: filter([1, -a], 1, x)  →  scipy: lfilter(b, a, x)
audio_preemph = signal.lfilter([1, -pre_emph_coeff], [1], audio_raw)

# ─────────────────────────────────────────────────────────────────────────────
# STEP 3: FRAMING PARAMETERS
# ─────────────────────────────────────────────────────────────────────────────
frame_duration = 0.025          # 25 ms
hop_duration   = 0.010          # 10 ms

frame_len  = int(round(frame_duration * Fs))
hop_len    = int(round(hop_duration   * Fs))
fft_size   = int(2 ** np.ceil(np.log2(frame_len * 2)))   # next power of 2
num_frames = int(np.floor((N - frame_len) / hop_len)) + 1

print(f"[INFO] Frame length: {frame_len} samples ({frame_duration*1000:.1f}ms)")
print(f"[INFO] Hop length:   {hop_len} samples ({hop_duration*1000:.1f}ms)")
print(f"[INFO] Total frames: {num_frames}\n")

# Hann window (periodic, matching MATLAB's hann(...,'periodic'))
win = np.hanning(frame_len + 1)[:-1]     # length = frame_len, periodic

# ─────────────────────────────────────────────────────────────────────────────
# STEP 4: VOICE ACTIVITY DETECTION (VAD)
# ─────────────────────────────────────────────────────────────────────────────
print("[STEP 2] Running Voice Activity Detection (VAD)...")

frame_energy = np.zeros(num_frames)
frame_zcr    = np.zeros(num_frames)   # Zero Crossing Rate

for i in range(num_frames):
    idx   = i * hop_len
    frame = audio_preemph[idx : idx + frame_len]
    frame_energy[i] = np.sum(frame ** 2) / frame_len
    frame_zcr[i]    = np.sum(np.abs(np.diff(np.sign(frame)))) / (2 * frame_len)

sorted_energy      = np.sort(frame_energy)
n_noise            = max(1, int(round(num_frames * 0.2)))
noise_floor_energy = np.mean(sorted_energy[:n_noise])

energy_threshold = noise_floor_energy * 3.0
zcr_threshold    = np.mean(frame_zcr) * 2.0

is_speech = (frame_energy > energy_threshold) & (frame_zcr < zcr_threshold)

# If too few noise frames, force bottom 20 % as noise
if np.sum(~is_speech) < round(num_frames * 0.20):
    print("[WARN] Too few noise frames. Forcing bottom 20% energy frames as noise.")
    sorted_idx = np.argsort(frame_energy)
    is_speech[:] = True
    is_speech[sorted_idx[: int(round(num_frames * 0.20))]] = False

speech_ratio = np.sum(is_speech) / num_frames * 100
print(f"[INFO] Speech detected in {speech_ratio:.1f}% of frames")
print(f"[INFO] Noise frames available: {np.sum(~is_speech)}\n")

# ─────────────────────────────────────────────────────────────────────────────
# STEP 5: NOISE SPECTRUM ESTIMATION
# ─────────────────────────────────────────────────────────────────────────────
print("[STEP 3] Estimating noise spectrum...")

half_size  = fft_size // 2 + 1
noise_psd  = np.zeros(half_size)
noise_count = 0

for i in range(num_frames):
    if not is_speech[i]:
        idx   = i * hop_len
        frame = audio_preemph[idx : idx + frame_len] * win
        padded = np.concatenate([frame, np.zeros(fft_size - frame_len)])
        S      = np.fft.rfft(padded, fft_size)
        noise_psd  += np.abs(S) ** 2
        noise_count += 1

if noise_count > 0:
    noise_psd /= noise_count
else:
    print("[WARN] No silence detected. Using first 10 frames for noise estimate.")
    for i in range(min(10, num_frames)):
        idx   = i * hop_len
        frame = audio_preemph[idx : idx + frame_len] * win
        padded = np.concatenate([frame, np.zeros(fft_size - frame_len)])
        S      = np.fft.rfft(padded, fft_size)
        noise_psd += np.abs(S) ** 2
    noise_psd /= 10

# Smooth noise estimate (5-tap moving average)
kernel    = np.ones(5) / 5
noise_psd = np.convolve(noise_psd, kernel, mode='same')
print(f"[INFO] Noise estimated from {noise_count} frames\n")

# ─────────────────────────────────────────────────────────────────────────────
# STEP 6: SPECTRAL SUBTRACTION + WIENER FILTER
# ─────────────────────────────────────────────────────────────────────────────
print("[STEP 4] Applying Spectral Subtraction + Wiener Filter...")

alpha        = 1.2          # Over-subtraction factor
beta         = 0.10         # Spectral floor
gain_smooth  = 0.7          # Temporal smoothing

output_spec   = np.zeros(N + fft_size)
output_weight = np.zeros(N + fft_size)
prev_gain     = np.ones(half_size)

win_padded = np.concatenate([win, np.zeros(fft_size - frame_len)])

for i in range(num_frames):
    idx   = i * hop_len
    frame = audio_preemph[idx : idx + frame_len] * win
    padded = np.concatenate([frame, np.zeros(fft_size - frame_len)])

    S_half = np.fft.rfft(padded, fft_size)      # rfft gives half spectrum directly

    mag   = np.abs(S_half)
    phase = np.angle(S_half)
    mag2  = mag ** 2

    # Spectral Subtraction
    mag2_clean = mag2 - alpha * noise_psd
    mag2_clean = np.maximum(mag2_clean, beta * mag2)
    mag_clean  = np.sqrt(mag2_clean)

    # Wiener Post-Filter
    snr_post    = np.maximum(mag2 / (noise_psd + 1e-10) - 1, 0)
    wiener_gain = snr_post / (snr_post + 1)
    wiener_gain = gain_smooth * prev_gain + (1 - gain_smooth) * wiener_gain
    wiener_gain = np.maximum(wiener_gain, 0.25)     # floor: never fully mute voice
    prev_gain   = wiener_gain

    mag_final  = mag_clean * wiener_gain
    S_clean    = mag_final * np.exp(1j * phase)

    # IFFT via irfft (automatically constructs conjugate-symmetric spectrum)
    frame_out  = np.fft.irfft(S_clean, fft_size)

    out_idx = slice(idx, idx + fft_size)
    output_spec[out_idx]   += frame_out * win_padded
    output_weight[out_idx] += win_padded ** 2

output_spec = output_spec[:N] / (output_weight[:N] + 1e-10)
print("[INFO] Spectral processing complete\n")

# ─────────────────────────────────────────────────────────────────────────────
# STEP 7: DE-EMPHASIS
# ─────────────────────────────────────────────────────────────────────────────
print("[STEP 5] Applying De-emphasis...")
audio_deemph = signal.lfilter([1], [1, -pre_emph_coeff], output_spec)

# ─────────────────────────────────────────────────────────────────────────────
# STEP 8: VOICE FREQUENCY BAND BOOSTING (EQ)
# ─────────────────────────────────────────────────────────────────────────────
print("[STEP 6] Boosting voice frequency bands (EQ)...")
audio_eq = audio_deemph.copy()

# Band 1: Warmth boost (300–500 Hz) +4 dB
f1_low  = 300  / (Fs / 2)
f1_high = 500  / (Fs / 2)
if f1_high < 1.0:
    b1 = signal.firwin(65, [f1_low, f1_high], pass_zero=False)
    boost1   = signal.lfilter(b1, 1, audio_eq)
    audio_eq = audio_eq + 0.6 * boost1

# Band 2: Presence boost (1000–2500 Hz) +6 dB
f2_low  = 1000 / (Fs / 2)
f2_high = min(2500 / (Fs / 2), 0.99)
b2      = signal.firwin(65, [f2_low, f2_high], pass_zero=False)
boost2   = signal.lfilter(b2, 1, audio_eq)
audio_eq = audio_eq + 0.9 * boost2

# Band 3: Crispness boost (2500–4000 Hz) +3 dB
f3_low  = 2500 / (Fs / 2)
f3_high = min(4000 / (Fs / 2), 0.99)
if f3_low < 0.99 and f3_low < f3_high:
    b3 = signal.firwin(65, [f3_low, f3_high], pass_zero=False)
    boost3   = signal.lfilter(b3, 1, audio_eq)
    audio_eq = audio_eq + 0.4 * boost3

# High-pass at 80 Hz — remove rumble/hum
b_hp     = signal.firwin(129, 80 / (Fs / 2), pass_zero=False)
audio_eq = signal.lfilter(b_hp, 1, audio_eq)

# Low-pass at 8000 Hz — remove high-frequency hiss
b_lp     = signal.firwin(65, min(8000 / (Fs / 2), 0.99))
audio_eq = signal.lfilter(b_lp, 1, audio_eq)

print("[INFO] EQ applied: warmth + presence + crispness boosted\n")

# ─────────────────────────────────────────────────────────────────────────────
# STEP 9: DYNAMIC RANGE COMPRESSION
# ─────────────────────────────────────────────────────────────────────────────
print("[STEP 7] Applying Dynamic Range Compression...")

threshold_db  = -24.0
ratio         = 2.5
attack_ms     = 10.0
release_ms    = 120.0
makeup_gain   = 20.0

attack_coeff  = np.exp(-1.0 / (attack_ms  * Fs / 1000.0))
release_coeff = np.exp(-1.0 / (release_ms * Fs / 1000.0))
threshold_lin = 10 ** (threshold_db / 20.0)

gain_smooth_comp = 1.0
audio_comp = np.zeros(N)

for n in range(N):
    sample_level = abs(audio_eq[n])
    if sample_level > threshold_lin:
        target_gain = (threshold_lin *
                       (sample_level / threshold_lin) ** (1.0 / ratio) /
                       (sample_level + 1e-10))
    else:
        target_gain = 1.0

    if target_gain < gain_smooth_comp:
        gain_smooth_comp = (attack_coeff  * gain_smooth_comp +
                            (1 - attack_coeff)  * target_gain)
    else:
        gain_smooth_comp = (release_coeff * gain_smooth_comp +
                            (1 - release_coeff) * target_gain)

    audio_comp[n] = audio_eq[n] * gain_smooth_comp

makeup_lin  = 10 ** (makeup_gain / 20.0)
audio_comp *= makeup_lin

print(f"[INFO] Compression: threshold={threshold_db:.0f}dB, "
      f"ratio={ratio:.0f}:1, makeup=+{makeup_gain:.0f}dB\n")

# ─────────────────────────────────────────────────────────────────────────────
# STEP 10: FINAL NORMALIZATION & HARD LIMITER
# ─────────────────────────────────────────────────────────────────────────────
print("[STEP 8] Normalizing and limiting output...")

target_level = 0.99
audio_final  = audio_comp / (np.max(np.abs(audio_comp)) + 1e-10) * target_level

clip_thresh = 0.98
audio_final = audio_final / np.maximum(1.0, np.abs(audio_final) / clip_thresh)

original_rms = np.sqrt(np.mean(audio_raw ** 2))
current_rms  = np.sqrt(np.mean(audio_final ** 2))
if current_rms > 0:
    rms_gain    = min(original_rms / current_rms, 10.0)
    audio_final = audio_final * rms_gain
    audio_final = audio_final / (np.max(np.abs(audio_final)) + 1e-10) * target_level

print(f"[INFO] Output normalized to {target_level*100:.0f}%\n")

# ─────────────────────────────────────────────────────────────────────────────
# STEP 11: SAVE OUTPUT
# ─────────────────────────────────────────────────────────────────────────────
output_filename = "voice_enhanced_output.wav"
sf.write(output_filename, audio_final, Fs)
print(f"[OUTPUT] Saved to: {output_filename}\n")

# ─────────────────────────────────────────────────────────────────────────────
# STEP 12: RESULTS SUMMARY
# ─────────────────────────────────────────────────────────────────────────────
in_rms   = np.sqrt(np.mean(audio_raw   ** 2))
out_rms  = np.sqrt(np.mean(audio_final ** 2))
rms_gain_db = 20 * np.log10(out_rms / (in_rms + 1e-10))

print("=" * 50)
print("   ENHANCEMENT SUMMARY")
print("=" * 50)
print(f"Input  RMS Level : {in_rms:.4f}")
print(f"Output RMS Level : {out_rms:.4f}")
print(f"RMS Gain Applied : {rms_gain_db:.2f} dB")
print(f"Input  Peak      : {np.max(np.abs(audio_raw)):.4f}")
print(f"Output Peak      : {np.max(np.abs(audio_final)):.4f}")
print("=" * 50)

# ─────────────────────────────────────────────────────────────────────────────
# STEP 13: PLOTS
# ─────────────────────────────────────────────────────────────────────────────

# ── 1. Waveform Comparison ────────────────────────────────────────────────
fig1, axes = plt.subplots(1, 2, figsize=(14, 4))
fig1.canvas.manager.set_window_title("Waveform Comparison")

axes[0].plot(t, audio_raw, color=(0.2, 0.4, 0.8), linewidth=0.6)
axes[0].set_title("ORIGINAL Audio", fontsize=13, fontweight='bold')
axes[0].set_xlabel("Time (s)"); axes[0].set_ylabel("Amplitude")
axes[0].set_ylim([-1.1, 1.1]); axes[0].grid(True)

axes[1].plot(t, audio_final, color=(0.1, 0.7, 0.3), linewidth=0.6)
axes[1].set_title("ENHANCED Audio", fontsize=13, fontweight='bold')
axes[1].set_xlabel("Time (s)"); axes[1].set_ylabel("Amplitude")
axes[1].set_ylim([-1.1, 1.1]); axes[1].grid(True)

plt.tight_layout()

# ── 2. Spectrogram Comparison ─────────────────────────────────────────────
fig2, axes2 = plt.subplots(1, 2, figsize=(14, 5))
fig2.canvas.manager.set_window_title("Spectrogram Comparison")

for ax, audio, title in zip(axes2,
                             [audio_raw, audio_final],
                             ["ORIGINAL Spectrogram", "ENHANCED Spectrogram"]):
    f_spec, t_spec, Sxx = signal.spectrogram(audio, Fs,
                                              window=signal.windows.hann(256),
                                              nperseg=256, noverlap=128,
                                              nfft=512)
    ax.pcolormesh(t_spec, f_spec / 1000, 10 * np.log10(Sxx + 1e-12),
                  shading='gouraud', cmap='jet', vmin=-80)
    ax.set_ylim([0, min(8, Fs / 2000)])
    ax.set_title(title, fontsize=13, fontweight='bold')
    ax.set_xlabel("Time (s)"); ax.set_ylabel("Frequency (kHz)")

plt.tight_layout()

# ── 3. Power Spectral Density Comparison ─────────────────────────────────
fig3, ax3 = plt.subplots(figsize=(8, 4))
fig3.canvas.manager.set_window_title("Frequency Response")

f1, P1 = signal.welch(audio_raw,   Fs, window='hann', nperseg=1024, noverlap=512, nfft=1024)
f2, P2 = signal.welch(audio_final, Fs, window='hann', nperseg=1024, noverlap=512, nfft=1024)

ax3.plot(f1, 10 * np.log10(P1 + 1e-12), 'b', linewidth=1.2, label='Original')
ax3.plot(f2, 10 * np.log10(P2 + 1e-12), 'g', linewidth=1.5, label='Enhanced')
ax3.set_xlim([0, min(8000, Fs / 2)])
ax3.set_title("Power Spectral Density Comparison", fontsize=13, fontweight='bold')
ax3.set_xlabel("Frequency (Hz)"); ax3.set_ylabel("Power (dB/Hz)")
ax3.legend(loc='upper right'); ax3.grid(True)
plt.tight_layout()

# ── 4. VAD Result ─────────────────────────────────────────────────────────
fig4, ax4 = plt.subplots(figsize=(10, 3))
fig4.canvas.manager.set_window_title("Voice Activity Detection")

frame_times = np.arange(num_frames) * hop_len / Fs
ax4.fill_between(frame_times,
                 is_speech.astype(float) * np.max(frame_energy),
                 alpha=0.3, color=(0.2, 0.8, 0.2), label='Speech Regions')
ax4.plot(frame_times, frame_energy, 'b', linewidth=1, label='Frame Energy')
ax4.set_title("Voice Activity Detection Result", fontsize=13, fontweight='bold')
ax4.set_xlabel("Time (s)"); ax4.set_ylabel("Energy")
ax4.legend(); ax4.grid(True)
plt.tight_layout()

plt.show()

# ─────────────────────────────────────────────────────────────────────────────
# STEP 14: PLAYBACK (requires sounddevice)
# ─────────────────────────────────────────────────────────────────────────────
if PLAYBACK_AVAILABLE:
    import time
    print("\nPlaying ORIGINAL audio...")
    sd.play(audio_raw, Fs)
    sd.wait()
    time.sleep(0.5)

    print("Playing ENHANCED audio...")
    sd.play(audio_final, Fs)
    sd.wait()

print("\nDone! Check voice_enhanced_output.wav")
