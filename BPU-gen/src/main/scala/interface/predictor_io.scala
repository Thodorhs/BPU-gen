package interface
import chisel3._

// Matches: core::br_cntrl_bus_t
//MAYBE DONT NEED ALL THOSE SIGNALS FROM THE BUS BUT KEEP THEM FOR NOW
class BrCntrlBus(val addrWidth: Int) extends Bundle {
  val is_taken          = Bool() // 1 if we jumped to what we guessed
  val branch_target     = UInt(addrWidth.W) // the actual target the CPU jumped
  val i_addr            = UInt(addrWidth.W) // PC address that was used
  val mispredict        = Bool() // 1 if we predicted wrong
  val mispredict_target = UInt(addrWidth.W) // the target we predicted wrong
}

// Matches: predictor control module IO
class predictor_io(val addrWidth: Int) extends Bundle {
  // Inputs from CPU
  val br_cntrl_i    = Input(new BrCntrlBus(addrWidth)) // see above
  val is_branch_i   = Input(Bool()) // 1 if decoded instruction is branch
  val read_addr_i   = Input(UInt(addrWidth.W)) // PC address currently fetched
  //val read_addr_b_i = Input(UInt(addrWidth.W)) // ?????????

  // Outputs to CPU
  val prediction_o  = Output(Bool()) // 1 = taken 0 = not taken
  val pred2id       = Output(Bool()) // forward prediction_o to instruction decode ?
}