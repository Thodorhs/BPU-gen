package predictor_core
import chisel3._
import _root_.interface.predictor_io

abstract class base_predictor(val addrWidth: Int) extends Module{
  val io = IO(new predictor_io(addrWidth))
}
