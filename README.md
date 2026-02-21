# Bellum - Resource Task Scheduling System

| Service       | Master                                                                                                                                                            | Develop                                                                                                                                                                                         |
|---------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| CI Status     | ![master status](https://github.com/pintowar/bellum/actions/workflows/master.yml/badge.svg?branch=master)                                                         | ![develop status](https://github.com/pintowar/bellum/actions/workflows/develop.yml/badge.svg?branch=develop)                                                                                    |
| Test Coverage | [![Sonar Coverage](https://sonarcloud.io/api/project_badges/measure?project=pintowar_bellum&metric=coverage)](https://sonarcloud.io/dashboard?id=pintowar_bellum) | [![Sonar Coverage](https://sonarcloud.io/api/project_badges/measure?project=pintowar_bellum&metric=coverage&branch=develop)](https://sonarcloud.io/dashboard?id=pintowar_bellum&branch=develop) |

![GitHub release (latest)](https://img.shields.io/github/v/release/pintowar/bellum?logo=github)
![GitHub license](https://img.shields.io/github/license/pintowar/bellum)

## Description

It is a Kotlin-based scheduling system that optimizes task assignment to employees.
It uses constraint programming solvers for optimal scheduling.

## Features

- **Constraint-based Scheduling**: Uses constraint programming to find feasible and optimal schedules
- **Skill Matching**: Tasks require specific skill levels; employees have skill profiles
- **Task Dependencies**: Support for task precedence constraints
- **Priority Levels**: Tasks can be CRITICAL, MAJOR, or MINOR priority
- **Parallel Solving**: Multi-threaded solver for faster optimization
- **Multiple Output Formats**: Console output with optional HTML dashboard

## Modules

- **bellum-core**: Core domain models, solver abstractions, estimators, and file parsers;
- **bellum-solver**
  - **choco**: ChocoSolver implementation using constraint programming;
- **bellum-cli**: Command-line interface for running the scheduler.

## Supported Solvers

Currently supported:

- **choco**: Choco Solver - Constraint programming solver (default)

Run `solvers` command to list all available solvers.

## Input Format (RTS)

Bellum uses a custom RTS (Resource Task Scheduling) format with CSV-like structure:

```
id,content,skill1,skill2,skill3,...
1,Employee Name,5,3,0,...
================================================================================
id,content,priority,precedes,skill1,skill2,skill3,...
1,Task Description,major,-1,3,2,0,...
2,Another Task,minor,1,1,1,2,...
================================================================================
,1,2
1,10,20
2,30,40
```

### Employee Section (above first separator)

| Column | Description |
|--------|-------------|
| `id` | Unique employee identifier |
| `content` | Employee name |
| `skillN` | Skill level for skill N (0-5) |

### Task Section (between separators)

| Column | Description |
|--------|-------------|
| `id` | Unique task identifier |
| `content` | Task description |
| `priority` | Task priority: `critical`, `major`, or `minor` |
| `precedes` | ID of task that must complete first (`-1` for none) |
| `skillN` | Required skill level for skill N |

### Estimation Matrix (below second separator - optional)

When present, this section overrides the default Pearson correlation estimator and provides custom duration estimates (in minutes) for each employee-task pair.

```
,1,2,3
emp_id1,10,20,30
emp_id2,40,50,60
```

| Column | Description |
|--------|-------------|
| (first column) | Employee ID |
| (remaining columns) | Task ID headers |
| (data rows) | Duration in minutes for each employee-task combination |

If this section is omitted, the solver uses the default Pearson correlation estimator based on skill matching.

## CLI Usage

### List Available Solvers

```bash
./bellum solvers
```

### Solve a Scheduling Problem

```bash
./bellum solve [OPTIONS] PATH
```

### Examples

```bash
# Basic solve with default settings
./bellum solve project.rts

# Solve with 60 second time limit (default 30)
./bellum solve -l 60 project.rts

# Solve and generate HTML dashboard
./bellum solve -o ./output project.rts

# Use Choco as solver (default choco)
./bellum solve -s choco project.rts

# Use 4 threads for parallel solving
./bellum solve -p 4 project.rts
```

### Output Example

```
[1.2s       ]: Sample Project - 4h 30m | valid, scheduled, optimal
```

The output shows:
- **Duration**: Time taken to find the solution
- **Project name**: From the input file
- **Total duration**: Sum of all task durations
- **Status**: valid/invalid, scheduled/partial/none, optimal/suboptimal
