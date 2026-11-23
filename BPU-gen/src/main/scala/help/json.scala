package help
import chisel3.util.log2Ceil

import scala.io.Source
import ujson.Value

sealed trait bpu_config {
  def address_width: Int
  def activeLowReset: Boolean
}

case class BimodalConfig(address_width: Int, counter_width: Int, pht_size: Int, activeLowReset: Boolean) extends bpu_config
case class GAgConfig(address_width : Int, counter_width : Int, pht_size : Int,  ghr_size : Int, pc_bits : Int = 0, activeLowReset: Boolean) extends bpu_config
case class TournamentConfig(address_width: Int,choiceTableSize: Int,choiceCounterWidth: Int,localPhtSize: Int,localCounterWidth: Int,globalPhtSize: Int,globalGhrSize: Int,
globalCounterWidth: Int,activeLowReset: Boolean) extends bpu_config

case class PAgConfig(address_width : Int, counter_width : Int, pht_size : Int,  history_table_w : Int, history_table_sz : Int, pc_bits : Int = 0, activeLowReset: Boolean) extends bpu_config
object json {

  def parse_config(name: String, path: String = "config.json"): bpu_config = {
    val file = try{
      Source.fromFile(path).getLines().mkString
    } catch {
      case _: Exception => throw new Exception(s"Could not read from file: $path")
    }

    val data = try {
      ujson.read(file)
    } catch {
      case _: Exception => throw new Exception("Invalid JSON format.")
    }


    //general checks same for every algorithm
    require(data.obj.contains("address_width"), s"Missing 'address_width' for CPU architecture from config.json file.")
    val address_width =data("address_width").num.toInt

    val activeLowReset = if (data.obj.contains("active_low_reset")) data("active_low_reset").bool else false

    require(data.obj.contains(name), s"Missing configuration '$name' from config.json file.")
    val bpu_config = data(name)

    require(bpu_config.obj.contains("algorithm"), s"Missing 'algorithm' name from configuration '$name' from config file.")
    val algorithm = bpu_config("algorithm").str

    //PER ALGORITHM parsing and checks
    algorithm match {

      case "Bimodal" =>
        val pht_size = bpu_config("pattern_history_table_size").num.toInt
        val counter_width = bpu_config("counter_width").num.toInt
        require(pht_size > 0, "Bimodal Error: pattern_history_table_size must be > 0")
        require(counter_width > 0, "Bimodal Error: counter_width must be > 0")
        println(s"Parsed and validated Bimodal config '$name'")
        BimodalConfig(address_width, counter_width, pht_size, activeLowReset)
      case "GAg" =>
        val pht_size = bpu_config("pattern_history_table_size").num.toInt
        val counter_width = bpu_config("counter_width").num.toInt
        val ghr_size      = bpu_config("ghr_size").num.toInt
        // optional: number of PC bits concatenated to the GHR bits in the index (0 = pure GAg)
        val pc_bits       = bpu_config.obj.get("pc_bits").map(_.num.toInt).getOrElse(0)
        require(pht_size > 0, "Bimodal Error: pattern_history_table_size must be > 0")
        require(counter_width > 0, "Bimodal Error: counter_width must be > 0")
        require(ghr_size > 0, "Bimodal Error: ghr_size must be > 0")
        require(pc_bits >= 0, "GAg Error: pc_bits must be >= 0")

        println(s"Parsed and validated GAg config '$name'")
        GAgConfig(address_width = address_width, counter_width= counter_width , pht_size = pht_size, ghr_size = ghr_size, pc_bits = pc_bits, activeLowReset)
      case "Tournament" =>
        val choice_table_size    = bpu_config("choice_table_size").num.toInt
        val choice_counter_width = bpu_config("choice_counter_width").num.toInt

        // Local predictor parameters (Bimodal)
        val local_pht_size       = bpu_config("local_pht_size").num.toInt
        val local_counter_width  = bpu_config("local_counter_width").num.toInt

        // Global predictor parameters (GAg)
        val global_pht_size      = bpu_config("global_pht_size").num.toInt
        val global_ghr_size      = bpu_config("global_ghr_size").num.toInt
        val global_counter_width = bpu_config("global_counter_width").num.toInt

        // Validations for Choice (Meta) Table
        require(choice_table_size > 0, "Tournament Error: choice_table_size must be > 0")
        require((choice_table_size & (choice_table_size - 1)) == 0, "Tournament Error: choice_table_size must be a power of 2")
        require(choice_counter_width >= 2, "Tournament Error: choice_counter_width must be >= 2")

        // Validations for Local Table
        require(local_pht_size > 0, "Tournament Error: local_pht_size must be > 0")
        require((local_pht_size & (local_pht_size - 1)) == 0, "Tournament Error: local_pht_size must be a power of 2")
        require(local_counter_width >= 2, "Tournament Error: local_counter_width must be >= 2")

        // Validations for Global Table
        require(global_pht_size > 0, "Tournament Error: global_pht_size must be > 0")
        require((global_pht_size & (global_pht_size - 1)) == 0, "Tournament Error: global_pht_size must be a power of 2")
        require(global_ghr_size > 0, "Tournament Error: global_ghr_size must be > 0")
        require(global_counter_width >= 2, "Tournament Error: global_counter_width must be >= 2")

        println(s"Parsed and validated Tournament config '$name'")
        TournamentConfig(
          address_width       = address_width,
          choiceTableSize    = choice_table_size,
          choiceCounterWidth = choice_counter_width,
          localPhtSize       = local_pht_size,
          localCounterWidth  = local_counter_width,
          globalPhtSize      = global_pht_size,
          globalGhrSize      = global_ghr_size,
          globalCounterWidth = global_counter_width,
          activeLowReset     = activeLowReset
        )
      case "PAg" =>
        val pht_size = bpu_config("pattern_history_table_size").num.toInt
        val counter_width = bpu_config("counter_width").num.toInt
        val history_table_w = bpu_config("history_table_width").num.toInt
        val history_table_s = bpu_config("history_table_size").num.toInt
        val pc_bits = log2Ceil(history_table_s)
        println(s"Parsed and validated PAg config '$name'")
        PAgConfig(address_width = address_width,
          counter_width = counter_width,
          pht_size = pht_size,
          history_table_w = history_table_w,
          history_table_sz = history_table_s,
          pc_bits = pc_bits,
          activeLowReset = activeLowReset)
      case other =>
        throw new Exception(s"""Unknown or not implemented algorithm: '$other'. Valid options are: Bimodal, GAg, PAg.""")
    }
  }
}