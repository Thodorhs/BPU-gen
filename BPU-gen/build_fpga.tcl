# Builds the bitstream of the FPGA project in the current directory.
# Called by BPU-gen.py ("Run on FPGA"):  vivado -mode batch -source build_fpga.tcl
# Prints BUILD_WNS <ns> and BUILD_OK, or BUILD_FAILED: <reason>.

set xpr [lindex [glob -nocomplain *.xpr] 0]
if {$xpr eq ""} { puts "BUILD_FAILED: no .xpr project in [pwd]"; exit 1 }
open_project $xpr

# Always start from scratch: Vivado does not notice changed .hex files.
reset_run synth_1
launch_runs impl_1 -to_step write_bitstream -jobs 4
wait_on_run impl_1

if {[get_property PROGRESS [get_runs impl_1]] ne "100%"} {
    puts "BUILD_FAILED: implementation did not finish (status: [get_property STATUS [get_runs impl_1]])"
    exit 1
}
if {[llength [glob -nocomplain [get_property DIRECTORY [get_runs impl_1]]/*.bit]] == 0} {
    puts "BUILD_FAILED: no bitstream was written"
    exit 1
}

puts "BUILD_WNS [get_property STATS.WNS [get_runs impl_1]]"
puts "BUILD_WHS [get_property STATS.WHS [get_runs impl_1]]"
puts "BUILD_OK"
close_project
