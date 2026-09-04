"""Run reproducible DSP/ML/agent ablations and write report-ready results."""
from __future__ import annotations

import argparse
import csv
from pathlib import Path

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np
import soundfile as sf

from dsp_pipeline import (estimate_snr, frame_params, pre_emphasis,
                          run_pipeline, voice_activity_detection)
from self_eval import evaluate

ALL_CONFIGS = ["unprocessed", "dsp_only", "dsp_ml", "agent"]
CONFIGS = {
    "dsp_only": {"use_agent": False, "params": {
        "use_ml_postfilter": False,
        "use_eq": True, "eq_gain": 1.0,
        "compression_ratio": 2.0, "compression_makeup_db": 10.0,
    }},
    "dsp_ml": {"use_agent": False, "params": {
        "use_ml_postfilter": True,
        "use_eq": True, "eq_gain": 1.0,
        "compression_ratio": 2.0, "compression_makeup_db": 10.0,
    }},
    "agent": {"use_agent": True, "params": None},
}


def transcript_proxy(clean_path: str, enhanced_path: str) -> int | None:
    from stt_module import transcribe
    clean = transcribe(clean_path)
    enhanced = transcribe(enhanced_path)
    if not clean["available"] or not enhanced["available"]:
        return None
    return len(enhanced["text"].split()) - len(clean["text"].split())


def mean_or_blank(values):
    values = [float(v) for v in values if v not in (None, "")]
    return round(float(np.mean(values)), 4) if values else ""


def write_summary(rows, output: Path):
    fields = ("config", "snr_db_condition", "runs", "mean_snr_improvement_db", "mean_stoi", "mean_pesq")
    summary = []
    configs_to_summarize = [c for c in ALL_CONFIGS if any(r["config"] == c for r in rows)]
    for config in configs_to_summarize:
        for condition in sorted({int(row["snr_db_condition"]) for row in rows}):
            group = [r for r in rows if r["config"] == config and int(r["snr_db_condition"]) == condition]
            summary.append({"config": config, "snr_db_condition": condition, "runs": len(group),
                            "mean_snr_improvement_db": mean_or_blank(r["snr_improvement_db"] for r in group),
                            "mean_stoi": mean_or_blank(r["stoi"] for r in group),
                            "mean_pesq": mean_or_blank(r["pesq"] for r in group)})
        group = [r for r in rows if r["config"] == config]
        summary.append({"config": config, "snr_db_condition": "all", "runs": len(group),
                        "mean_snr_improvement_db": mean_or_blank(r["snr_improvement_db"] for r in group),
                        "mean_stoi": mean_or_blank(r["stoi"] for r in group),
                        "mean_pesq": mean_or_blank(r["pesq"] for r in group)})
    with output.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=fields); writer.writeheader(); writer.writerows(summary)
    return summary


