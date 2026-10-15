package predictor_core
import chisel3._
import help.BimodalConfig

class bimodal(val config: BimodalConfig) extends base_predictor(config.address_width) {
  println(s"Generating pht table for Bimodal BPU")
  val pht = SyncReadMem(config.pht_size,UInt(config.counter_width.W))

  io.prediction_o := false.B
  io.pred2id := false.B

}
