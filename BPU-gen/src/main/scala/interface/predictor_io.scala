package interface
import chisel3._

// Matches: core::br_cntrl_bus_t
class BrCntrlBus(val addrWidth: Int) extends Bundle {
  val is_taken          = Bool()
  val branch_target     = UInt(addrWidth.W)
  val i_addr            = UInt(addrWidth.W)
  val mispredict        = Bool()
  val mispredict_target = UInt(addrWidth.W)
}

// Matches: predictor control module IO
class predictor_io(val addrWidth: Int) extends Bundle {
  // Inputs from CPU
  val br_cntrl_i    = Input(new BrCntrlBus(addrWidth))
  val is_branch_i   = Input(Bool())
  val read_addr_i   = Input(UInt(addrWidth.W))
  val read_addr_b_i = Input(UInt(addrWidth.W))

  // Outputs to CPU
  val prediction_o  = Output(Bool())
  val pred2id       = Output(Bool())
}