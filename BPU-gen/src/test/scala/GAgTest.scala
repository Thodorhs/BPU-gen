import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import help.GAgConfig
import predictor_core.GAg

class GAgSpec extends AnyFlatSpec with ChiselScalatestTester {

  behavior of "GAg"

  // Execute stage resolves a branch at `pc` with outcome `taken`.
  private def resolve(dut: GAg, pc: Long, taken: Boolean): Unit = {
    dut.io.br_cntrl_i.branch_resolved.poke(true.B)
    dut.io.br_cntrl_i.i_addr.poke(pc.U)
    dut.io.br_cntrl_i.is_taken.poke(taken.B)
    dut.clock.step()
    dut.io.br_cntrl_i.branch_resolved.poke(false.B)
    dut.io.br_cntrl_i.i_addr.poke(0.U)
    dut.io.br_cntrl_i.is_taken.poke(false.B)
  }

  // Fetch stage asks for a prediction for `pc`.
  private def predict(dut: GAg, pc: Long): Boolean = {
    dut.io.is_branch_i.poke(true.B)
    dut.io.read_addr_i.poke(pc.U)
    val r = dut.io.prediction_o.peek().litToBoolean
    dut.io.is_branch_i.poke(false.B)
    r
  }

  private def init(dut: GAg): Unit = {
    dut.io.is_branch_i.poke(false.B)
    dut.io.read_addr_i.poke(0.U)
    dut.io.br_cntrl_i.branch_resolved.poke(false.B)
    dut.io.br_cntrl_i.i_addr.poke(0.U)
    dut.io.br_cntrl_i.is_taken.poke(false.B)
    dut.clock.step(2)
  }

  it should "index with the GHR only and train on branch_resolved" in {
    // pht 4 entries, pc_bits = 0 -> index = GHR[1:0]; counters start at 00
    val config = GAgConfig(address_width = 32, pht_size = 1024, ghr_size = 10, counter_width = 2)
    test(new GAg(config)) { dut =>
      init(dut)

      assert(!predict(dut, 0x100), "initial prediction is not-taken")

      // A write with branch_resolved low must not train anything.
      dut.io.br_cntrl_i.i_addr.poke(0x100.U)
      dut.io.br_cntrl_i.is_taken.poke(true.B)

      dut.clock.step(3)
      dut.io.br_cntrl_i.i_addr.poke(0.U)
      dut.io.br_cntrl_i.is_taken.poke(false.B)
      assert(!predict(dut, 0x100), "no training without branch_resolved")

      // 4 taken: GHR idx 0,1,3,3 -> PHT[0]=1 PHT[1]=1 PHT[3]=2, GHR=1111
      for (_ <- 0 until 4) resolve(dut, 0x100, taken = true)
      assert(predict(dut, 0x100), "PHT[3] reached 10 -> taken")

      // not taken: PHT[3] 2->1, GHR=1110 -> idx 2 (untrained) -> not taken
      resolve(dut, 0x100, taken = false)
      assert(!predict(dut, 0x100), "PHT[2] untrained -> not taken")
    }
  }

  it should "include PC bits in the index when pc_bits > 0" in {
    // pht 8 entries, pc_bits = 2 -> index = {GHR[0], PC[3:2]}
    val config = GAgConfig(address_width = 32, pht_size = 8, ghr_size = 4, counter_width = 2, pc_bits = 2)
    test(new GAg(config)) { dut =>
      init(dut)

      resolve(dut, 0x4, taken = true) // idx {0,01}=1 -> PHT[1]=1, GHR=0001
      resolve(dut, 0x4, taken = true) // idx {1,01}=5 -> PHT[5]=1, GHR=0011
      assert(!predict(dut, 0x4), "PHT[5]=01 -> not taken")
      resolve(dut, 0x4, taken = true) // idx {1,01}=5 -> PHT[5]=2, GHR=0111

      assert(predict(dut, 0x4), "PHT[5]=10 -> taken")
      assert(!predict(dut, 0x8), "different PC maps to PHT[6] -> not taken")
    }
  }
}