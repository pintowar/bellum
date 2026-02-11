!#/bin/bash

minizinc --output-mode json --solver org.choco.choco -p 15 -t 3000 scheduler.mzn sample04.dzn | python3 visualize_schedule.py
