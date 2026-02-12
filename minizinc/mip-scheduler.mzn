% MIP-Adapted Scheduling Model
% Optimized for Mixed Integer Programming solvers (HiGHS, CBC, Gurobi, CPLEX)

% ----------------------------------------------------------
% 1. Indices and Parameters
% ----------------------------------------------------------
int: n;
int: m;

set of int: T = 0..n-1;
set of int: E = 0..m-1;

% Coerce data to 0-based indexing
array[int, int] of int: d_raw; 
array[E, T] of int: d = array2d(E, T, d_raw);

array[int] of int: p_raw;
array[T] of int: p = array1d(T, p_raw);

array[int, 1..2] of int: P_raw;
array[int, 1..2] of int: P = P_raw;

% Precomputed per-task minimum duration across all employees
array[T] of int: min_dur = array1d(T, [min(e in E)(d[e,t]) | t in T]);

% Tighter upper bound: minimum total work across employees
int: M = min([sum(t in T)(d[e,t]) | e in E]);
int: D_max = max([d[e,t] | e in E, t in T]);
int: BigM = M + D_max;  % Big-M constant for linearization

% ----------------------------------------------------------
% 2. Decision Variables
% ----------------------------------------------------------

% Binary assignment: x[e,t] = 1 if task t assigned to employee e
array[E, T] of var 0..1: x;

% Start times
array[T] of var 0..M: s;

% Duration (determined by assignment)
array[T] of var 0..D_max: dur;

% Employee workload
array[E] of var 0..M: w;

% Makespan
var 0..M: C_max;

% End times
array[T] of var 0..(M + D_max): end_t;

% Priority cost
var 0..(n*n): c_p;

% Binary variables for ordering on same employee: y[e,t1,t2] = 1 if t1 before t2
array[E, T, T] of var 0..1: y;

% Binary variables for start time ordering: z[t1,t2] = 1 if s[t1] <= s[t2]
array[T, T] of var 0..1: z;

% ----------------------------------------------------------
% 3. Constraints
% ----------------------------------------------------------

% Constraint 1: Each task assigned to exactly one employee
constraint forall(t in T) (
    sum(e in E) (x[e, t]) = 1
);

% Constraint 2: Task duration based on assignment
constraint forall(t in T) (
    dur[t] = sum(e in E) (x[e, t] * d[e, t])
);

% Constraint 3: End time definition
constraint forall(t in T) (
    end_t[t] = s[t] + dur[t]
);

% Constraint 4: Precedence constraints
constraint forall(i in index_set_1of2(P)) (
    end_t[P[i, 1]] <= s[P[i, 2]]
);

% Constraint 5: No-overlap using big-M formulation
% For each employee and each pair of tasks, enforce disjunction if both assigned
constraint forall(e in E, t1 in T, t2 in T where t1 < t2) (
    % y[e,t1,t2] can only be 1 if both tasks assigned to e
    y[e, t1, t2] <= x[e, t1] /\
    y[e, t1, t2] <= x[e, t2]
);

% If both tasks on same employee, exactly one ordering must hold
constraint forall(e in E, t1 in T, t2 in T where t1 < t2) (
    x[e, t1] + x[e, t2] - 1 <= y[e, t1, t2] + y[e, t2, t1]
);

% Disjunctive constraints using big-M
constraint forall(e in E, t1 in T, t2 in T where t1 < t2) (
    end_t[t1] <= s[t2] + BigM * (1 - y[e, t1, t2]) /\
    end_t[t2] <= s[t1] + BigM * (1 - y[e, t2, t1])
);

% Constraint 6: Employee workload
constraint forall(e in E) (
    w[e] = sum(t in T) (x[e, t] * d[e, t])
);

% Constraint 7: Makespan
constraint forall(t in T) (
    C_max >= end_t[t]
);

% Constraint 8: Lower bounds on makespan
constraint C_max >= sum(t in T)(min_dur[t]) div m;
constraint C_max >= max(t in T)(min_dur[t]);

% Constraint 9: Linearized start time ordering variables
constraint forall(t1 in T, t2 in T where t1 != t2) (
    s[t1] <= s[t2] + BigM * (1 - z[t1, t2]) /\
    s[t2] <= s[t1] + BigM * z[t1, t2]
);

constraint forall(t in T) (
    z[t, t] = 0
);

% Constraint 10: Priority cost - count priority inversions linearly
constraint c_p = sum(t1 in T, t2 in T where t1 < t2) (
    if p[t1] > p[t2] then
        % p[t1] > p[t2], t1 should be first - cost if s[t1] > s[t2], i.e., z[t2,t1] = 1
        z[t2, t1]
    elseif p[t2] > p[t1] then
        % p[t2] > p[t1], t2 should be first - cost if s[t2] > s[t1], i.e., z[t1,t2] = 1  
        z[t1, t2]
    else
        0
    endif
);

% ----------------------------------------------------------
% 4. Objective and Output
% ----------------------------------------------------------
solve minimize 100 * C_max + c_p;

output [
    "Makespan: ", show(C_max), "\n",
    "Priority Cost: ", show(c_p), "\n"
];
