import tkinter as tk
from tkinter import scrolledtext, messagebox
import subprocess
import shutil
import os
import datetime
import re
import json

# DIR and COMMAND DEFINES
CHISEL_PROJECT_DIR = "."  # Where your build.sbt is
DEFAULT_CPU_SIM_DIR = "C:/Users/prote/Desktop/DTU/02201_Agile_Hardware_Design/core/riscv"
DEFAULT_TARGET_FILE = "./RISC-V-Core/core/synthesis/predictor_top.sv"

# PUT the absolute path FOR NOW ...
SIM_COMMAND = ["C:/Xilinx/2025.1/Vivado/bin/vivado.bat", "-mode", "batch", "-source", "run_sim.tcl"]

CSV_FILE = "benchmark_results.csv"

#HELPER
def clear_benchmarks():
    with open(CSV_FILE, "w") as f:
        f.write("Config,Algorithm,Instructions Executed,Total Branches,Conditional,Jumps,Mispredicted,Mispred Rate\n")

    log_box.insert(tk.END, f"\n[{datetime.datetime.now().strftime('%H:%M:%S')}] Cleared contents of {CSV_FILE}!\n")
    log_box.see(tk.END)

def get_test_class(algorithm):
    # map the JSON algorithm name to the ChiselTest class name
    test_map = {
        "Bimodal": "BimodalTest",
        "GAg": "GAgSpec",
        "PAg": "PAgSpec"
    }
    return test_map.get(algorithm, None)

def extract_metric(pattern, text):
    # Uses Regex to find numbers in the Vivado output
    match = re.search(pattern, text)
    return match.group(1) if match else "0"

# MAIN STUFF
def run_pipeline():
    config_name = config_entry.get().strip()
    cpu_sim_dir = sim_dir_entry.get().strip()
    target_file = target_file_entry.get().strip()

    if not config_name:
        messagebox.showerror("Error", "Please enter a config name (e.g., test3)")
        return

    log_box.insert(tk.END, f"\n[{datetime.datetime.now().strftime('%H:%M:%S')}] Starting Pipeline for '{config_name}'...\n")
    log_box.see(tk.END)
    window.update()

    try:
        # STEP 1: Parse JSON to find the algorithm and run the specific Chisel Test
        log_box.insert(tk.END, "--> 1. Running Pre-Simulation Chisel Tests...\n")
        window.update()

        with open(os.path.join(CHISEL_PROJECT_DIR, "config.json"), "r") as f:
            config_data = json.load(f)

        if config_name not in config_data:
            raise Exception(f"Config '{config_name}' not found in config.json!")

        algorithm = config_data[config_name].get("algorithm", "")
        test_class = get_test_class(algorithm)

        if test_class:
            log_box.insert(tk.END, f"       Detected Algorithm: {algorithm}. Running test: {test_class}\n")
            test_cmd = ["sbt", f"testOnly {test_class}"]
            subprocess.run(test_cmd, cwd=CHISEL_PROJECT_DIR, check=True, capture_output=True, text=True, shell=True)
            log_box.insert(tk.END, "       Chisel Test PASSED! Hardware is valid.\n")
        else:
            log_box.insert(tk.END, f"       Warning: No automated test mapped for algorithm '{algorithm}'. Skipping test.\n")

        # STEP 2: Run SBT to Generate the Hardware
        log_box.insert(tk.END, "--> 2. Running Chisel Generator (SBT)...\n")
        window.update()

        sbt_cmd = ["sbt", f"runMain BPU_gen {config_name}"]
        subprocess.run(sbt_cmd, cwd=CHISEL_PROJECT_DIR, check=True, capture_output=True, text=True, shell=True)

        # STEP 3: Move Verilog to CPU folder
        log_box.insert(tk.END, "--> 3. Moving Verilog to CPU folder...\n")
        generated_file = os.path.join(CHISEL_PROJECT_DIR, "predictor_top.sv")

        if os.path.exists(generated_file):
            shutil.copy(generated_file, target_file)
        else:
            raise FileNotFoundError(f"Generated file not found: {generated_file}")

        # STEP 4: Ensure run_sim.tcl exists in the CPU directory
        log_box.insert(tk.END, "--> 4. Checking for TCL script...\n")
        tcl_target_path = os.path.join(cpu_sim_dir, "run_sim.tcl")
        if not os.path.exists(tcl_target_path):
            log_box.insert(tk.END, "       run_sim.tcl not found in CPU folder. Copying it now...\n")
            shutil.copy("run_sim.tcl", tcl_target_path)

        # STEP 5: Run the Vivado Simulation invisibly
        log_box.insert(tk.END, "--> 5. Running Vivado Simulation in Batch Mode...\n")
        window.update()

        sim_process = subprocess.run(SIM_COMMAND, cwd=cpu_sim_dir, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, shell=True)
        sim_output = sim_process.stdout

        if sim_process.returncode != 0:
            log_box.insert(tk.END, f"  (Note: Vivado exited with code {sim_process.returncode} from $finish)\n")

        # STEP 6: Parse Metrics and Save to CSV
        log_box.insert(tk.END, "--> 6. Extracting Metrics to CSV...\n")

        if "Instructions Executed:" in sim_output:
            # Use Regex to pull out the exact numbers
            instr = extract_metric(r"Instructions Executed:\s+(\d+)", sim_output)
            total_br = extract_metric(r"Total Branches:\s+(\d+)", sim_output)
            cond = extract_metric(r"Conditional:\s+(\d+)", sim_output)
            jumps = extract_metric(r"Jumps:\s+(\d+)", sim_output)
            mispred = extract_metric(r"Mispredicted:\s+(\d+)", sim_output)
            rate = extract_metric(r"Mispred Rate:\s+([0-9.]+)", sim_output)

            # Check if file exists to write headers
            write_header = not os.path.exists(CSV_FILE)

            with open(CSV_FILE, "a") as f:
                if write_header:
                    f.write("Config,Algorithm,Instructions Executed,Total Branches,Conditional,Jumps,Mispredicted,Mispred Rate\n")
                f.write(f"{config_name},{algorithm},{instr},{total_br},{cond},{jumps},{mispred},{rate}\n")

            log_box.insert(tk.END, f"\nSUCCESS! {config_name} ({algorithm}) metrics appended to {CSV_FILE}\n")
            log_box.insert(tk.END, f"Rate: {rate}%\n")
        else:
            log_box.insert(tk.END, "\nWARNING: Simulation finished, but metrics were not found.\n")

    except subprocess.CalledProcessError as e:
        log_box.insert(tk.END, f"\n[ERROR] Command failed with exit code {e.returncode}!\n")

        # SBT prints the actual Scala crash to stdout, so we need to print that
        if e.stdout:
            # Print the last 1500 characters
            log_box.insert(tk.END, f"--- Output Log ---\n{e.stdout[-1500:]}\n")

        if e.stderr and "WARNING: A terminally deprecated method" not in e.stderr:
            log_box.insert(tk.END, f"--- Error Log ---\n{e.stderr[-1000:]}\n")
    except Exception as e:
        log_box.insert(tk.END, f"\n[ERROR] {str(e)}\n")

    log_box.insert(tk.END, "-"*60 + "\n")
    log_box.see(tk.END)


