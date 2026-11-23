package predictor_core
import chisel3._
import chisel3.util._
import help.PAgConfig


class PAg(val config: PAgConfig)
  extends base_predictor(config.address_width) {

  println("Generating PAg BPU")

  private val phtSize      = config.pht_size
  private val counterWidth = config.counter_width
  private val histWidth    = config.history_table_w
  private val histSize     = config.history_table_sz
  private val pcBits       = log2Ceil(histSize)       // == config.pc_bits
  private val phtIdxBits   = log2Ceil(phtSize)

  require(phtSize >= 2, "PHT size must be at least 2")
  require((1 << phtIdxBits) == phtSize, "PHT size must be a power of two")
  require(counterWidth >= 2, "Counter width must be at least 2")
  require(histSize >= 1, "History table size must be at least 1")
  require((1 << pcBits) == histSize, "History table size must be a power of two")
  require(histWidth >= 2, "History width must be at least 2")
  require(histWidth >= phtIdxBits, "History width must be >= log2(pht_size)")
  require(config.address_width >= 2 + pcBits, "address_width too small for history table index")


  private val hTable = RegInit(VecInit(Seq.fill(histSize)(0.U(histWidth.W))))


  private val pht = Mem(phtSize, UInt(counterWidth.W))
  pht_init(pht, phtSize)

  private val maxCounter = ((BigInt(1) << counterWidth) - 1).U(counterWidth.W)


  private def hIdx(pc: UInt): UInt =
    if (pcBits == 0) 0.U else pc(2 + pcBits - 1, 2)


  private val rHist = hTable(hIdx(io.read_addr_i))
  private val rIdx  = rHist(phtIdxBits - 1, 0)

  io.prediction_o := pht(rIdx)(counterWidth - 1)


  private val actualTaken = io.br_cntrl_i.is_taken
  private val wHIdx       = hIdx(io.br_cntrl_i.i_addr)
  private val wHist       = hTable(wHIdx)
  private val wIdx        = wHist(phtIdxBits - 1, 0)

  when(io.br_cntrl_i.branch_resolved) {
    val cur = pht(wIdx)

    when(actualTaken) {
      when(cur =/= maxCounter) { pht(wIdx) := cur + 1.U }
    }.otherwise {
      when(cur =/= 0.U) { pht(wIdx) := cur - 1.U }
    }


    hTable(wHIdx) := Cat(wHist(histWidth - 2, 0), actualTaken)
  }
}