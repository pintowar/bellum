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

Handle complex constraints like:

- Task dependencies;
- Skill Requirements;
- Time conflicts.
