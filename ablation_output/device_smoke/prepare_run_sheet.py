"""Prepare the local, gitignored >=2-minute smoke clip and a manual run sheet.

This script reads only the local synthetic evaluation WAVs. It does not copy
audio to the phone. The phone input picker is SAF-only, so the owner must stage
the selected inputs through USB/MTP before running the manual sheet.
"""

from __future__ import annotations

import csv
import struct
import wave
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = Path(__file__).resolve().parent
OUT.mkdir(parents=True, exist_ok=True)


def wav_info(path: Path) -> tuple[int, int, int, int]:
    data = path.read_bytes()
    if data[:4] != b"RIFF" or data[8:12] != b"WAVE":
        raise ValueError(f"Not a RIFF/WAVE file: {path}")
    cursor = 12
    sample_rate = channels = bits = block_align = data_bytes = 0
    while cursor + 8 <= len(data):
        chunk_id = data[cursor:cursor + 4]
        chunk_size = struct.unpack_from("<I", data, cursor + 4)[0]
        payload = data[cursor + 8:cursor + 8 + chunk_size]
        if chunk_id == b"fmt ":
            _, channels, sample_rate, _, block_align, bits = struct.unpack_from("<HHIIHH", payload)
        elif chunk_id == b"data":
            data_bytes = chunk_size
        cursor += 8 + chunk_size + (chunk_size & 1)
    if not (sample_rate and channels and bits and block_align and data_bytes):
        raise ValueError(f"Missing WAV format/data chunk: {path}")
    return sample_rate, data_bytes // block_align, channels, bits // 8


def write_concatenated(paths: list[Path], output: Path) -> tuple[int, float]:
    if not paths:
        raise RuntimeError("No source WAVs found")
    first = wav_info(paths[0])
    if any(wav_info(path)[::2] != first[::2] or wav_info(path)[3] != first[3] for path in paths):
        raise RuntimeError("WAVs differ in sample rate, channels, or sample width")
    with wave.open(str(output), "wb") as dst:
        with wave.open(str(paths[0]), "rb") as src:
            dst.setparams(src.getparams())
        for path in paths:
            with wave.open(str(path), "rb") as src:
                dst.writeframes(src.readframes(src.getnframes()))
    rate, frames, _, _ = wav_info(output)
    return rate, frames / rate


def row(clip: str, path: Path, clean: Path | None, route: str, listen: str) -> dict[str, str]:
    rate, frames, channels, width = wav_info(path)
    return {
        "clip": clip,
        "local_input_path": str(path.resolve()),
        "clean_reference_path": str(clean.resolve()) if clean and clean.exists() else "",
        "duration_s": f"{frames / rate:.3f}",
        "sample_rate_hz": str(rate),
        "channels": str(channels),
        "sample_width_bits": str(width * 8),
        "expected_route_or_record_from_app": route,
        "what_to_listen_for": listen,
        "my_rating": "",
        "notes": "",
    }


noisy = ROOT / "ablation_testset/noisy"
clean_dir = ROOT / "ablation_testset/clean"
stress = ROOT / "ablation_output/v111_stress/inputs"
sid = "holdout_00_7976-110124-0013"

light = noisy / f"{sid}_white_+15dB.wav"
classical = noisy / f"{sid}_white_+05dB.wav"
full = noisy / f"{sid}_white_-05dB.wav"
step = stress / f"step_up_4x_power_{sid}_white_noisy.wav"
late = stress / f"noise_last_2s_{sid}_white_noisy.wav"
continuous = stress / f"continuous_speech_final_3s_{sid}_white_noisy.wav"
for path in (light, classical, full, step, late, continuous):
    if not path.is_file():
        raise FileNotFoundError(path)

# 40 original 3-second conditions make a 120-second mixed-condition stress clip.
long_sources = sorted(noisy.glob("*.wav"))
if len(long_sources) != 40:
    raise RuntimeError(f"Expected 40 original noisy evaluation clips; found {len(long_sources)}")
long_noisy = OUT / "long_2min_noisy.wav"
rate, duration = write_concatenated(long_sources, long_noisy)
if duration < 120.0:
    raise RuntimeError(f"Long clip is only {duration:.3f} seconds")

# Match the same file IDs in the clean directory for optional metric comparison.
long_clean_sources = []
for source in long_sources:
    file_id = source.name.split("_", 1)[0] + "_" + source.name.split("_", 2)[1]
    # The source ID itself contains one underscore after "holdout"; keep the
    # filename through its first two underscore-delimited fields.
    file_id = "_".join(source.name.split("_")[:3])
    clean_path = clean_dir / f"{file_id}.wav"
    if not clean_path.is_file():
        raise FileNotFoundError(clean_path)
    long_clean_sources.append(clean_path)
long_clean = OUT / "long_2min_clean.wav"
write_concatenated(long_clean_sources, long_clean)

clean_ref = clean_dir / f"{sid}.wav"
rows = [
    row("1-light-touch", light, clean_ref, "Light touch (original C3 routing: white +15 dB)", "Clean/high-SNR: any pumping or unnecessary coloration?"),
    row("2-classical-stationary", classical, clean_ref, "Classical DSP (original C3 routing: white +5 dB)", "Musical-noise waves; speech consonants and pauses."),
    row("3-full-onnx", full, clean_ref, "Full adaptive (original C3 routing: white -5 dB)", "Speech artifacts, latency, and ONNX fallback warning."),
    row("4-step-up-4x", step, stress / f"step_up_4x_power_{sid}_white_clean.wav", "Record chosenMode from filtered app log/UI", "Quiet first half: muffled or pumping after the mid-file 4x noise step?"),
    row("5-noise-last-2s", late, stress / f"noise_last_2s_{sid}_white_clean.wav", "Record chosenMode from filtered app log/UI", "Clean first part: any speech suppression before late noise begins?"),
    row("6-continuous-speech-final-3s", continuous, stress / f"continuous_speech_final_3s_{sid}_white_clean.wav", "Record chosenMode from filtered app log/UI", "Final 3 seconds: speech continuity, pumping, or loss of syllables?"),
    row("7-long-2min", long_noisy, long_clean, "Record chosenMode from filtered app log/UI", "Check sustained output, thermal behavior, and memory over 2 minutes."),
]

with (OUT / "clips.csv").open("w", newline="", encoding="utf-8") as handle:
    writer = csv.DictWriter(handle, fieldnames=rows[0].keys())
    writer.writeheader()
    writer.writerows(rows)

with (OUT / "listening_sheet.csv").open("w", newline="", encoding="utf-8") as handle:
    writer = csv.DictWriter(handle, fieldnames=["clip", "route", "what_to_listen_for", "my_rating", "notes"])
    writer.writeheader()
    for item in rows:
        writer.writerow({
            "clip": item["clip"],
            "route": item["expected_route_or_record_from_app"],
            "what_to_listen_for": item["what_to_listen_for"],
            "my_rating": "",
            "notes": "",
        })

print(f"Created {long_noisy} at {rate} Hz, {duration:.3f} s from {len(long_sources)} clips")
print(f"Created {long_clean} at {wav_info(long_clean)[0]} Hz, {wav_info(long_clean)[1] / wav_info(long_clean)[0]:.3f} s")
print(f"Wrote {OUT / 'clips.csv'} and {OUT / 'listening_sheet.csv'}")
