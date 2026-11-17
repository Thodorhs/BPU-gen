import tkinter as tk
from tkinter import scrolledtext, messagebox
import subprocess
import shutil
import os
import datetime
import re
import json
import csv
import time
import matplotlib.pyplot as plt

# DIR and COMMAND DEFINES
CHISEL_PROJECT_DIR = "."  # Where your build.sbt is
DEFAULT_CPU_SIM_DIR = "C:/Users/prote/Desktop/DTU/02201_Agile_Hardware_Design/core/riscv"
DEFAULT_FPGA_DIR = "C:/Users/prote/Desktop/DTU/02201_Agile_Hardware_Design/core/riscv_fpga"
DEFAULT_TARGET_FILE = "./RISC-V-Core/core/synthesis/predictor_top.sv"
DEFAULT_TEST_NAME = "Fibonacci"
DEFAULT_COM_PORT = "auto"     # "auto" = find the Nexys board's USB-UART, or e.g. "COM5"

# PUT the absolute path FOR NOW ...
VIVADO_BAT = "C:/Xilinx/2025.1/Vivado/bin/vivado.bat"
SIM_COMMAND = [VIVADO_BAT, "-mode", "batch", "-source", "run_sim.tcl"]
FPGA_BUILD_COMMAND = [VIVADO_BAT, "-mode", "batch", "-source", "build_fpga.tcl"]
FPGA_PROGRAM_COMMAND = [VIVADO_BAT, "-mode", "batch", "-source", "program_fpga.tcl"]

UART_BAUD = 115200
FPGA_REPORT_TIMEOUT_S = 40    # the board itself gives up after 10 s (TIMEOUT report)

CSV_FILE = "benchmark_results.csv"
CSV_HEADER = ["Config", "Algorithm", "Test", "Platform", "Instructions Executed", "Total Branches",
              "Conditional", "Jumps", "Mispredicted", "Mispred Rate"]
PLOT_DIR = "benchmark_plots"

#HELPER
def log(msg):
    log_box.insert(tk.END, msg)
    log_box.see(tk.END)
    window.update()

def timestamp():
    return datetime.datetime.now().strftime('%H:%M:%S')

def clear_benchmarks():
    with open(CSV_FILE, "w", newline="") as f:
        csv.writer(f).writerow(CSV_HEADER)

    log(f"\n[{timestamp()}] Cleared contents of {CSV_FILE}!\n")

def append_result(row):
    # one row in CSV_HEADER order; csv.writer quotes any commas in the test name
    write_header = not os.path.exists(CSV_FILE)
    with open(CSV_FILE, "a", newline="") as f:
        writer = csv.writer(f)
        if write_header:
            writer.writerow(CSV_HEADER)
        writer.writerow(row)

def get_test_name():
    test_name = test_entry.get().strip()
    if not test_name:
        raise Exception("Please enter the name of the test program (e.g., Fibonacci)")
    return test_name

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

