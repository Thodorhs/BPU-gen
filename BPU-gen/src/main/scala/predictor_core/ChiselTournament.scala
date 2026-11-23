package predictor_core

import chisel3._
import chisel3.util._
import help.TournamentConfig

// Tournament predictor combining Bimodal (Local) and GAg (Global).
//
// A choice PHT (RAM, initialized to 0) tracks which predictor is currently
// more accurate for each PC:
//   - Counter MSB = 0: Predict using Bimodal (Local)
//   - Counter MSB = 1: Predict using GAg (Global)
//
// The choice table is indexed by PC[2 +: choice_index_bits].
// When a branch resolves:
//   - Both sub-predictors are updated unconditionally.
//   - The choice counter updates ONLY when one was right and the other was wrong.
class ChiselTournament(val config: TournamentConfig)
  extends base_predictor(config.address_width) {

  println("Generating Tournament BPU")

  private val choicePhtSize      = config.choiceTableSize
  private val choiceCounterWidth = config.choiceCounterWidth
  private val choiceIndexBits    = log2Ceil(choicePhtSize)

  require(choicePhtSize >= 2, "Choice PHT size must be at least 2")
  require((1 << choiceIndexBits) == choicePhtSize, "Choice PHT size must be a power of two")
  require(choiceCounterWidth >= 2, "Choice counter width must be at least 2")

  // 1. Instantiate the two sub-predictors
  // (Assuming Bimodal and GAg constructors take their respective configs or parameters)
  private val localPred  = Module(new bimodal(
    help.BimodalConfig(
      address_width = config.address_width,
      counter_width = config.localCounterWidth,
      pht_size      = config.localPhtSize,
      activeLowReset = config.activeLowReset
    )
  ))

  private val globalPred = Module(new GAg(
    help.GAgConfig(
      address_width = config.address_width,
      counter_width = config.globalCounterWidth,
      pht_size      = config.globalPhtSize,
      ghr_size      = config.globalGhrSize,
      activeLowReset = config.activeLowReset
    )
  ))

  // Connect common inputs to both sub-predictors
  localPred.io.read_addr_i   := io.read_addr_i
  localPred.io.br_cntrl_i    := io.br_cntrl_i

  globalPred.io.read_addr_i  := io.read_addr_i
  globalPred.io.br_cntrl_i   := io.br_cntrl_i

  // 2. Choice table using RAM + pht_init (matching your style!)
  private val choicePht = Mem(choicePhtSize, UInt(choiceCounterWidth.W))
  pht_init(choicePht, choicePhtSize)

  private val maxChoice = ((BigInt(1) << choiceCounterWidth) - 1).U(choiceCounterWidth.W)

  // Index choice table using word-aligned PC bits: PC[choiceIndexBits + 1 : 2]
  private def choiceIndex(pc: UInt): UInt = pc(choiceIndexBits + 1, 2)

  private val rIdx = choiceIndex(io.read_addr_i)
  private val wIdx = choiceIndex(io.br_cntrl_i.i_addr)

  // 3. Prediction Phase
  // MSB = 1 -> select Global (GAg), MSB = 0 -> select Local (Bimodal)
  private val chooseGlobal = choicePht(rIdx)(choiceCounterWidth - 1) === 1.U

  io.prediction_o := Mux(chooseGlobal, globalPred.io.prediction_o, localPred.io.prediction_o)

  // 4. Training Phase
  private val actualTaken = io.br_cntrl_i.is_taken

  when(io.br_cntrl_i.branch_resolved) {
    val curChoice     = choicePht(wIdx)
    val localCorrect  = localPred.io.prediction_o === actualTaken
    val globalCorrect = globalPred.io.prediction_o === actualTaken

    // Only update choice counter when the two sub-predictors disagreed!
    when(globalCorrect && !localCorrect) {
      // Global won: increment toward Global
      when(curChoice =/= maxChoice) {
        choicePht(wIdx) := curChoice + 1.U
      }
    }.elsewhen(localCorrect && !globalCorrect) {
      // Local won: decrement toward Local
      when(curChoice =/= 0.U) {
        choicePht(wIdx) := curChoice - 1.U
      }
    }
  }
}