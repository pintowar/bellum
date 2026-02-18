include "globals.mzn";

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

% ----------------------------------------------------------
% 2. Decision Variables
% ----------------------------------------------------------
array[T] of var E: a;                
array[T] of var 0..M: s;             
array[T] of var 0..D_max: dur;       
array[E] of var 0..M: w;             
var 0..M: C_max;                     
var 0..n*n: c_p;                     

% Coerce comprehension to T (0..n-1)
array[T] of var 0..M: end_t = array1d(T, [s[t] + dur[t] | t in T]);
array[T] of var int: task_priority; 
constraint forall(t in T)(task_priority[t] = p[t]); % Expose priority for JSON output

% EXPOSE P_raw for visualization
array[index_set_1of2(P_raw), 1..2] of var int: P_out;
constraint forall(i in index_set_1of2(P_raw), j in 1..2)(P_out[i,j] = P_raw[i,j]);

% ----------------------------------------------------------
% Symmetry Break Helper
% ----------------------------------------------------------
array[T] of bool: is_constrained = array1d(T, [
    exists(i in index_set_1of2(P)) (
        P[i, 1] == t \/ P[i, 2] == t
    ) 
    | t in T
]);

% ----------------------------------------------------------
% 3. Constraints
% ----------------------------------------------------------

% Constraint 1: Task Duration 
constraint forall(t in T) (
    dur[t] = d[a[t], t]
);

% Constraint 2: Precedence
constraint forall(i in index_set_1of2(P)) (
    end_t[P[i, 1]] <= s[P[i, 2]]
);

% Constraint 3: No-Overlap using diffn
% Each task is a rectangle: x=start_time, y=employee, dx=duration, dy=1
constraint diffn(
    array1d(T, [s[t] | t in T]),     % x positions (start times)
    array1d(T, [a[t] | t in T]),     % y positions (employee assignment)
    array1d(T, [dur[t] | t in T]),   % x sizes (durations)
    array1d(T, [1 | t in T])         % y sizes (each task occupies 1 employee)
);

% Constraint 3b: Redundant per-employee cumulative
constraint forall(e in E) (
    cumulative(s, dur, array1d(T, [bool2int(a[t] == e) | t in T]), 1)
);

% Constraint 4: Employee Workload
constraint forall(e in E) (
    w[e] = sum(t in T) ( bool2int(a[t] == e) * dur[t] )
);

% Constraint 5: Makespan
constraint C_max = max(t in T)(end_t[t]);

% Redundant Constraint: Lower Bound on C_max
constraint C_max >= sum(t in T)(min_dur[t]) div m;
constraint C_max >= max(t in T)(min_dur[t]);

% Constraint 6: Priority Cost
constraint c_p = sum(t1, t2 in T where t1 < t2) (
    if p[t1] > p[t2] then       
        bool2int(s[t1] < s[t2]) 
    else if p[t2] > p[t1] then  
        bool2int(s[t2] < s[t1]) 
    else 
        0 
    endif endif
);

% Constraint 7: Symmetry Breaking
% For tasks with same priority, assigned to same employee, and NOT involved in precedence:
% Force an arbitrary order (based on task index) to reduce search space.
constraint forall(t1, t2 in T where t1 < t2) (
    if (p[t1] == p[t2] /\ not is_constrained[t1] /\ not is_constrained[t2] /\
        forall(e in E)(d[e, t1] == d[e, t2])) then
        (a[t1] == a[t2]) -> (s[t1] <= s[t2])
    else
        true
    endif
);

% ----------------------------------------------------------
% 4. Objective and Output
% ----------------------------------------------------------
solve 
:: seq_search([
    int_search(a, first_fail, indomain_min),
    int_search(s, smallest, indomain_min)
])
minimize 100 * C_max + c_p;

output [
    "Makespan: \(C_max)\n",
    "Priority Cost: \(c_p)\n"
] % ++
% [
%     "Assignments:\n",
%     "T\(t) (p:\(p[t])): E\(a[t]) [\(s[t]) - \(end_t[t])]\n" | t in T
% ];