def generate_plots():
    log_box.insert(tk.END, f"\n[{datetime.datetime.now().strftime('%H:%M:%S')}] Generating Plots...\n")
    window.update()

    if os.path.exists(PLOT_DIR):
        shutil.rmtree(PLOT_DIR)
    os.makedirs(PLOT_DIR)

    if not os.path.exists(CSV_FILE):
        log_box.insert(tk.END, "[ERROR] No benchmark_results.csv found to plot.\n")
        return

    # open config.json to grab PHT sizes
    try:
        with open(os.path.join(CHISEL_PROJECT_DIR, "config.json"), "r") as f:
            config_data = json.load(f)
    except Exception as e:
        log_box.insert(tk.END, f"[ERROR] Could not read config.json: {e}\n")
        return

    # read the CSV and group data by Platform and Test Type
    groups = {}
    try:
        with open(CSV_FILE, "r") as f:
            reader = csv.DictReader(f)
            for row in reader:
                platform = row.get("Platform", "Unknown")
                test_type = row.get("Test", "Unknown")
                config_name = row["Config"]
                algo = row["Algorithm"]
                mispred_rate = float(row["Mispred Rate"])
                pht_size = "Unknown"
                if config_name in config_data and "pattern_history_table_size" in config_data[config_name]:
                    pht_size = str(config_data[config_name]["pattern_history_table_size"])

                #create the bar label: testname_algorithm_phtsize
                label = f"{config_name}\n{algo}\n(PHT: {pht_size})"

                group_key = (platform, test_type)
                if group_key not in groups:
                    groups[group_key] = {"labels": [], "mispred_rates": []}

                groups[group_key]["labels"].append(label)
                groups[group_key]["mispred_rates"].append(mispred_rate)

        #generate a bar chart for every Platform/Test combination
        color_palette = ['salmon', 'skyblue', 'lightgreen', 'plum', 'gold', 'lightcoral', 'cornflowerblue']

        for (platform, test_type), data in groups.items():
            plt.figure(figsize=(10, 6.5))
            bar_colors = [color_palette[i % len(color_palette)] for i in range(len(data["labels"]))]
            bars = plt.bar(data["labels"], data["mispred_rates"], color=bar_colors, edgecolor='black')
            max_rate = max(data["mispred_rates"]) if data["mispred_rates"] else 100
            plt.ylim(0, min(100, max_rate + 5))
            plt.ylabel("Misprediction Rate (%)", fontsize=12)
            plt.xlabel("Predictor Configuration\n(Format: [Config Name]  -  [Algorithm]  -  [PHT Size])", fontsize=11, labelpad=10)
            plt.title(f"Branch Predictor Misprediction Rate (Lower is Better)\nTest: {test_type} | Platform: {platform}", fontsize=14, fontweight="bold")
            plt.xticks(rotation=0, fontsize=10)
            for bar in bars:
                yval = bar.get_height()
                plt.text(bar.get_x() + bar.get_width()/2, yval + (max_rate * 0.02), f"{yval:.2f}%", ha='center', va='bottom', fontweight='bold', fontsize=11)
            plt.tight_layout()
            filename = f"{platform}_{test_type}_MispredictionRate.png".replace(" ", "_")
            filepath = os.path.join(PLOT_DIR, filename)
            plt.savefig(filepath)
            plt.close()

        log_box.insert(tk.END, f"SUCCESS! Plots generated in the '{PLOT_DIR}' folder.\n")
    except Exception as e:
        log_box.insert(tk.END, f"[ERROR] Failed to generate plots: {e}\n")

    log_box.see(tk.END)


# SHARED: Chisel test + generation (steps 1-3 of both flows)
def generate_bpu(config_name, target_file):
    # STEP 1: Parse JSON to find the algorithm and run the specific Chisel Test
    log("--> 1. Running Pre-Simulation Chisel Tests...\n")

    with open(os.path.join(CHISEL_PROJECT_DIR, "config.json"), "r") as f:
        config_data = json.load(f)

    if config_name not in config_data:
        raise Exception(f"Config '{config_name}' not found in config.json!")

    algorithm = config_data[config_name].get("algorithm", "")
    test_class = get_test_class(algorithm)

    if test_class:
        log(f"       Detected Algorithm: {algorithm}. Running test: {test_class}\n")
        test_cmd = ["sbt", f"testOnly {test_class}"]
        subprocess.run(test_cmd, cwd=CHISEL_PROJECT_DIR, check=True, capture_output=True, text=True, shell=True)
        log("       Chisel Test PASSED! Hardware is valid.\n")
    else:
        log(f"       Warning: No automated test mapped for algorithm '{algorithm}'. Skipping test.\n")

    # STEP 2: Run SBT to Generate the Hardware
    log("--> 2. Running Chisel Generator (SBT)...\n")

    sbt_cmd = ["sbt", f"runMain BPU_gen {config_name}"]
    subprocess.run(sbt_cmd, cwd=CHISEL_PROJECT_DIR, check=True, capture_output=True, text=True, shell=True)

    # STEP 3: Move Verilog to CPU folder
    log("--> 3. Moving Verilog to CPU folder...\n")
    generated_file = os.path.join(CHISEL_PROJECT_DIR, "predictor_top.sv")

    if os.path.exists(generated_file):
        shutil.copy(generated_file, target_file)
    else:
        raise FileNotFoundError(f"Generated file not found: {generated_file}")

    return algorithm


def report_error(e):
    if isinstance(e, subprocess.CalledProcessError):
        log(f"\n[ERROR] Command failed with exit code {e.returncode}!\n")

        # SBT prints the actual Scala crash to stdout, so we need to print that
        if e.stdout:
            # Print the last 1500 characters
            log(f"--- Output Log ---\n{e.stdout[-1500:]}\n")

        if e.stderr and "WARNING: A terminally deprecated method" not in e.stderr:
            log(f"--- Error Log ---\n{e.stderr[-1000:]}\n")
    else:
        log(f"\n[ERROR] {str(e)}\n")


