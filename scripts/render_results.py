"""Render measured CSV results into reproducible report tables and plots."""
import csv
from pathlib import Path
from statistics import mean, stdev

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

ROOT = Path(__file__).resolve().parents[1]
TABLES = ROOT / "results" / "tables"
PLOTS = ROOT / "results" / "plots"
SIZES = [100, 1000, 10000, 100000]
COLORS = {"DynamicArray": "#1864ab", "LinkedList": "#c05621", "MinHeap": "#1864ab"}
with (TABLES / "summary.csv").open(encoding="utf-8") as source:
    rows = list(csv.DictReader(source))
with (TABLES / "raw.csv").open(encoding="utf-8") as source:
    raw = list(csv.DictReader(source))


def validate():
    assert len(rows) == 56 and len(raw) == 280
    assert len({(r["workload"], r["structure"], r["n"]) for r in rows}) == 56
    for row in rows:
        group = [r for r in raw if all(r[k] == row[k] for k in ("workload", "structure", "n"))]
        assert sorted(int(r["run"]) for r in group) == [1, 2, 3, 4, 5]
        times = [int(r["time_ns"]) / 1e6 for r in group]
        assert abs(mean(times) - float(row["mean_ms"])) <= 0.00000051
        assert abs(stdev(times) - float(row["stddev_ms"])) <= 0.00000051
        assert int(row["n"]) in SIZES
        for key in ("m", "accesses", "comparisons", "movements"):
            assert all(r[key] == row[key] for r in group)
        assert len({r["checksum"] for r in group}) == 1
        n, m = int(row["n"]), int(row["m"])
        work, structure = row["workload"], row["structure"]
        index = n // 2 if work.endswith("middle") else 0
        if work.startswith("remove"):
            assert m == min(1000, n - index)
            if structure == "DynamicArray":
                movements = m * (n - index - 1) - m * (m - 1) // 2
                assert int(row["movements"]) == movements
                assert int(row["accesses"]) == movements + m
            else:
                assert int(row["accesses"]) == m * (index + 1)
        elif work.startswith("insert"):
            assert m == 1000
            if structure == "DynamicArray":
                capacity = 8
                while capacity < n:
                    capacity *= 2
                copies = 0
                while capacity < n + m:
                    copies += capacity
                    capacity *= 2
                assert int(row["movements"]) == m * (n - index) + m * (m - 1) // 2 + copies
            else:
                assert int(row["accesses"]) == m * index
        elif work == "access":
            assert m == 10000
            if structure == "DynamicArray":
                assert int(row["accesses"]) == m
        elif work == "search":
            assert m == 1000
            counterpart = next(r for r in rows if r["workload"] == work and r["n"] == row["n"] and r["structure"] != structure)
            assert row["comparisons"] == counterpart["comparisons"]
            assert all(int(r["checksum"]) == 500 for r in group)
    print("Validated 280 raw runs, 56 means, repeat counts and exact work formulas.")


def series(workload, structure):
    return sorted((r for r in rows if r["workload"] == workload and r["structure"] == structure), key=lambda r: int(r["n"]))


def configure(axis, title, ylabel):
    axis.set_title(title, fontsize=11, loc="left")
    axis.set_xscale("log")
    axis.set_yscale("log")
    axis.set_xlabel("Initial elements n (log scale)")
    axis.set_ylabel(ylabel)
    axis.grid(True, which="major", alpha=0.2)
    axis.spines[["top", "right"]].set_visible(False)


