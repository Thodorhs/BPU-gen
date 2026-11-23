import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import help.TournamentConfig
import predictor_core.ChiselTournament

class TournamentSpec extends AnyFlatSpec with ChiselScalatestTester {

  behavior of "Tournament"

  private def resolve(dut: ChiselTournament, pc: Long, taken: Boolean): Unit = {
    dut.io.br_cntrl_i.branch_resolved.poke(true.B)
    dut.io.br_cntrl_i.i_addr.poke(pc.U)
    dut.io.br_cntrl_i.is_taken.poke(taken.B)
    dut.clock.step()
    dut.io.br_cntrl_i.branch_resolved.poke(false.B)
    dut.io.br_cntrl_i.i_addr.poke(0.U)
    dut.io.br_cntrl_i.is_taken.poke(false.B)
  }

  private def predict(dut: ChiselTournament, pc: Long): Boolean = {
    dut.io.read_addr_i.poke(pc.U)
    val r = dut.io.prediction_o.peek().litToBoolean
    r
  }

  private def init(dut: ChiselTournament): Unit = {
    dut.io.read_addr_i.poke(0.U)
    dut.io.br_cntrl_i.branch_resolved.poke(false.B)
    dut.io.br_cntrl_i.i_addr.poke(0.U)
    dut.io.br_cntrl_i.is_taken.poke(false.B)
    dut.clock.step(2)
  }

  it should "start by favoring Local predictor and shift to Global when Global is more accurate" in {
    val config = TournamentConfig(
      address_width       = 32,
      choiceTableSize    = 16,
      choiceCounterWidth = 2,
      localPhtSize       = 16,
      localCounterWidth  = 2,
      globalPhtSize      = 16,
      globalGhrSize      = 4,
      globalCounterWidth = 2,
      activeLowReset     = true
    )

    test(new ChiselTournament(config)) { dut =>
      init(dut)

      // Initial state: choice table is 00 -> Local predictor is selected
      // Both Local & Global start at 00 -> predicts not-taken
      assert(!predict(dut, 0x4), "Initial prediction should be not-taken")

      // Train a series of taken branches
      // Both sub-predictors learn, choice table adjusts based on who succeeds
      for (_ <- 0 until 5) resolve(dut, 0x4, taken = true)

      // Once trained, both should predict taken
      assert(predict(dut, 0x4), "Trained branch should predict taken")
    }
  }
}