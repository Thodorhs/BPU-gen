# BPU-Gen: Parameterized Branch Predictor Generator

## Project Description
**BPU-Gen** is a highly configurable hardware generator for Branch Predictor Units (BPUs), written in Chisel. 

In CPU pipelines, branch predictors guess the outcome of branches to prevent the processor from stalling. This project builds a configurable generator in Chisel. It allows us to automatically create different types of branch predictors,e.g Bimodal predictors or GShare predictors, just by changing a few settings.

## Key Features (Planned)
Because this is a **generator**, the hardware is entirely polymorphic. Users will be able to configure:
* **Algorithm Selection:** Select between Bimodal, Global History (GAg), Per-Address History (PAg), or simple Static prediction. 
* **Table Sizes:** Dynamically size the Pattern History Table (PHT) memory blocks (e.g., 64 entries vs. 4096 entries).
* **Counter Widths:** Parameterize the N-bit saturating counters (e.g., standard 2-bit vs. highly confident 3-bit counters).
* **History Length:** Configure the length and size of the history registers for the two-level algorithms.
* **Works with any CPU:** You can just set the memory address size (like 32-bit or 64-bit) as a parameter, and it will generate the correct hardware for whatever CPU you are building.

## Development Methodology
This project is managed using the **Scrum methodology** new things might come.

## Planning
* **Configuration using JSON** Parse JSON file used as configuration (address width32/64, algorithm, pht size, counter width, history table wifth/size) ✅
* **Algorithm selection Type:** Select between Bimodal, GShare, or simple Static prediction ✅
* **Handle errors of programm input/config** ✅
* **Agree on Chisel IO to match CPU interface** ✅
* **Implemment/test Bimodal** ✅
* **Integrate the generated Verilog into our existing SystemVerilog RISC-V CPU and run** ✅
* **Add cpu as a submodule to the repo** ✅
* **Implemment/test GAg** ✅
* **Make a python script with UI to run sbt generate and make the predictor.sv excactly where we want in the cpu files and run the metrics** ✅
* **Add to the script option to plot the collected execution data (from a file) of the various setups and compare the BPUs** ✅
* **Synthesize** ✅
* **update simulation metrics display to say what kind of test is running and if it is simulated**
* **Create a UART that will run and take results back**
* **update python script to run inplementation and bitstream and flash to fpga (tcl script)**
* **hardcoded "Fibonacci" and "Simulated" into the CSV outputs needs to get values from either from simulation or from fpga**
* **Add tests with different kind of programs**
* **Implemment/test PAg**
* **Implemment/test tournament predictor (run 2 predictors and select the best result)**
* **Try to flash to an FPGA and get real data ?**
* **Update README/Report (dont forget how to run section)**
* **Create final presentation and demo**
