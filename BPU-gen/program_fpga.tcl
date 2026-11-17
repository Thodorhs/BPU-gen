# Programs the connected Nexys A7 with the bitstream of the FPGA project in the
# current directory. Called by BPU-gen.py ("Run on FPGA"):
#     vivado -mode batch -source program_fpga.tcl
# Prints PROGRAM_OK, or PROGRAM_FAILED: <reason>.

set bit [lindex [glob -nocomplain *.runs/impl_1/fpga_top.bit] 0]
if {$bit eq ""} { puts "PROGRAM_FAILED: no fpga_top.bit in [pwd]/*.runs/impl_1"; exit 1 }
set bit [file normalize $bit]

open_hw_manager
connect_hw_server
open_hw_target

set dev [lindex [get_hw_devices xc7a100t*] 0]
if {$dev eq ""} { puts "PROGRAM_FAILED: no xc7a100t found. Is the board connected and switched on?"; exit 1 }
current_hw_device $dev
set_property PROGRAM.FILE $bit $dev
program_hw_devices $dev

puts "PROGRAM_OK"
close_hw_target
disconnect_hw_server
close_hw_manager