# MAIN STUFF: simulation
def run_pipeline():
    config_name = config_entry.get().strip()
    cpu_sim_dir = sim_dir_entry.get().strip()
    target_file = target_file_entry.get().strip()

    if not config_name:
        messagebox.showerror("Error", "Please enter a config name (e.g., test3)")
        return

    log(f"\n[{timestamp()}] Starting Simulation Pipeline for '{config_name}'...\n")

    try:
        test_name = get_test_name()

        # STEPS 1-3: Chisel test, generate, copy
        algorithm = generate_bpu(config_name, target_file)

        # STEP 4: Ensure run_sim.tcl exists in the CPU directory
        log("--> 4. Checking for TCL script...\n")
        tcl_target_path = os.path.join(cpu_sim_dir, "run_sim.tcl")
        if not os.path.exists(tcl_target_path):
            log("       run_sim.tcl not found in CPU folder. Copying it now...\n")
            shutil.copy("run_sim.tcl", tcl_target_path)

        # STEP 5: Run the Vivado Simulation invisibly
        log("--> 5. Running Vivado Simulation in Batch Mode...\n")

        sim_process = subprocess.run(SIM_COMMAND, cwd=cpu_sim_dir, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, shell=True)
        sim_output = sim_process.stdout

        if sim_process.returncode != 0:
            log(f"  (Note: Vivado exited with code {sim_process.returncode} from $finish)\n")

        # STEP 6: Parse Metrics and Save to CSV
        log("--> 6. Extracting Metrics to CSV...\n")

        if "Instructions Executed:" in sim_output:
            # Use Regex to pull out the exact numbers
            instr = extract_metric(r"Instructions Executed:\s+(\d+)", sim_output)
            total_br = extract_metric(r"Total Branches:\s+(\d+)", sim_output)
            cond = extract_metric(r"Conditional:\s+(\d+)", sim_output)
            jumps = extract_metric(r"Jumps:\s+(\d+)", sim_output)
            mispred = extract_metric(r"Mispredicted:\s+(\d+)", sim_output)
            rate = extract_metric(r"Mispred Rate:\s+([0-9.]+)", sim_output)

            append_result([config_name, algorithm, test_name, "Simulated",
                           instr, total_br, cond, jumps, mispred, rate])

            log(f"\nSUCCESS! {config_name} ({algorithm}, {test_name}, Simulated) metrics appended to {CSV_FILE}\n")
            log(f"Rate: {rate}%\n")
        else:
            log("\nWARNING: Simulation finished, but metrics were not found.\n")

    except Exception as e:
        report_error(e)

    log("-"*60 + "\n")


# FPGA HELPERS
def run_vivado_logged(cmd, cwd, log_name):
    # Runs Vivado in batch mode and writes its output to <cwd>/<log_name>.
    # Polls instead of blocking, so the window stays responsive during long builds.
    log_path = os.path.join(cwd, log_name)
    start = time.time()
    with open(log_path, "w") as log_file:
        proc = subprocess.Popen(cmd, cwd=cwd, stdout=log_file, stderr=subprocess.STDOUT, text=True, shell=True)
        last_note = start
        while proc.poll() is None:
            window.update()
            time.sleep(0.2)
            if time.time() - last_note >= 60:
                log(f"       ... still running ({int(time.time() - start) // 60} min)\n")
                last_note = time.time()
    with open(log_path, "r", errors="replace") as f:
        output = f.read()
    return proc.returncode, output, log_path

def find_board_port(port_setting):
    # "auto": the Nexys A7 USB-UART is an FTDI chip (USB vendor ID 0x0403)
    from serial.tools import list_ports
    if port_setting and port_setting.lower() != "auto":
        return port_setting
    ports = sorted((p for p in list_ports.comports() if p.vid == 0x0403), key=lambda p: p.device)
    if not ports:
        raise Exception("No FPGA board COM port found. Is the board connected and switched on? "
                        "You can also type the port (e.g. COM5) in the COM Port field.")
    return ports[-1].device

def read_fpga_report(ser, timeout_s):
    # Collects UART text until the report's closing line of '=' (or the timeout)
    text = ""
    deadline = time.time() + timeout_s
    while time.time() < deadline:
        chunk = ser.read(ser.in_waiting or 1)
        if chunk:
            text += chunk.decode("ascii", errors="replace")
            if re.search(r"FPGA test ====.*?\n={20,}", text, re.S):
                break
        window.update()
    return text

