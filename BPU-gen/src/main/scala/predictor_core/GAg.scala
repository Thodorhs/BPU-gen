package predictor_core
import chisel3._
import chisel3.util._
import help.GAgConfig

// Global-history 2-level predictor, ported from the hand-written predictor_cntrl.sv.
//
//   index = { GHR[ghrIdxBits-1:0], PC[2 +: pc_bits] }
//
// pc_bits = 0 (default) gives a pure GAg (index = GHR only).
// pc_bits > 0 gives the concatenated GHR+PC index the SV module uses
// (PC_SELECT = pc_bits, GHR_SELECT = log2(pht_size) - pc_bits).
//
// The PHT is read at fetch with the current GHR and trained when the branch
// resolves (br_cntrl_i.branch_resolved), using the GHR and i_addr at that time,
// exactly as predictor_cntrl.sv does.
class GAg(val config: GAgConfig)
  extends base_predictor(config.address_width) {

  println("Generating GAg BPU")

  private val phtSize      = config.pht_size
  private val counterWidth = config.counter_width
  private val ghrWidth     = config.ghr_size
  private val pcBits       = config.pc_bits
  private val indexBits    = log2Ceil(phtSize)
  private val ghrIdxBits   = indexBits - pcBits

  require(phtSize >= 2, "PHT size must be at least 2")
  require((1 << indexBits) == phtSize, "PHT size must be a power of two")
  require(counterWidth >= 2, "Counter width must be at least 2")
  require(ghrWidth >= 2, "GHR width must be at least 2")
  require(pcBits >= 0, "pc_bits must be >= 0")
  require(ghrIdxBits >= 1, "pc_bits must be smaller than log2(pht_size)")
  require(ghrWidth >= ghrIdxBits, "GHR must be at least as wide as the GHR part of the PHT index")
  require(config.address_width >= 2 + pcBits, "address_width too small for pc_bits")

  // Global history register, shifted when a branch resolves.
//  private val ghr = RegInit(0.U(ghrWidth.W))
//
//  // Pattern history table. All counters start at 0 (strongly not-taken),
//  // matching the cntr_table initialisation in predictor_cntrl.sv.
//  private val pht = RegInit(
//    VecInit(Seq.fill(phtSize)(0.U(counterWidth.W)))
//  )

  private val (ghr, pht) = {
    val history = RegInit(0.U(ghrWidth.W))
    val table = RegInit(
      VecInit(Seq.fill(phtSize)(0.U(counterWidth.W)))
    )
    (history, table)
  }

  private val maxCounter = ((BigInt(1) << counterWidth) - 1).U(counterWidth.W)

  // {GHR bits, PC bits}
  private def phtIndex(pc: UInt): UInt = {
    val ghrPart = ghr(ghrIdxBits - 1, 0)
    if (pcBits == 0) ghrPart
    else Cat(ghrPart, pc(2 + pcBits - 1, 2))
  }

  private val rIdx = phtIndex(io.read_addr_i)
  private val wIdx = phtIndex(io.br_cntrl_i.i_addr)

  // Prediction: MSB of the selected counter.

  io.prediction_o := pht(rIdx)(counterWidth - 1)


  // Training, driven by the execute stage.
  private val actualTaken = io.br_cntrl_i.is_taken

  when(io.br_cntrl_i.branch_resolved) {
    val cur = pht(wIdx)

    when(actualTaken) {
      when(cur =/= maxCounter) { pht(wIdx) := cur + 1.U }   // saturating increment
    }.otherwise {
      when(cur =/= 0.U) { pht(wIdx) := cur - 1.U }          // saturating decrement
    }

    ghr := Cat(ghr(ghrWidth - 2, 0), actualTaken)
  }
}