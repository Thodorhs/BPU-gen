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
* **Add cpu as a submodule to the repo**
* **Implemment/test GAg**
* **Make a script to run sbt generate and make the predictor.sv excactly where we want in the cpu files and run the metrics**
* **Create small presentation and demo**
* **Implemment/test PAg**
* **Collect execution data to compare the Bimodal, GAg, and PAg algorithms and measure performance and accuracy gains**
* **Make a script to plot the data**
* **Update README with how to run section**
* (If time allows) Parameterize the BTB too
* (If time allows) Implement a Tournament Predictor (combines Bimodal and GAg with a meta-predictor)