def parse_fpga_report(text):
    start = text.find("FPGA test ====")
    if start < 0:
        return None
    report = text[start:]

    def num(label):
        m = re.search(label + r"\s*:\s*(\d+)", report)
        return int(m.group(1)) if m else None

    m = re.search(r"result\s*:\s*(\w+)", report)
    return {
        "result":   m.group(1) if m else "UNKNOWN",
        "exit":     num(r"exit code"),
        "cycles":   num(r"cycles"),
        "instr":    num(r"instructions"),
        "cond":     num(r"cond\. branches"),
        "jumps":    num(r"jumps"),
        "mispred":  num(r"mispredicted"),
    }


# MAIN STUFF: FPGA
def run_fpga():
    config_name = config_entry.get().strip()
    fpga_dir = fpga_dir_entry.get().strip()
    target_file = target_file_entry.get().strip()

    if not config_name:
        messagebox.showerror("Error", "Please enter a config name (e.g., test3)")
        return

    log(f"\n[{timestamp()}] Starting FPGA Pipeline for '{config_name}'...\n")
    fpga_btn.config(state=tk.DISABLED)
    run_btn.config(state=tk.DISABLED)

    try:
        test_name = get_test_name()
        try:
            import serial
        except ImportError:
            raise Exception("The 'pyserial' package is missing. Install it with:  pip install pyserial")

        if not os.path.isdir(fpga_dir) or not any(n.endswith(".xpr") for n in os.listdir(fpga_dir)):
            raise Exception(f"No Vivado project (.xpr) found in FPGA Project Dir: {fpga_dir}")

        # STEPS 1-3: Chisel test, generate, copy (same as the simulation)
        algorithm = generate_bpu(config_name, target_file)

        # STEP 4: Copy the Vivado scripts into the FPGA project folder
        log("--> 4. Copying build/program TCL scripts to the FPGA project...\n")
        for script in ("build_fpga.tcl", "program_fpga.tcl"):
            shutil.copy(script, os.path.join(fpga_dir, script))

        # STEP 5: Synthesis + implementation + bitstream
        log("--> 5. Building bitstream (synthesis + implementation, takes a few minutes)...\n")
        rc, out, log_path = run_vivado_logged(FPGA_BUILD_COMMAND, fpga_dir, "fpga_build.log")
        if "BUILD_OK" not in out:
            m = re.search(r"BUILD_FAILED: (.*)", out)
            raise Exception(f"Bitstream build failed: {m.group(1) if m else 'see ' + log_path}")
        wns = float(extract_metric(r"BUILD_WNS\s+(-?[0-9.]+)", out))
        whs = float(extract_metric(r"BUILD_WHS\s+(-?[0-9.]+)", out))
        log(f"       Bitstream ready. WNS = {wns:.3f} ns, WHS = {whs:.3f} ns\n")
        if wns < 0 or whs < 0:
            raise Exception("Timing is not met, so results from the board would not be reliable. "
                            f"Check the timing report (log: {log_path}).")

        # STEP 6: Open the UART first (the report arrives right after programming), then program
        port = find_board_port(com_port_entry.get().strip())
        log(f"--> 6. Programming the FPGA (UART on {port})...\n")
        with serial.Serial(port, UART_BAUD, timeout=0.2) as ser:
            ser.reset_input_buffer()
            rc, out, log_path = run_vivado_logged(FPGA_PROGRAM_COMMAND, fpga_dir, "fpga_program.log")
            if "PROGRAM_OK" not in out:
                m = re.search(r"PROGRAM_FAILED: (.*)", out)
                raise Exception(f"Programming failed: {m.group(1) if m else 'see ' + log_path}")

            # STEP 7: Read the report from the board
            log("--> 7. Waiting for the test report from the board...\n")
            uart_text = read_fpga_report(ser, FPGA_REPORT_TIMEOUT_S)

        rep = parse_fpga_report(uart_text)
        if rep is None:
            raise Exception("No report received from the board. Is the COM port right, and is PuTTY closed? "
                            f"Received: {uart_text[-300:]!r}")
        report_start = uart_text.find("====")
        log(uart_text[report_start:].replace("\r", "") + "\n")

        # STEP 8: Save to CSV
        log("--> 8. Extracting Metrics to CSV...\n")
        if rep["result"] == "TIMEOUT":
            raise Exception("The program did not finish on the board (TIMEOUT). Nothing was added to the CSV.")
        if None in (rep["instr"], rep["cond"], rep["jumps"], rep["mispred"]):
            raise Exception("The report from the board was incomplete. Nothing was added to the CSV.")

        total_br = rep["cond"] + rep["jumps"]
        # same rounding as the simulation's %0.2f
        rate = f"{100.0 * rep['mispred'] / rep['cond']:.2f}" if rep["cond"] else "0.00"

        append_result([config_name, algorithm, test_name, "FPGA",
                       rep["instr"], total_br, rep["cond"], rep["jumps"], rep["mispred"], rate])

        log(f"\nSUCCESS! {config_name} ({algorithm}, {test_name}, FPGA) metrics appended to {CSV_FILE}\n")
        log(f"Result: {rep['result']} (return value {rep['exit']}), {rep['cycles']} cycles, Rate: {rate}%\n")

    except Exception as e:
        report_error(e)
    finally:
        fpga_btn.config(state=tk.NORMAL)
        run_btn.config(state=tk.NORMAL)

    log("-"*60 + "\n")


