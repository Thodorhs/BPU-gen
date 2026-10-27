package predictor_core

import chisel3._
import chisel3.util.log2Ceil
import help.BimodalConfig
import _root_.interface.predictor_io

class bimodal(val config: BimodalConfig) extends base_predictor(config.address_width) {
  println(s"Generating PHT table for Bimodal BPU")

  val indexBits = log2Ceil(config.pht_size)
  val pht = RegInit(VecInit(Seq.fill(config.pht_size)(0.U(config.counter_width.W))))
  val pht_addr = io.read_addr_i(indexBits + 1, 2)
  io.prediction_o := false.B
    // Read from the Register array
    val pht_counter = pht(pht_addr)
    val msb = pht_counter(config.counter_width - 1)
    when (msb === 1.U) {
      io.prediction_o := true.B
    } .otherwise {
      io.prediction_o := false.B
    }


  val is_valid_update = io.br_cntrl_i.i_addr =/= 0.U
  when (is_valid_update) {
    val train_addr = io.br_cntrl_i.i_addr(indexBits + 1, 2)
    val old_counter = pht(train_addr)
    val maxVal = ((1 << config.counter_width) - 1).U
    val minVal = 0.U
    val new_counter = WireDefault(old_counter)
    // Saturate counter
    when (io.br_cntrl_i.is_taken && old_counter < maxVal) {
      new_counter := old_counter + 1.U
    } .elsewhen (!io.br_cntrl_i.is_taken && old_counter > minVal) {
      new_counter := old_counter - 1.U
    }
    pht(train_addr) := new_counter
  }
}