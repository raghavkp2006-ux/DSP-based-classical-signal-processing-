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

By default, debug mode is **off** and the server binds to `127.0.0.1:5000`. For local development with auto-reload, set `FLASK_DEBUG=1`. To change the port, set the `PORT` environment variable.

### Environment variables

| Variable | Default | Purpose |
|---|---|---|
| `FLASK_DEBUG` | `0` | Set to `1` for development auto-reload |
| `PORT` | `5000` | Server port |
| `MAX_AUDIO_SECONDS` | `600` | Max decoded audio duration (seconds) |
| `PIPELINE_TIMEOUT_SECONDS` | `60` | Wall-clock timeout for the pipeline |
| `FILE_RETENTION_SECONDS` | `3600` | Auto-cleanup age for upload/output files |
| `PROCESS_RATE_LIMIT` | `10 per minute` | Rate limit on `/process` endpoint |

## Train the ML post-filter

Use clean `.flac` speech such as LibriSpeech `dev-clean`:

```powershell
python train_denoiser.py --data "C:\path\to\LibriSpeech\dev-clean" --epochs 8 --steps-per-epoch 500
```

The training script saves two files:
- `models/spectral_mask_denoiser.pt` — model weights only (loaded with `weights_only=True`)
- `models/spectral_mask_denoiser.json` — hyperparameter metadata sidecar

Both are tracked in git (the checkpoint is ~50KB, small enough to not need Git LFS).

## Layout

- `dsp_pipeline.py` — enhancement pipeline and tolerant MPEG decoding
- `agent_analyzer.py`, `decision_agent.py` — analysis and autonomous policy
- `trained_ml_denoiser.py`, `train_denoiser.py` — CNN inference and training
- `ml_postfilter.py`, `stt_module.py`, `self_eval.py` — ML, STT, and evaluation modules
- `app.py`, `templates/` — Flask dashboard

The FFmpeg fallback can recover many partially damaged MP3/MPEG/WhatsApp recordings. Reference-based STOI requires the clean original alongside each noisy test clip.

## Evaluation metric

This environment uses **STOI-only** reporting. PESQ is unavailable because its native extension requires Microsoft C++ Build Tools; all reported quality metrics and ablation conclusions are therefore STOI-based.

## Final validated results

The final ablation uses the retrained DSP-aware ML checkpoint. `dsp_ml` significantly
outperforms `dsp_only` on STOI (p=0.000146), and the autonomous `agent` also significantly
outperforms `dsp_only` on STOI (p=0.000231). Segmental SNR improvement alone is not a
reliable quality proxy: it rewarded an earlier checkpoint that suppressed speech energy
indiscriminately, so STOI is the primary reported metric.

The remaining limitation is that the agent still trails the unprocessed baseline on STOI
(0.822 vs 0.891), likely near the practical ceiling for these DSP stages without further
hurting intelligibility.
