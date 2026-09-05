"""Paired statistical tests for the held-out ablation results."""
from __future__ import annotations

import argparse
import csv
from pathlib import Path

import numpy as np
from scipy.stats import ttest_rel


def load_rows(path):
    with open(path, newline="", encoding="utf-8") as handle:
        return list(csv.DictReader(handle))


def paired(rows, left, right, metric):
    key = lambda row: (row["file_id"], row["noise_type"], row["snr_db_condition"])
    left_rows = {key(r): r for r in rows if r["config"] == left}
    right_rows = {key(r): r for r in rows if r["config"] == right}
    keys = sorted(set(left_rows) & set(right_rows))
    x, y = [], []
    for k in keys:
        try:
            xv, yv = float(left_rows[k][metric]), float(right_rows[k][metric])
        except (TypeError, ValueError):
            continue
        if np.isfinite(xv) and np.isfinite(yv):
            x.append(xv); y.append(yv)
    if len(x) < 2:
        return {"n": len(x), "t_statistic": "", "p_value": "", "mean_difference": "",
                "interpretation": "Not enough paired observations."}
    x, y = np.asarray(x), np.asarray(y)
    t_stat, p_value = ttest_rel(x, y)
    difference = float(np.mean(y - x))
    if p_value < 0.05:
        direction = "outperforms" if difference > 0 else "underperforms"
        interpretation = f"{right} significantly {direction} {left} on {metric}, p={p_value:.4g}."
    else:
        interpretation = f"No significant difference between {left} and {right} on {metric}, p={p_value:.4g}."
    return {"n": len(x), "t_statistic": round(float(t_stat), 6), "p_value": round(float(p_value), 6),
            "mean_difference": round(difference, 6), "interpretation": interpretation}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", default="ablation_output/full/ablation_results.csv")
    parser.add_argument("--output", default="ablation_output/full/statistical_tests.csv")
    args = parser.parse_args()
    rows = load_rows(args.input)
    tests = [
        ("dsp_only_vs_agent", "dsp_only", "agent", "snr_improvement_db"),
        ("dsp_only_vs_agent", "dsp_only", "agent", "stoi"),
        ("dsp_only_vs_dsp_ml", "dsp_only", "dsp_ml", "stoi"),
        ("agent_vs_unprocessed", "agent", "unprocessed", "stoi"),
    ]
    output_rows = []
    for comparison, left, right, metric in tests:
        result = paired(rows, left, right, metric)
        output_rows.append({"comparison": comparison, "left_config": left, "right_config": right,
                            "metric": metric, **result})
        print(result["interpretation"])
    fields = ("comparison", "left_config", "right_config", "metric", "n", "t_statistic", "p_value",
              "mean_difference", "interpretation")
    output = Path(args.output); output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=fields); writer.writeheader(); writer.writerows(output_rows)
    print(f"Saved {output}")


if __name__ == "__main__":
    main()
