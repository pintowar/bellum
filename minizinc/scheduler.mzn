% ==========================================================
% Project Scheduling Constraint Programming Model
% ==========================================================

include "globals.mzn";

% ----------------------------------------------------------
% 1. Indices and Parameters
% ----------------------------------------------------------

% Number of tasks (n) and employees (m)
int: n;
int: m;

% Sets T and E (0-indexed as per spec)
set of int: T = 0..n-1;
set of int: E = 0..m-1;

% Parameters
array[E, T] of int: d;     % Duration matrix (Employee x Task)
array[T] of int: p;        % Priority values (lower = higher priority)
array[int, 1..2] of int: P; % Precedence constraints (Set P)

% Upper bound M calculation (as per spec)
% M = min_e sum_t d_et (Time for the fastest single employee to do everything)
int: M = min([sum(t in T)(d[e,t]) | e in E]);

% Maximum task duration D_max
int: D_max = max([d[e,t] | e in E, t in T]);

% ----------------------------------------------------------
% 2. Decision Variables
% ----------------------------------------------------------

array[T] of var E: a;                % Employee assigned to task t
array[T] of var 0..M: s;             % Start time of task t
array[T] of var 0..D_max: dur;       % Actual duration of task t
array[E] of var 0..M: w;             % Total workload of employee e
var 0..M: C_max;                     % Makespan
var 0..n*n: c_p;                     % Priority inversion cost

% Derived Variable: End times
% Included for convenience in constraints
array[T] of var 0..M: end_t = [s[t] + dur[t] | t in T];

% ----------------------------------------------------------
% 3. Constraints
% ----------------------------------------------------------

% Constraint 1: Task Duration Constraint
% dur_t depends on the employee assigned (Element constraint)
constraint forall(t in T) (
    dur[t] = d[a[t], t]
);

% Constraint 2: Precedence Constraints
% end_t1 <= s_t2 for all dependencies
constraint forall(i in index_set_1of2(P)) (
    let { 
        int: t1 = P[i, 1]; 
        int: t2 = P[i, 2]; 
    } in
    end_t[t1] <= s[t2]
);

% Constraint 3: No-Overlap Constraint
% If assigned to same employee, time intervals cannot overlap
constraint forall(t1, t2 in T where t1 < t2) (
    (a[t1] == a[t2]) -> 
    (end_t[t1] <= s[t2] \/ end_t[t2] <= s[t1])
);

% Constraint 4: Employee Workload Constraint
constraint forall(e in E) (
    w[e] = sum(t in T) ( bool2int(a[t] == e) * dur[t] )
);

% Constraint 5: Makespan Definition
constraint C_max = max(t in T)(end_t[t]);

% Constraint 6: Priority Cost Definition
% Penalize if a lower priority task (higher p value) starts before a higher priority task
constraint c_p = sum(t1, t2 in T where t1 < t2) (
    if p[t1] > p[t2] then       % t2 is higher priority
        bool2int(s[t1] < s[t2]) % Penalty if t1 starts first
    else if p[t2] > p[t1] then  % t1 is higher priority
        bool2int(s[t2] < s[t1]) % Penalty if t2 starts first
    else 
        0 
    endif endif
);

% ----------------------------------------------------------
% 4. Objective Function
% ----------------------------------------------------------

solve minimize 100 * C_max + c_p;

% ----------------------------------------------------------
% Output
% ----------------------------------------------------------
output [
    "Makespan: \(C_max)\n",
    "Priority Cost: \(c_p)\n",
    "Assignments (Task: Employee, Start-End):\n"
] ++
[
    "T\(t): E\(a[t]) [\(s[t]) - \(end_t[t])]\n" | t in T
];
