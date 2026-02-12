minizinc --output-mode json --solver org.choco.choco -p 15 -t 3000 scheduler.mzn sample04.dzn | uv run visualize_schedule.py
