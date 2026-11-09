"""Render the README charts into docs/assets/ (light and dark versions).

Backgrounds are transparent so the charts sit on any GitHub theme
(light, dark, dark dimmed). Numbers are copied from ml/reports/llm/evaluation-report.md; update them here
when that report changes. Usage: python scripts/make_readme_charts.py [OUT_DIR]
"""
import sys
from pathlib import Path
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.patches import Rectangle
from matplotlib.ticker import FuncFormatter

OUT = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).resolve().parent.parent / "docs" / "assets"
OUT.mkdir(parents=True, exist_ok=True)

THEMES = {
    "light": dict(bg="#ffffff", ink="#0b0b0b", ink2="#52514e", muted="#8a8984",
                  grid="#e6e5e0", accent="#2a78d6", neutral="#a9a8a2", zone="#2a78d6"),
    "dark": dict(bg="#0d1117", ink="#f0efec", ink2="#c3c2b7", muted="#8f8e88",
                 grid="#30363d", accent="#3987e5", neutral="#6e6d68", zone="#3987e5"),
}


def style(ax, t):
    ax.set_facecolor("none")
    for s in ("top", "right"):
        ax.spines[s].set_visible(False)
    for s in ("left", "bottom"):
        ax.spines[s].set_color(t["grid"])
    ax.tick_params(colors=t["ink2"], labelsize=9, length=0)
    ax.grid(True, color=t["grid"], linewidth=0.8)
    ax.set_axisbelow(True)


# ---------- 1. Run trajectory: human-reviewed accuracy vs missed risk ----------
# Relabeled 414-case split, 156 human-reviewed; risk models read as the app reads them.
runs = [
    # label, missed risk (of 188 risky), human-reviewed acc, highlight, label offset
    ("run 3 · Q4_K_M", 52, 72.4, False, (8, 8)),
    ("run 4 · Q4_K_M", 20, 71.8, False, (8, -14)),
    ("run 5 · Q4_K_M", 24, 66.7, False, (8, -4)),
    ("run 6 · Q4_K_M", 25, 78.8, False, (-8, -4)),
    ("run 6 · Q8_0 (shipped)", 24, 78.8, True, (0, -18)),
    ("teacher (Claude Sonnet 5.5)", 21, 82.1, None, (0, -18)),
]
TARGET_ACC, TARGET_MISSED = 72.4, 25


def chart_runs(t, name):
    fig, ax = plt.subplots(figsize=(7.2, 4.2), dpi=200)
    fig.patch.set_alpha(0)
    style(ax, t)
    # acceptance region set before run 6: acc > 72.4, missed <= 25 (only that corner is shaded)
    ax.add_patch(Rectangle((15, TARGET_ACC), TARGET_MISSED - 15, 85 - TARGET_ACC,
                           facecolor=t["zone"], alpha=0.12, lw=0, zorder=0))
    ax.axhline(TARGET_ACC, color=t["muted"], lw=1, ls=(0, (4, 3)))
    ax.axvline(TARGET_MISSED, color=t["muted"], lw=1, ls=(0, (4, 3)))
    ax.text(25.6, 84.2, "target set before run 6 →\n> 72.4% and ≤ 25 misses", color=t["ink2"], fontsize=8, va="top", ha="right", linespacing=1.4)
    for label, x, y, hi, off in runs:
        if hi:
            c, s, z = t["accent"], 90, 4
        elif hi is None:
            c, s, z = "none", 70, 3
        else:
            c, s, z = t["neutral"], 60, 3
        ax.scatter(x, y, s=s, color=c, edgecolor=t["accent"] if hi is None else "none",
                   linewidth=1.6 if hi is None else 2, zorder=z)
        ax.annotate(label, (x, y), xytext=off, textcoords="offset points",
                    fontsize=8.5, color=t["ink"] if hi else t["ink2"],
                    fontweight="bold" if hi else "normal", ha="center" if off[0] == 0 else ("right" if off[0] < 0 else "left"))
    ax.set_xlim(55, 15)  # fewer misses to the right
    ax.set_ylim(64, 85)
    ax.set_yticks(range(65, 86, 5))
    ax.yaxis.set_major_formatter(FuncFormatter(lambda v, _: f"{v:.0f}%"))
    ax.set_xlabel("Missed risky pages (of 188)  →  fewer", color=t["ink2"], fontsize=9)
    ax.set_ylabel("Human-reviewed accuracy (156 cases)", color=t["ink2"], fontsize=9)
    ax.set_title("Run 6 is the first model inside the target box", loc="left",
                 color=t["ink"], fontsize=11, fontweight="bold", pad=12)
    fig.tight_layout()
    fig.savefig(OUT / f"runs-{name}.png", transparent=True)
    plt.close(fig)


# ---------- 2. Quantization stability: agreement with f16 ----------
quant = ["Q4_K_M", "Q5_K_M", "Q8_0"]
run2 = [65.3, 88.1, 96.6]
run3 = [91.5, 92.4, 97.5]


def chart_quant(t, name):
    fig, ax = plt.subplots(figsize=(7.2, 2.8), dpi=200)
    fig.patch.set_alpha(0)
    style(ax, t)
    ax.grid(axis="y", visible=False)
    ys = list(range(len(quant)))[::-1]
    for y, a, b in zip(ys, run2, run3):
        ax.plot([a, b], [y, y], color=t["grid"], lw=3, solid_capstyle="round", zorder=1)
        ax.scatter(a, y, s=70, color=t["neutral"], edgecolor="none", zorder=2)
        ax.scatter(b, y, s=70, color=t["accent"], edgecolor="none", zorder=3)
        ax.text(b + 1.0, y, f"{b:.1f}%", va="center", fontsize=8.5, color=t["ink"])
        ax.text(min(a, b) - 1.2, y, f"{a:.1f}%", va="center", ha="right", fontsize=8.5, color=t["ink2"])
    ax.set_yticks(ys, quant)
    ax.set_xlim(55, 103)
    ax.set_ylim(-0.5, 2.5)
    ax.set_xlabel("Same risk as the f16 file, 118-case test split", color=t["ink2"], fontsize=9)
    ax.scatter([], [], color=t["neutral"], s=50, label="run 2 (3 epochs)")
    ax.scatter([], [], color=t["accent"], s=50, label="run 3 (5 epochs, batch 8)")
    leg = ax.legend(loc="lower left", bbox_to_anchor=(0, 1.0), ncol=2, frameon=False, fontsize=8.5, handletextpad=0.3, columnspacing=1.5)
    for txt in leg.get_texts():
        txt.set_color(t["ink2"])
    ax.xaxis.set_major_formatter(FuncFormatter(lambda v, _: f"{v:.0f}%"))
    ax.set_title("Longer training kept quantized files close to f16", loc="left",
                 color=t["ink"], fontsize=11, fontweight="bold", pad=28)
    fig.tight_layout()
    fig.savefig(OUT / f"quant-{name}.png", transparent=True)
    plt.close(fig)


for name, t in THEMES.items():
    chart_runs(t, name)
    chart_quant(t, name)
print("wrote", sorted(p.name for p in OUT.glob("*.png")))
