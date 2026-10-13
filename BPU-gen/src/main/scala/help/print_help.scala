package help

object print_help {
  def printHelp(): Unit = {
    val helpText = """
                     |=================================================================
                     | BPU-Gen: Parameterized Branch Predictor Generator
                     |=================================================================
                     |
                     | ERROR: Invalid arguments.
                     |
                     | USAGE:
                     | sbt "runMain BPU_gen <config_name>"
                     |
                     | Exactly ONE argument is required: the name of the configuration
                     | block you want to generate from your config.json file.
                     |
                     | EXAMPLE:
                     | sbt "runMain BPU_gen test2"
                     |
                     |-----------------------------------------------------------------
                     | CONFIG.JSON FORMAT & PARAMETERS:
                     |-----------------------------------------------------------------
                     | The config.json file must contain an "address_width" at the root,
                     | followed by your named configurations.
                     |
                     | SUPPORTED ALGORITHMS & REQUIRED FIELDS:
                     | 1. "Bimodal"
                     |    - pattern_history_table_size : Int (e.g., 1024)
                     |    - counter_width              : Int (e.g., 2)
                     |
                     | 2. "GAg" (Global History)
                     |
                     | 3. "PAg" (Per-Address History)
                     |
                     |=================================================================
                     |""".stripMargin.stripTrailing

    println(helpText)
  }
}
