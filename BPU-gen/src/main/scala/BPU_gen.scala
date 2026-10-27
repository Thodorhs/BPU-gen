import chisel3._
import help.{json, print_help}
import predictor_core.predictor_top

object BPU_gen extends App {
  if (args.length != 1) {
    print_help.printHelp()
    sys.exit(1)
  }

  val configName = args(0)

  try {
    // 1. Get the generic config (it could be Bimodal or GAg)
    val config = json.parse_config(configName)

    println(s"Generating Verilog for configuration: $configName")

    // 2. Just pass it to PredictorTop! It will figure out what to build.
    emitVerilog(new predictor_top(config))

  } catch {
    case e: Exception => println(s"\n[ERROR] ${e.getMessage}\n")
  }
}