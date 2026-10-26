package predictor_core
import chisel3._
import chisel3.util.log2Ceil
import help.BimodalConfig

class bimodal(val config: BimodalConfig) extends base_predictor(config.address_width) {
  println(s"Generating pht table, prediction logic and counter update logic for Bimodal BPU")
  val indexBits = log2Ceil(config.pht_size)
  private val asyncReset = (!reset.asBool).asAsyncReset
  private val pht = withReset(asyncReset) {
    RegInit(VecInit(Seq.fill(config.pht_size)(
      0.U(config.counter_width.W)
    )))
  }

  val pht_addr = io.read_addr_i(indexBits + 1, 2)
  io.prediction_o := false.B

  // prediction logic

  val pht_counter = pht(pht_addr)
  val msb = pht_counter(config.counter_width-1)
  when ( msb === 1.U) {
    io.prediction_o := true.B
    } .otherwise {
      io.prediction_o := false.B
    }


  // train when i get address back from br_cntrl_bus_t
  when (io.br_cntrl_i.branch_resolved =/= 0.U){
    val train_addr = io.br_cntrl_i.i_addr(indexBits + 1, 2)
    val old_counter = pht(train_addr)
    val new_counter = WireDefault(old_counter)
    val max_val = ((1 << config.counter_width) - 1).U
    val min_val = 0.U

    //saturate counter dont go above limmits
    when (io.br_cntrl_i.is_taken && old_counter < max_val) {
      new_counter := old_counter + 1.U
    } .elsewhen (!io.br_cntrl_i.is_taken && old_counter > min_val) {
      new_counter := old_counter - 1.U
    }
    //update the counter
    pht(train_addr) := new_counter
  }
}
