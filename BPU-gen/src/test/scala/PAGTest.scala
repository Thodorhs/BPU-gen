import help.PAgConfig
import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import predictor_core.PAg

class PAGTest extends AnyFlatSpec with ChiselScalatestTester {

  // 16-entry PHT, 2-bit counters, 4 local histories of 4 bits each.
  // History table index = PC[3:2], so PCs 0x0, 0x4, 0x8, 0xC map to entries 0..3,
  // and PCs 0x0 / 0x10 alias to the same entry.
  private val cfg = PAgConfig(
    address_width    = 32,
    counter_width    = 2,
    pht_size         = 16,
    history_table_w  = 4,
    history_table_sz = 4,
    pc_bits          = 2,
    activeLowReset   = false
  )

  private def newDut = new PAg(cfg)

  // ---- helpers ----------------------------------------------------------

  /** Combinational read of the prediction for `pc`. */
  private def predict(dut: PAg, pc: Long): Boolean = {
    dut.io.read_addr_i.poke(pc.U)
    dut.io.prediction_o.peek().litToBoolean
  }

  /** Resolve one branch (one cycle of training). */
  private def train(dut: PAg, pc: Long, taken: Boolean): Unit = {
    dut.io.br_cntrl_i.i_addr.poke(pc.U)
    dut.io.br_cntrl_i.is_taken.poke(taken.B)
    dut.io.br_cntrl_i.branch_resolved.poke(true.B)
    dut.clock.step()
    dut.io.br_cntrl_i.branch_resolved.poke(false.B)
  }

  // ---- tests ------------------------------------------------------------

  behavior of "PAg"

  it should "predict not-taken for every PC after reset" in {
    test(newDut) { dut =>
      dut.io.br_cntrl_i.branch_resolved.poke(false.B)
      for (pc <- Seq(0x0L, 0x4L, 0x8L, 0xCL, 0x10L, 0x1234L)) {
        assert(!predict(dut, pc), f"pc=0x$pc%x should start not-taken")
      }
    }
  }

  it should "not update when branch_resolved is low" in {
    test(newDut) { dut =>
      dut.io.br_cntrl_i.i_addr.poke(0.U)
      dut.io.br_cntrl_i.is_taken.poke(true.B)
      dut.io.br_cntrl_i.branch_resolved.poke(false.B)
      dut.clock.step(20)
      assert(!predict(dut, 0x0))
    }
  }

  it should "learn an always-taken branch" in {
    test(newDut) { dut =>
      dut.io.br_cntrl_i.branch_resolved.poke(false.B)
      assert(!predict(dut, 0x0))
      // History needs to fill with 1s, then the counter at 1111 must reach >= 2.
      for (_ <- 0 until 8) train(dut, 0x0, taken = true)
      assert(predict(dut, 0x0), "should predict taken after warm-up")
    }
  }

  it should "learn an always-not-taken branch (stays not-taken)" in {
    test(newDut) { dut =>
      dut.io.br_cntrl_i.branch_resolved.poke(false.B)
      for (_ <- 0 until 8) train(dut, 0x0, taken = false)
      assert(!predict(dut, 0x0))
    }
  }

  it should "perfectly predict an alternating T/N pattern after warm-up" in {
    test(newDut) { dut =>
      dut.io.br_cntrl_i.branch_resolved.poke(false.B)
      val pc = 0x8L
      var correct = 0
      val warmup = 24
      val total  = 64
      for (i <- 0 until total) {
        val actual = (i % 2 == 0)           // T, N, T, N, ...
        val pred   = predict(dut, pc)
        if (i >= warmup && pred == actual) correct += 1
        train(dut, pc, actual)
      }
      assert(correct == total - warmup,
        s"expected 100% after warm-up, got $correct/${total - warmup}")
    }
  }

  it should "keep separate local histories for different history-table entries" in {
    test(newDut) { dut =>
      dut.io.br_cntrl_i.branch_resolved.poke(false.B)
      val pcA = 0x0L   // always taken
      val pcB = 0x4L   // always not taken
      for (_ <- 0 until 12) {
        train(dut, pcA, taken = true)
        train(dut, pcB, taken = false)
      }
      assert(predict(dut, pcA),  "A should be predicted taken")
      assert(!predict(dut, pcB), "B should be predicted not-taken")
    }
  }

  it should "alias PCs that differ only above the history-table index bits" in {
    test(newDut) { dut =>
      dut.io.br_cntrl_i.branch_resolved.poke(false.B)
      // 0x0 and 0x10 both have PC[3:2] == 0 -> same local history entry.
      for (_ <- 0 until 8) train(dut, 0x0, taken = true)
      assert(predict(dut, 0x10), "aliased PC should see the same history")
    }
  }

  it should "recover after a pattern change" in {
    test(newDut) { dut =>
      dut.io.br_cntrl_i.branch_resolved.poke(false.B)
      for (_ <- 0 until 10) train(dut, 0x0, taken = true)
      assert(predict(dut, 0x0))
      // Switch to always not-taken; history drains to 0000 and counter there is low.
      for (_ <- 0 until 12) train(dut, 0x0, taken = false)
      assert(!predict(dut, 0x0))
    }
  }
}