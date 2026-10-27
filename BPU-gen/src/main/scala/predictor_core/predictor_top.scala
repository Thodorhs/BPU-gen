package predictor_core

import chisel3._
import _root_.interface.predictor_io
import help.{bpu_config, BimodalConfig, GAgConfig} // Import the configs

class predictor_top(config: bpu_config) extends RawModule {
    val clk = IO(Input(Clock()))
    val rst = IO(Input(Bool()))
    val io = IO(new predictor_io(config.address_width))
    val internal_reset = if (config.activeLowReset) !rst else rst

    withClockAndReset(clk, internal_reset) {
      val core = config match {
        case b: BimodalConfig => Module(new bimodal(b))
        case g: GAgConfig     => Module(new GAg(g))
        case _ => throw new Exception("Unknown config passed to PredictorTop!")
      }

      // Wire all the IOs together
      io <> core.io
    }
  }