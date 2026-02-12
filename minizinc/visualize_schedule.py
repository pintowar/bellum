# /// script
# dependencies = [
#   "matplotlib",
#   "tk",
# ]
# ///

import sys
import json
import matplotlib
import matplotlib.pyplot as plt
import argparse
import random

def parse_arguments():
    parser = argparse.ArgumentParser(description='Visualize Minizinc Schedule')
    parser.add_argument('--save', type=str, help='Filename to save the plot (e.g., schedule.png)')
    return parser.parse_args()

def random_color():
    return "#" + ''.join([random.choice('0123456789ABCDEF') for j in range(6)])

def main():
    args = parse_arguments()

    try:
        # Read all stdin
        input_data = sys.stdin.read()
        
        # Split by Minizinc separator
        parts = input_data.split('----------')
        
        # Find the last valid JSON part
        data = None
        for part in reversed(parts):
            part = part.strip()
            if not part:
                continue
            # Ignore final status messages like ==========
            if part.startswith('='):
                continue
                
            try:
                data = json.loads(part)
                break
            except json.JSONDecodeError:
                continue
        
        if data is None:
            print("Error: No valid JSON found in input.")
            return

    except Exception as e:
        print(f"Error reading input: {e}")
        return

    # Extract data
    max_time = data.get('C_max', 0)
    priority_cost = data.get('c_p', 0)
    assignments = data.get('a', [])
    start_times = data.get('s', [])
    durations = data.get('dur', [])
    task_priorities = data.get('task_priority', [])
    
    if not assignments or not start_times or not durations:
        print("Error: Missing required data fields (a, s, dur) in JSON.")
        return

    # Prepare data for plotting
    tasks = []
    employees = sorted(list(set(assignments)))
    
    # Color mapping based on priority
    # 0 -> Red, 1 -> Blue, 2 -> Green
    priority_colors = {
        0: 'red',
        1: 'blue',
        2: 'green'
    }
    
    # Precedence data
    precedence = data.get('P_out', [])
    
    # Map each task to its predecessors
    task_predecessors = {}
    for pred, succ in precedence:
        if succ not in task_predecessors:
            task_predecessors[succ] = []
        task_predecessors[succ].append(pred)

    fig, ax = plt.subplots(figsize=(12, 6))

    # Pre-calculate coordinates for all tasks to draw lines
    task_coords = {}
    for i, (emp, start, dur) in enumerate(zip(assignments, start_times, durations)):
        task_coords[i] = (start, start + dur, emp)

    for i, (emp, start, dur) in enumerate(zip(assignments, start_times, durations)):
        # Determine color based on priority if available
        color = 'gray' # Default
        if i < len(task_priorities):
            p = task_priorities[i]
            color = priority_colors.get(p, 'gray')
        
        # Task i assigned to employee emp, starting at start with duration dur
        # Bar chart: (start, duration)
        ax.barh(emp, dur, left=start, height=0.5, align='center', color=color, edgecolor='black', alpha=0.8)
        
        # Determine Label
        label = f'T{i}'
        preds = task_predecessors.get(i, [])
        if preds:
            pred_str = ",".join([f"T{p}" for p in preds])
            label = f"{pred_str} -> {label}"
            
            # Draw lines from predecessors
            for p_idx in preds:
                if p_idx in task_coords:
                    p_start, p_end, p_emp = task_coords[p_idx]
                    # Draw arrow from end of predecessor to start of current task
                    ax.annotate("",
                                xy=(start, emp), xycoords='data',
                                xytext=(p_end, p_emp), textcoords='data',
                                arrowprops=dict(arrowstyle="->", color="purple", lw=1.5))

        # Add text label
        ax.text(start + dur/2, emp, label, ha='center', va='center', color='white', fontweight='bold', fontsize=8)

    # Formatting the plot
    ax.set_yticks(employees)
    ax.set_yticklabels([f'Employee {e}' for e in employees])
    ax.set_xlabel('Time')
    ax.set_ylabel('Employees')
    ax.set_title(f'Schedule Visualization (Max Time: {max_time}, Priority Cost: {priority_cost})')
    ax.grid(True, axis='x', linestyle='--', alpha=0.7)

    plt.tight_layout()

    if args.save:
        plt.savefig(args.save)
        print(f"Plot saved to {args.save}")
    else:
        # matplotlib.use('TkAgg') 
        plt.show()

if __name__ == "__main__":
    main()