# UI
window = tk.Tk()
window.title("BPU-Gen Automated Verification Pipeline")
window.geometry("700x650")

tk.Label(window, text="BPU-Gen Pipeline Manager", font=("Arial", 16, "bold")).pack(pady=10)

# Frame for Paths
path_frame = tk.LabelFrame(window, text="Directory Configuration", font=("Arial", 10, "bold"), padx=10, pady=10)
path_frame.pack(fill="x", padx=20, pady=5)

tk.Label(path_frame, text="CPU Sim Dir:").grid(row=0, column=0, sticky="e", pady=2)
sim_dir_entry = tk.Entry(path_frame, width=70)
sim_dir_entry.grid(row=0, column=1, pady=2)
sim_dir_entry.insert(0, DEFAULT_CPU_SIM_DIR)

tk.Label(path_frame, text="Target File:").grid(row=1, column=0, sticky="e", pady=2)
target_file_entry = tk.Entry(path_frame, width=70)
target_file_entry.grid(row=1, column=1, pady=2)
target_file_entry.insert(0, DEFAULT_TARGET_FILE)

# Frame for Actions
action_frame = tk.Frame(window)
action_frame.pack(pady=10)

tk.Label(action_frame, text="Config Name:", font=("Arial", 12)).grid(row=0, column=0, padx=5)
config_entry = tk.Entry(action_frame, font=("Arial", 12), width=15)
config_entry.grid(row=0, column=1, padx=5)
config_entry.insert(0, "test3")

run_btn = tk.Button(action_frame, text="Generate & Simulate", font=("Arial", 11, "bold"), bg="green", fg="white", command=run_pipeline)
run_btn.grid(row=0, column=2, padx=15)

clear_btn = tk.Button(action_frame, text="Clear Benchmark Results", font=("Arial", 11, "bold"), bg="red", fg="white", command=clear_benchmarks)
clear_btn.grid(row=0, column=3, padx=5)

log_box = scrolledtext.ScrolledText(window, width=80, height=20, font=("Consolas", 10), bg="black", fg="lightgreen")
log_box.pack(pady=10, padx=20)
log_box.insert(tk.END, "Ready...\n")

window.mainloop()