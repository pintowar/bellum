# /// script
# dependencies = [
#   "matplotlib",
#   "tk",
# ]
# ///

import sys
import json
import matplotlib
import matplotlib.pyplot as plt
import argparse
import random


def parse_arguments():
    parser = argparse.ArgumentParser(description="Visualize MIP Schedule from MiniZinc")
    parser.add_argument(
        "--save", type=str, help="Filename to save the plot (e.g., schedule.png)"
    )
    return parser.parse_args()


def random_color():
    return "#" + "".join([random.choice("0123456789ABCDEF") for j in range(6)])


def main():
    args = parse_arguments()

    try:
        input_data = sys.stdin.read()

        # Split by MiniZinc separator and take the first JSON part
        parts = input_data.split("----------")

        data = None
        for part in parts:
            part = part.strip()
            if not part or part.startswith("="):
                continue
            try:
                data = json.loads(part)
                break
            except json.JSONDecodeError:
                continue

        if data is None:
            print("Error: No valid JSON found in input.")
            return

    except Exception as e:
        print(f"Error reading input: {e}")
        return

    max_time = data.get("C_max", 0)
    priority_cost = data.get("c_p", 0)

    x = data.get("x", [[]])
    start_times = data.get("s", [])
    durations = data.get("dur", [])

    p = data.get("p", [])
    P_raw = data.get("P_raw", [[]])

    if not x or not start_times or not durations:
        print("Error: Missing required data fields (x, s, dur) in JSON.")
        return

    n = len(start_times)
    m = len(x) if x else 0

    assignments = []
    for t in range(n):
        assigned_emp = None
        for e in range(m):
            if e < len(x) and t < len(x[e]) and x[e][t] == 1:
                assigned_emp = e
                break
        if assigned_emp is None:
            print(f"Warning: Task {t} has no assignment")
            assigned_emp = 0
        assignments.append(assigned_emp)

    priority_colors = {0: "red", 1: "blue", 2: "green"}

    precedence = []
    if P_raw and len(P_raw) > 0:
        for pair in P_raw:
            if len(pair) >= 2:
                precedence.append((pair[0], pair[1]))

    task_predecessors = {}
    for pred, succ in precedence:
        if succ not in task_predecessors:
            task_predecessors[succ] = []
        task_predecessors[succ].append(pred)

    employees = sorted(list(set(assignments)))

    fig, ax = plt.subplots(figsize=(14, 7))

    task_coords = {}
    for i, (emp, start, dur) in enumerate(zip(assignments, start_times, durations)):
        task_coords[i] = (start, start + dur, emp)

    for i, (emp, start, dur) in enumerate(zip(assignments, start_times, durations)):
        color = "gray"
        if i < len(p):
            priority = p[i]
            color = priority_colors.get(priority, "gray")

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
        f"MIP Schedule Visualization (Makespan: {max_time}, Priority Cost: {priority_cost})"
    )
    ax.grid(True, axis="x", linestyle="--", alpha=0.7)

    plt.tight_layout()

    if args.save:
        plt.savefig(args.save)
        print(f"Plot saved to {args.save}")
    else:
        plt.show()


if __name__ == "__main__":
    main()
