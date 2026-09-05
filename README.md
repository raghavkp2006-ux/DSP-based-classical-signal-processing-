# SignalChain — Autonomous Speech Enhancement Agent

SignalChain listens to noisy audio, measures its conditions, selects an enhancement strategy, improves the signal, transcribes it, and records the result.

## Features

- **Classical DSP:** pre-emphasis, VAD, noise PSD, spectral subtraction, adaptive Wiener filtering, de-emphasis, voice EQ, compression, normalization, and SNR measurement.
- **Rule-based decision agent:** estimates SNR, noise stationarity, and speech activity, then selects an explainable processing policy.
- **Trained ML post-filter:** compact CNN spectral-mask denoiser trained with synthetic noisy/clean speech pairs.
- **Whisper STT:** optional raw-versus-enhanced transcription using Whisper Base.
- **Self-evaluation:** STOI-based scoring and decision logging for ablation studies.
- **Flask dashboard:** upload audio, inspect the decision, listen to both signals, and view waveform, spectrogram, PSD, and VAD results.

## Run

```powershell
python -m pip install -r requirements.txt
python app.py
```

Open `http://127.0.0.1:5000`.

## Train the ML post-filter

Use clean `.flac` speech such as LibriSpeech `dev-clean`:

```powershell
python train_denoiser.py --data "C:\path\to\LibriSpeech\dev-clean" --epochs 8 --steps-per-epoch 500
```

The checkpoint is saved to `models/spectral_mask_denoiser.pt`. It is intentionally excluded from Git as a generated binary artifact; use Git LFS if you want to publish it.

## Layout

- `dsp_pipeline.py` — enhancement pipeline and tolerant MPEG decoding
- `agent_analyzer.py`, `decision_agent.py` — analysis and autonomous policy
- `trained_ml_denoiser.py`, `train_denoiser.py` — CNN inference and training
- `ml_postfilter.py`, `stt_module.py`, `self_eval.py` — ML, STT, and evaluation modules
- `app.py`, `templates/` — Flask dashboard

The FFmpeg fallback can recover many partially damaged MP3/MPEG/WhatsApp recordings. Reference-based STOI requires the clean original alongside each noisy test clip.

## Evaluation metric

This environment uses **STOI-only** reporting. PESQ is unavailable because its native extension requires Microsoft C++ Build Tools; all reported quality metrics and ablation conclusions are therefore STOI-based.
