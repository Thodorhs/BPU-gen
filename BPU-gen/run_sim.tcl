open_project riscv.xpr

reset_simulation -simset sim_1 -mode behavioral

launch_simulation
run all
close_sim
close_project