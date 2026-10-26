package interface
import chisel3._

// Matches: core::br_cntrl_bus_t
class BrCntrlBus(val addrWidth: Int) extends Bundle {
  val is_taken          = Bool() // 1 if we jumped to what we guessed
  val i_addr            = UInt(addrWidth.W) // PC address that was used
  val branch_resolved   = Bool() // 1 when execute stage has resolved the branch (is_taken/i_addr valid)
}

// Matches: predictor control module IO
class predictor_io(val addrWidth: Int) extends Bundle {
  // Inputs from CPU
  val br_cntrl_i    = Input(new BrCntrlBus(addrWidth)) // see above
  val is_branch_i   = Input(Bool()) // 1 if decoded instruction is branch
  val read_addr_i   = Input(UInt(addrWidth.W)) // PC address currently fetched

  // Outputs to CPU
  val prediction_o  = Output(Bool()) // 1 = taken 0 = not taken
}