def plot():
    PLOTS.mkdir(parents=True, exist_ok=True)
    plt.rcParams.update({"font.family": "DejaVu Sans", "font.size": 9, "figure.dpi": 140})
    panels = [("access", "Random access; m = 10,000"), ("search", "Search; m = 1,000"),
              ("insert_front", "Front insertion; m = 1,000"), ("remove_front", "Front removal; m = min(1,000, n)"),
              ("insert_middle", "Middle insertion; m = 1,000"), ("remove_middle", "Middle removal; m = min(1,000, n - floor(n/2))")]
    for kind in ("time", "operations"):
        fig, axes = plt.subplots(3, 2, figsize=(12, 12), constrained_layout=True)
        fig.suptitle("Execution time vs. n" if kind == "time" else "Logical operations vs. n", fontsize=18)
        for axis, (workload, title) in zip(axes.flat, panels):
            for structure in ("DynamicArray", "LinkedList"):
                data = series(workload, structure)
                metric = "mean_ms" if kind == "time" else "comparisons" if workload == "search" else "accesses" if workload == "access" or structure == "LinkedList" else "movements"
                x = [int(r["n"]) for r in data]
                y = [float(r[metric]) for r in data]
                label = structure if kind == "time" else f"{structure}: {metric}"
                if all(v == 0 for v in y):
                    axis.text(0.04, 0.78, "LinkedList: 0 existing-node visits;\n1,000 node allocations (not counted)", transform=axis.transAxes, color=COLORS[structure])
                else:
                    axis.plot(x, y, "o-", label=label, color=COLORS[structure], linewidth=1.7, markersize=4)
                    if kind == "time":
                        # Range is always positive and honest on a log axis.
                        axis.fill_between(x, [float(r["min_ms"]) for r in data], [float(r["max_ms"]) for r in data], color=COLORS[structure], alpha=0.12)
            configure(axis, title, "Mean total time (ms, log scale)" if kind == "time" else "Count per workload (log scale)")
            axis.legend(loc="best", fontsize=8)
        fig.supxlabel("Source: results/tables/summary.csv; seed 42; 5 measured repeats. " + ("Shading: min–max of repeats." if kind == "time" else "Visits and movements are different logical units, not CPU instructions."), fontsize=9)
        fig.savefig(PLOTS / f"{kind}_vs_n.png")
        fig.savefig(PLOTS / f"{kind}_vs_n.svg")
        plt.close(fig)
    fig, axes = plt.subplots(1, 2, figsize=(12, 4.5), constrained_layout=True)
    for workload, label, color in (("heap_insert", "Insert n keys", "#1864ab"), ("heap_extract", "Extract n minima", "#c05621")):
        data = series(workload, "MinHeap")
        x = [int(r["n"]) for r in data]
        for axis, metric in zip(axes, ("mean_ms", "comparisons")):
            axis.plot(x, [float(r[metric]) for r in data], "o-", label=label, color=color)
    configure(axes[0], "Priority processing: execution time", "Mean total time (ms, log scale)")
    configure(axes[1], "Priority processing: key comparisons", "Comparisons (log scale)")
    for axis in axes:
        axis.legend()
    fig.supxlabel("Source: results/tables/summary.csv; seed 42; 5 measured repeats; m = n.", fontsize=9)
    fig.savefig(PLOTS / "heap.png")
    fig.savefig(PLOTS / "heap.svg")
    plt.close(fig)


def table(workload):
    lines = ["| Structure | n | Actual m | Mean ± SD (ms) | Accesses | Comparisons | Movements | Total workload complexity |",
             "|---|---:|---:|---:|---:|---:|---:|---|"]
    data = sorted((r for r in rows if r["workload"] == workload), key=lambda r: (int(r["n"]), r["structure"]))
    for r in data:
        counts = " | ".join(f"{int(r[k]):,}" for k in ("accesses", "comparisons", "movements"))
        lines.append(f"| {r['structure']} | {int(r['n']):,} | {int(r['m']):,} | {float(r['mean_ms']):.6f} ± {float(r['stddev_ms']):.6f} | {counts} | {r['theory']} |")
    return "\n".join(lines)


def render_report():
    report = (ROOT / "report" / "README.template.md").read_text(encoding="utf-8")
    for workload in {r["workload"] for r in rows}:
        report = report.replace("{{TABLE:" + workload + "}}", table(workload))
    for r in rows:
        key = r["workload"] + ":" + r["structure"] + ":" + r["n"]
        for metric in ("mean_ms", "comparisons", "accesses", "movements"):
            value = f"{float(r[metric]):.6f}" if metric == "mean_ms" else f"{int(r[metric]):,}"
            report = report.replace("{{" + key + ":" + metric + "}}", value)
    report = report.replace("{{ENVIRONMENT}}", (ROOT / "results" / "environment.txt").read_text(encoding="utf-8").strip())
    assert "{{" not in report, "Unresolved report placeholder"
    (ROOT / "README.md").write_text(report, encoding="utf-8")
    print("Rendered README.md and three figures (PNG + SVG).")


if __name__ == "__main__":
    validate()
    plot()
    render_report()
