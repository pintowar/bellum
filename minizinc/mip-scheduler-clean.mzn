% Reduced variables, tightened bounds, symmetry breaking

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

% Precomputed bounds
array[T] of int: min_dur = array1d(T, [min(e in E)(d[e,t]) | t in T]);
array[T] of int: max_dur = array1d(T, [max(e in E)(d[e,t]) | t in T]);
int: D_max = max(max_dur);
int: LB_C_max = max(ceil(sum(t in T)(min_dur[t]) / m), max(t in T)(min_dur[t]));
int: UB_C_max = max(e in E)(sum(t in T)(max_dur[t]));

% Tight Big-M values per task pair
array[T, T] of int: BigM_sep = array2d(T, T, [UB_C_max + max_dur[t1] | t1 in T, t2 in T]);

% Identify precedence-constrained tasks
array[T] of bool: has_pred = array1d(T, [exists(i in index_set_1of2(P))(P[i,2] == t) | t in T]);
array[T] of bool: has_succ = array1d(T, [exists(i in index_set_1of2(P))(P[i,1] == t) | t in T]);

% Pairs with different priorities (for optimization)
set of int: PriorityPairs = {i | i in 1..(n*n)};  % Will filter in constraints

% ----------------------------------------------------------
% 2. Decision Variables
% ----------------------------------------------------------

% Binary assignment
array[E, T] of var 0..1: x;

% Start times with tight bounds
array[T] of var 0..UB_C_max: s;

% Duration as expression
array[T] of var int: dur;

% Makespan with tight bounds
var LB_C_max..UB_C_max: C_max;

% Priority inversion indicator for each comparable pair
array[T, T] of var 0..1: is_inv;

% Binary variables for ordering on same employee: y[e,t1,t2] = 1 if t1 before t2
array[E, T, T] of var 0..1: y;

% Priority cost
var 0..(n*n): c_p = sum(t1 in T, t2 in T where t1 < t2)(is_inv[t1,t2]);

% ----------------------------------------------------------
% 3. Core Constraints
% ----------------------------------------------------------

% Assignment
constraint forall(t in T)(sum(e in E)(x[e,t]) == 1);

% Duration channeling
constraint forall(t in T)(dur[t] == sum(e in E)(x[e,t] * d[e,t]));

% Precedence (direct, no intermediate end variables)
constraint forall(i in index_set_1of2(P))(
    s[P[i,1]] + sum(e in E)(x[e,P[i,1]] * d[e,P[i,1]]) <= s[P[i,2]]
);

% ----------------------------------------------------------
% 4. No-Overlap: Optimized Formulation
% ----------------------------------------------------------

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

% Disjunctive constraints using Big-M
constraint forall(e in E, t1 in T, t2 in T where t1 < t2) (
    s[t1] + sum(ee in E)(x[ee,t1] * d[ee,t1]) <= s[t2] + BigM_sep[t1,t2] * (1 - y[e, t1, t2]) /\
    s[t2] + sum(ee in E)(x[ee,t2] * d[ee,t2]) <= s[t1] + BigM_sep[t2,t1] * (1 - y[e, t2, t1])
);

% ----------------------------------------------------------
% 5. Makespan
% ----------------------------------------------------------

constraint forall(t in T)(C_max >= s[t] + dur[t]);

% ----------------------------------------------------------
% 6. Priority Cost (Fully Linear)
% ----------------------------------------------------------

% For each pair, define inversion based on priority comparison
constraint forall(t1 in T, t2 in T where t1 < t2)(
    if p[t1] > p[t2] then
        % t1 has higher priority - should start at or before t2
        % No inversion if s[t1] <= s[t2]
        s[t1] <= s[t2] + BigM_sep[t1,t2] * is_inv[t1,t2] /\
        s[t1] >= s[t2] + 1 - BigM_sep[t1,t2] * (1 - is_inv[t1,t2])
    elseif p[t2] > p[t1] then
        % t2 has higher priority - should start at or before t1
        % No inversion if s[t2] <= s[t1]
        s[t2] <= s[t1] + BigM_sep[t2,t1] * is_inv[t1,t2] /\
        s[t2] >= s[t1] + 1 - BigM_sep[t2,t1] * (1 - is_inv[t1,t2])
    else
        is_inv[t1,t2] == 0
    endif
);

% ----------------------------------------------------------
% 7. Symmetry Breaking
% ----------------------------------------------------------

% Break employee symmetry: assign first task to employee 0
% constraint x[0, min(T)] == 1;

% Break start-time symmetry: order tasks with same duration and priority
% constraint forall(t1 in T, t2 in T where t1 < t2)(
%     if p[t1] == p[t2] /\ min_dur[t1] == min_dur[t2] /\ max_dur[t1] == max_dur[t2] /\
%        not has_pred[t1] /\ not has_pred[t2] /\ not has_succ[t1] /\ not has_succ[t2] then
%         s[t1] <= s[t2]
%     else
%         true
%     endif
% );

% Break start-time symmetry: order tasks with identical durations and priority
constraint forall(t1, t2 in T where t1 < t2) (
    if p[t1] == p[t2] /\ 
       forall(e in E)(d[e, t1] == d[e, t2]) /\ % Tasks are fully identical
       not has_pred[t1] /\ not has_pred[t2] /\ 
       not has_succ[t1] /\ not has_succ[t2] then
        s[t1] <= s[t2]
    else
        true
    endif
);

% ----------------------------------------------------------
% 8. Objective
% ----------------------------------------------------------

solve minimize 100 * C_max + c_p;

output [
    "Makespan: ", show(C_max), "\n",
    "Priority Cost: ", show(c_p), "\n"
];
