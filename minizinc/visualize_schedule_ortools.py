# /// script
# dependencies = [
#   "matplotlib",
#   "minizinc",
#   "ortools",
# ]
# ///

import sys
import json
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))

from common import create_parser, plot_schedule, save_or_show
from ortools.sat.python import cp_model


def solve_ortools(data_path: str, time_limit_sec: int = 30, parallel: int | None = None) -> dict:
    with open(data_path, "r") as f:
        data = json.load(f)

    tasks = data["tasks"]
    estimation_matrix = data["estimationMatrix"]

    m = len(estimation_matrix)
    n = len(estimation_matrix[0])

    # Parse priority
    priority_map = {"critical": 0, "major": 1, "minor": 2}
    p = [priority_map.get(t["priority"], 2) for t in tasks]

    # Parse precedences
    P_raw = []
    for i, t in enumerate(tasks):
        if t["precedes"] != -1:
            # t["precedes"] is 1-indexed, meaning it's an ID.
            # MiniZinc model used:
            # array[int] of int: pred_list = [
            #     if j == 1 then t.id - 1 else t.precedes - 1 endif
            #     | t in tasks, j in 1..2 where t.precedes != -1
            # ];
            # So the pair is [t["id"] - 1, t["precedes"] - 1].
            # Wait, MiniZinc array says:
            # constraint forall(i in index_set_1of2(P)) (
            #     end_t[P[i, 1]] <= s[P[i, 2]]
            # );
            # P[i, 1] is t["id"] - 1, P[i, 2] is t["precedes"] - 1.
            # So t["id"] ends BEFORE t["precedes"] starts.
            # Meaning t["id"] PRECEDES t["precedes"].
            # "id: 2, precedes: 5" -> 2 precedes 5.
            P_raw.append([t["id"] - 1, t["precedes"] - 1])

    # Precomputed max values
    M = sum(max(estimation_matrix[e][t] for e in range(m)) for t in range(n))
    D_max = max(max(estimation_matrix[e][t] for e in range(m)) for t in range(n))

    model = cp_model.CpModel()

    # Decision variables
    a = [model.NewIntVar(0, m - 1, f"a_{t}") for t in range(n)]
    s = [model.NewIntVar(0, M, f"s_{t}") for t in range(n)]
    dur = [model.NewIntVar(0, D_max, f"dur_{t}") for t in range(n)]
    end_t = [model.NewIntVar(0, M, f"end_t_{t}") for t in range(n)]
    
    C_max = model.NewIntVar(0, M, "C_max")
    c_p = model.NewIntVar(0, n * n, "c_p")

    # a_bool[e][t] == 1 iff a[t] == e
    a_bool = {}
    for e in range(m):
        for t in range(n):
            a_bool[(e, t)] = model.NewBoolVar(f"a_bool_{e}_{t}")
            model.Add(a[t] == e).OnlyEnforceIf(a_bool[(e, t)])
            model.Add(a[t] != e).OnlyEnforceIf(a_bool[(e, t)].Not())

    for t in range(n):
        # end_t[t] = s[t] + dur[t]
        model.Add(end_t[t] == s[t] + dur[t])
        
        # Exactly one employee assigned to task t
        model.AddExactlyOne(a_bool[(e, t)] for e in range(m))

        # dur[t] = d[a[t], t]
        for e in range(m):
            model.Add(dur[t] == estimation_matrix[e][t]).OnlyEnforceIf(a_bool[(e, t)])

    # Precedence
    for pred, succ in P_raw:
        model.Add(end_t[pred] <= s[succ])

    # No Overlap
    for e in range(m):
        intervals = []
        for t in range(n):
            interval = model.NewOptionalIntervalVar(
                s[t], dur[t], end_t[t], a_bool[(e, t)], f"interval_{e}_{t}"
            )
            intervals.append(interval)
        model.AddNoOverlap(intervals)

    # Makespan
    model.AddMaxEquality(C_max, end_t)

    # Priority Cost
    penalty_vars = []
    for t1 in range(n):
        for t2 in range(t1 + 1, n):
            if p[t1] > p[t2]:
                low_p_task, high_p_task = t1, t2
            elif p[t2] > p[t1]:
                low_p_task, high_p_task = t2, t1
            else:
                continue
            
            b = model.NewBoolVar(f"p_penalty_{t1}_{t2}")
            model.Add(s[low_p_task] < s[high_p_task]).OnlyEnforceIf(b)
            model.Add(s[low_p_task] >= s[high_p_task]).OnlyEnforceIf(b.Not())
            penalty_vars.append(b)
            
    model.Add(c_p == sum(penalty_vars))

    # Objective
    model.Minimize(100 * C_max + c_p)

    solver = cp_model.CpSolver()
    if parallel:
        solver.parameters.num_search_workers = parallel
    solver.parameters.max_time_in_seconds = time_limit_sec
    
    status = solver.Solve(model)

    if status == cp_model.OPTIMAL or status == cp_model.FEASIBLE:
        return {
            "a": [solver.Value(a[t]) for t in range(n)],
            "s": [solver.Value(s[t]) for t in range(n)],
            "dur": [solver.Value(dur[t]) for t in range(n)],
            "task_priority": p,
            "P_out": P_raw,
            "C_max": solver.Value(C_max),
            "c_p": solver.Value(c_p),
        }
    else:
        raise RuntimeError("No solution found")


def main():
    parser = create_parser("Visualize OR-Tools CP Schedule")
    args = parser.parse_args()

    data = solve_ortools(args.data, time_limit_sec=10*60, parallel=args.parallel)

    assigns = list(data.get("a", []))
    start_times = list(data.get("s", []))
    durations = list(data.get("dur", []))
    priorities = list(data.get("task_priority", []))
    precedence_raw = data.get("P_out", [])
    precedence = (
        [(pair[0], pair[1]) for pair in precedence_raw] if precedence_raw else []
    )

    if not assigns or not start_times or not durations:
        print("Error: Missing required data fields (a, s, dur).")
        return

    max_time = data.get("C_max", 0)
    priority_cost = data.get("c_p", 0)

    fig = plot_schedule(
        assignments=assigns,
        start_times=start_times,
        durations=durations,
        priorities=priorities,
        precedence=precedence,
        max_time=max_time,
        priority_cost=priority_cost,
        title_prefix="OR-Tools CP Schedule",
    )

    save_or_show(fig, args.save)


if __name__ == "__main__":
    main()
