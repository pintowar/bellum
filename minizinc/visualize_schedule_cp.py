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
    parser = create_parser("Visualize MiniZinc CP Schedule")
    args = parser.parse_args()

    data = run_minizinc("cp-scheduler.mzn", args.data, args.solver, args.parallel)

    assignments = list(data.get("a", []))
    start_times = list(data.get("s", []))
    durations = list(data.get("dur", []))
    priorities = list(data.get("task_priority", []))
    precedence_raw = data.get("P_out", [])
    precedence = (
        [(pair[0], pair[1]) for pair in precedence_raw] if precedence_raw else []
    )

    if not assignments or not start_times or not durations:
        print("Error: Missing required data fields (a, s, dur).")
        return

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
        title_prefix="CP Schedule",
    )

    save_or_show(fig, args.save)


if __name__ == "__main__":
    main()