def plot_metric(summary, metric, title, output: Path):
    rows = [r for r in summary if r["snr_db_condition"] == "all"]
    labels = [r["config"].replace("_", " + ") for r in rows]
    values = [float(r[metric]) if r[metric] != "" else 0.0 for r in rows]
    plt.style.use("dark_background")
    fig, ax = plt.subplots(figsize=(7.5, 4.2), facecolor="#0a0e14")
    ax.set_facecolor("#0f1520")
    palette = ["#5c6773", "#8a97a8", "#ffb454", "#33e1d6"]
    colors = palette[:len(rows)] if len(rows) <= len(palette) else plt.cm.tab10(np.linspace(0, 1, len(rows)))
    bars = ax.bar(labels, values, color=colors, width=.55)
    ax.bar_label(bars, fmt="%.2f", padding=4, color="#e7ecf3")
    ax.set_title(title, color="#e7ecf3", fontweight="bold")
    ax.set_ylabel("dB" if "snr" in metric else "Score")
    ax.grid(axis="y", color="#273142", alpha=.6, linestyle="--")
    fig.tight_layout(); fig.savefig(output, dpi=160, facecolor=fig.get_facecolor()); plt.close(fig)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--metadata", default="ablation_testset/metadata.csv")
    parser.add_argument("--output", default="ablation_output")
    parser.add_argument("--limit", type=int, default=None, help="Pilot limit, applied before the three configs")
    parser.add_argument("--use-stt", action="store_true")
    args = parser.parse_args()
    with open(args.metadata, newline="", encoding="utf-8") as handle:
        metadata = list(csv.DictReader(handle))
    if args.limit:
        metadata = metadata[:args.limit]
    output = Path(args.output); audio_dir = output / "audio"; audio_dir.mkdir(parents=True, exist_ok=True)
    rows = []
    for index, item in enumerate(metadata, 1):
        clean, sr = sf.read(item["clean_path"], dtype="float32")
        noisy, _ = sf.read(item["noisy_path"], dtype="float32")
        print(f"[{index}/{len(metadata)}] {item['file_id']} {item['noise_type']} {item['snr_db']} dB", flush=True)

        # 1. Compute fixed evaluation VAD and snr_before ONCE for this noisy recording
        fp = frame_params(len(noisy), sr)
        eval_preemph = pre_emphasis(noisy, 0.97)
        eval_is_speech, _, _ = voice_activity_detection(eval_preemph, fp, hangover_frames=0)
        eval_snr_before = estimate_snr(noisy, fp, eval_is_speech)

        # 2. Raw noisy baseline (unprocessed)
        noisy_scores = evaluate(clean, noisy, sr)
        noisy_stt = transcript_proxy(item["clean_path"], item["noisy_path"]) if args.use_stt else None
        rows.append({"file_id": item["file_id"], "noise_type": item["noise_type"],
                     "snr_db_condition": item["snr_db"], "config": "unprocessed",
                     "snr_before_db": round(eval_snr_before, 2), "snr_after_db": round(eval_snr_before, 2),
                     "snr_improvement_db": 0.0, "stoi": noisy_scores.get("stoi"),
                     "pesq": noisy_scores.get("pesq"), "transcript_delta": noisy_stt})

        # 3. Processed configurations
        for config, settings in CONFIGS.items():
            enhanced_path = audio_dir / f"{item['file_id']}_{item['noise_type']}_{item['snr_db']}dB_{config}.wav"
            result = run_pipeline(item["noisy_path"], enhanced_path, params=settings["params"],
                                  use_agent=settings["use_agent"], clean_reference=clean,
                                  feedback_enabled=False,
                                  eval_is_speech=eval_is_speech, eval_snr_before=eval_snr_before)
            scores = evaluate(clean, result["audio_final"], sr)
            transcript_delta = transcript_proxy(item["clean_path"], str(enhanced_path)) if args.use_stt else None
            metrics = result["metrics"]
            rows.append({"file_id": item["file_id"], "noise_type": item["noise_type"],
                         "snr_db_condition": item["snr_db"], "config": config,
                         "snr_before_db": metrics["snr_before_db"], "snr_after_db": metrics["snr_after_db"],
                         "snr_improvement_db": metrics["snr_improvement_db"], "stoi": scores.get("stoi"),
                         "pesq": scores.get("pesq"), "transcript_delta": transcript_delta})
    fields = ("file_id", "noise_type", "snr_db_condition", "config", "snr_before_db", "snr_after_db",
              "snr_improvement_db", "stoi", "pesq", "transcript_delta")
    with (output / "ablation_results.csv").open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=fields); writer.writeheader(); writer.writerows(rows)
    summary = write_summary(rows, output / "ablation_summary.csv")
    plot_metric(summary, "mean_snr_improvement_db", "Mean Segmental SNR Improvement", output / "snr_improvement.png")
    plot_metric(summary, "mean_stoi", "Mean STOI", output / "stoi.png")
    for row in [r for r in summary if r["snr_db_condition"] == "all"]:
        print(row)


if __name__ == "__main__":
    main()
