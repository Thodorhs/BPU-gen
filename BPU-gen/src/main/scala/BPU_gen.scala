
import help._
import chisel3._
import chisel3.util._
import predictor_core.bimodal

object BPU_gen extends App {

  def run(args: Array[String]): Unit = {
    if (args.length != 1) {
      print_help.printHelp()
      return
    }

    val config = args(0)
    println(s"Starting BPU-gen for configuration: $config")

    try {
      val algorithm_config = json.parse_config(config)

      algorithm_config match {
        case b: BimodalConfig =>
          println("Generating Verilog for Bimodal Predictor...")
          emitVerilog(new bimodal(b))
          println("Done Generating Bimodal predictor!")
      }
    } catch {
      case e: Exception =>
        println(s"\n[GENERATION STOPPED] ${e.getMessage}\n")
    }
  }

  run(args)
}