# UI
window = tk.Tk()
window.title("BPU-Gen Automated Verification Pipeline")
window.geometry("820x720")

tk.Label(window, text="BPU-Gen Pipeline Manager", font=("Arial", 16, "bold")).pack(pady=10)

# Frame for Paths
path_frame = tk.LabelFrame(window, text="Directory Configuration", font=("Arial", 10, "bold"), padx=10, pady=10)
path_frame.pack(fill="x", padx=20, pady=5)

tk.Label(path_frame, text="CPU Sim Dir:").grid(row=0, column=0, sticky="e", pady=2)
sim_dir_entry = tk.Entry(path_frame, width=80)
sim_dir_entry.grid(row=0, column=1, pady=2)
sim_dir_entry.insert(0, DEFAULT_CPU_SIM_DIR)

tk.Label(path_frame, text="FPGA Project Dir:").grid(row=1, column=0, sticky="e", pady=2)
fpga_dir_entry = tk.Entry(path_frame, width=80)
fpga_dir_entry.grid(row=1, column=1, pady=2)
fpga_dir_entry.insert(0, DEFAULT_FPGA_DIR)

tk.Label(path_frame, text="Target File:").grid(row=2, column=0, sticky="e", pady=2)
target_file_entry = tk.Entry(path_frame, width=80)
target_file_entry.grid(row=2, column=1, pady=2)
target_file_entry.insert(0, DEFAULT_TARGET_FILE)

tk.Label(path_frame, text="COM Port:").grid(row=3, column=0, sticky="e", pady=2)
com_port_entry = tk.Entry(path_frame, width=12)
com_port_entry.grid(row=3, column=1, sticky="w", pady=2)
com_port_entry.insert(0, DEFAULT_COM_PORT)

# Frame for Actions
action_frame = tk.Frame(window)
action_frame.pack(pady=10)

tk.Label(action_frame, text="Config Name:", font=("Arial", 12)).grid(row=0, column=0, padx=5)
config_entry = tk.Entry(action_frame, font=("Arial", 12), width=12)
config_entry.grid(row=0, column=1, padx=5)
config_entry.insert(0, "test3")

# name of the program in code.hex / codemem.hex, saved in the CSV "Test" column
tk.Label(action_frame, text="Test Program:", font=("Arial", 12)).grid(row=0, column=2, padx=5)
test_entry = tk.Entry(action_frame, font=("Arial", 12), width=14)
test_entry.grid(row=0, column=3, padx=5, columnspan=2, sticky="w")
test_entry.insert(0, DEFAULT_TEST_NAME)

# Buttons next to each other
button_frame = tk.Frame(window)
button_frame.pack(pady=5)

run_btn = tk.Button(button_frame, text="Run Simulation", font=("Arial", 11, "bold"), bg="green", fg="white", command=run_pipeline)
run_btn.grid(row=0, column=0, padx=10)

fpga_btn = tk.Button(button_frame, text="Run on FPGA", font=("Arial", 11, "bold"), bg="darkorange", fg="white", command=run_fpga)
fpga_btn.grid(row=0, column=1, padx=10)

plot_btn = tk.Button(button_frame, text="Generate Plots", font=("Arial", 11, "bold"), bg="#0052cc", fg="white", command=generate_plots)
plot_btn.grid(row=0, column=2, padx=10)

clear_btn = tk.Button(button_frame, text="Clear Data", font=("Arial", 11, "bold"), bg="red", fg="white", command=clear_benchmarks)
clear_btn.grid(row=0, column=3, padx=5)

log_box = scrolledtext.ScrolledText(window, width=90, height=20, font=("Consolas", 10), bg="black", fg="lightgreen")
log_box.pack(pady=10, padx=20)
log_box.insert(tk.END, "Ready...\n")

window.mainloop()
