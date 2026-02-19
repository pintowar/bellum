# /// script
# dependencies = [
#   "matplotlib",
#   "minizinc",
# ]
# ///

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))

from common import create_parser, plot_schedule, run_minizinc, save_or_show


def main():
    parser = create_parser("Visualize MiniZinc MIP Schedule")
    args = parser.parse_args()

    data = run_minizinc("mip-scheduler.mzn", args.data, args.solver, args.parallel)

    x = data.get("x", [])
    start_times = list(data.get("s", []))
    durations = list(data.get("dur", []))
    priorities = list(data.get("task_priority", []))
    precedence_raw = data.get("P_raw", [])
    precedence = (
        [(pair[0], pair[1]) for pair in precedence_raw] if precedence_raw else []
    )

    if not x or not start_times or not durations:
        print("Error: Missing required data fields (x, s, dur).")
        return

    n = len(start_times)
    m = len(x)

    assignments = []
    for t in range(n):
        assigned_emp = 0
        for e in range(m):
            if e < len(x) and t < len(x[e]) and x[e][t] == 1:
                assigned_emp = e
                break
        assignments.append(assigned_emp)

    max_time = data.get("C_max", 0)
    priority_cost = data.get("c_p", 0)

    fig = plot_schedule(
        assignments=assignments,
        start_times=start_times,
        durations=durations,
        priorities=priorities,
        precedence=precedence,
        max_time=max_time,
        priority_cost=priority_cost,
        title_prefix="MIP Schedule",
    )

    save_or_show(fig, args.save)


if __name__ == "__main__":
    main()
