"""Optional Whisper transcription wrapper with lazy model loading."""
from __future__ import annotations

import os
from pathlib import Path
import subprocess
import numpy as np


def transcribe(audio_path: str, model_size: str = "base") -> dict:
    try:
        import whisper
    except ImportError:
        return {"text": "", "available": False,
                "message": "Whisper is not installed (pip install openai-whisper)."}
    try:
        # Decode with the bundled FFmpeg and pass samples directly to Whisper.
        # This avoids Whisper's hard dependency on a system-wide `ffmpeg.exe`.
        import imageio_ffmpeg
        command = [imageio_ffmpeg.get_ffmpeg_exe(), "-hide_banner", "-loglevel", "error",
                   "-fflags", "+discardcorrupt", "-err_detect", "ignore_err", "-i", audio_path,
                   "-vn", "-ac", "1", "-ar", "16000", "-f", "f32le", "pipe:1"]
        decoded = subprocess.run(command, capture_output=True, check=True, timeout=120)
        samples = np.frombuffer(decoded.stdout, dtype=np.float32).copy()
        if samples.size == 0:
            raise RuntimeError("FFmpeg decoded no audio samples.")
        model = whisper.load_model(model_size)
        result = model.transcribe(samples, fp16=False)
        return {"text": result.get("text", "").strip(), "available": True, "message": ""}
    except Exception as exc:
        return {"text": "", "available": False, "message": str(exc)}
