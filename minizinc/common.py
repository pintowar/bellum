# /// script
# dependencies = [
#   "matplotlib",
#   "minizinc",
# ]
# ///

import argparse
from pathlib import Path
from typing import Any

import matplotlib.pyplot as plt
from minizinc import Instance, Model, Solver


def create_parser(description: str) -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=description)
    parser.add_argument(
        "--save", type=str, help="Filename to save the plot (e.g., schedule.png)"
    )
    parser.add_argument(
        "--solver", type=str, required=True, default="gecode", help="MiniZinc solver (default: gecode)"
    )
    parser.add_argument("--data", type=str, required=True, help="Path to data file (.dzn)")
    return parser


def run_minizinc(
    model_path: str, data_path: str | None = None, solver_name: str = "gecode"
) -> dict[str, Any]:
    model = Model(Path(model_path))
    solver = Solver.lookup(solver_name)
    instance = Instance(solver, model)

    if data_path:
        instance.add_file(Path(data_path))

    result = instance.solve()
    if result.solution is None:
        raise RuntimeError("No solution found")

    return {k: v for k, v in result.solution.__dict__.items() if not k.startswith("_")}


PRIORITY_COLORS = {0: "red", 1: "blue", 2: "green"}


def build_task_predecessors(precedence: list[tuple[int, int]]) -> dict[int, list[int]]:
    task_predecessors: dict[int, list[int]] = {}
    for pred, succ in precedence:
        if succ not in task_predecessors:
            task_predecessors[succ] = []
        task_predecessors[succ].append(pred)
    return task_predecessors


def plot_schedule(
    assignments: list[int],
    start_times: list[int],
    durations: list[int],
    priorities: list[int],
    precedence: list[tuple[int, int]],
    max_time: int,
    priority_cost: int,
    title_prefix: str = "Schedule",
) -> plt.Figure:
    employees = sorted(set(assignments))
    task_predecessors = build_task_predecessors(precedence)

    fig, ax = plt.subplots(figsize=(14, 7))

    task_coords = {}
    for i, (emp, start, dur) in enumerate(zip(assignments, start_times, durations)):
        task_coords[i] = (start, start + dur, emp)

    for i, (emp, start, dur) in enumerate(zip(assignments, start_times, durations)):
        priority = priorities[i] if i < len(priorities) else None
        color = (
            PRIORITY_COLORS.get(priority, "gray") if priority is not None else "gray"
        )

        ax.barh(
            emp,
            dur,
            left=start,
            height=0.5,
            align="center",
            color=color,
            edgecolor="black",
            alpha=0.8,
        )

        label = f"T{i}"
        preds = task_predecessors.get(i, [])
        if preds:
            pred_str = ",".join([f"T{p}" for p in preds])
            label = f"{pred_str} -> {label}"

            for p_idx in preds:
                if p_idx in task_coords:
                    p_start, p_end, p_emp = task_coords[p_idx]
                    ax.annotate(
                        "",
                        xy=(start, emp),
                        xycoords="data",
                        xytext=(p_end, p_emp),
                        textcoords="data",
                        arrowprops=dict(arrowstyle="->", color="purple", lw=1.5),
                    )

        ax.text(
            start + dur / 2,
            emp,
            label,
            ha="center",
            va="center",
            color="white",
            fontweight="bold",
            fontsize=8,
        )

    ax.set_yticks(employees)
    ax.set_yticklabels([f"Employee {e}" for e in employees])
    ax.set_xlabel("Time")
    ax.set_ylabel("Employees")
    ax.set_title(
        f"{title_prefix} Visualization (Makespan: {max_time}, Priority Cost: {priority_cost})"
    )
    ax.grid(True, axis="x", linestyle="--", alpha=0.7)

    return fig


def save_or_show(fig: plt.Figure, save_path: str | None) -> None:
    plt.tight_layout()
    if save_path:
        fig.savefig(save_path)
        print(f"Plot saved to {save_path}")
    else:
        plt.show()
