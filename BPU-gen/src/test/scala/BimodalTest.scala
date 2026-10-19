import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import help.BimodalConfig
import predictor_core.bimodal

class BimodalTest extends AnyFlatSpec with ChiselScalatestTester {

  "Bimodal BPU" should "correctly route the selected input to the output" in {
    val config = BimodalConfig(address_width=32,counter_width=2,pht_size=1024)

    test(new bimodal(config)) { dut =>
      println("\n[TEST] Starting Bimodal Predictor Test...")

      // (PC) address 0x00000004
      val testPC = 4.U

      dut.io.is_branch_i.poke(true.B)
      dut.io.read_addr_i.poke(testPC)
      dut.io.br_cntrl_i.i_addr.poke(0.U) // 0 means "no training right now"

      // Initial state: Counter should be 00 (Strongly Not Taken)
      println("[TEST] Initial State Check...")
      dut.io.prediction_o.expect(false.B)
      dut.io.pred2id.expect(true.B)

      // --- TRAINING: PRETEND PRVIOUS WAS TAKEN EXPECT AN UPDATE IN THE COUNTER(00 -> 01) ---
      println("[TEST] Training 1st 'Taken'...")
      dut.io.br_cntrl_i.i_addr.poke(testPC)
      dut.io.br_cntrl_i.is_taken.poke(true.B)
      dut.clock.step(1) // Advance clock to write to memory
      dut.io.br_cntrl_i.i_addr.poke(0.U)
      // Counter is now 01. MSB is still 0, so prediction should still be false.
      dut.io.prediction_o.expect(false.B)

      // --- TRAINING: AGAIN TAKEN (01 -> 10) ---
      println("[TEST] Training 2nd 'Taken'...")
      dut.io.br_cntrl_i.i_addr.poke(testPC)
      dut.io.br_cntrl_i.is_taken.poke(true.B)
      dut.clock.step(1)

      dut.io.br_cntrl_i.i_addr.poke(0.U)
      // Counter is now 10 it should now predict TRUE!
      println("[TEST] Checking if prediction flipped to True...")
      dut.io.prediction_o.expect(true.B)

      // --- TRAINING: SATURATION (10 -> 11 -> 11) ---
      println("[TEST] Testing Saturation...")
      dut.io.br_cntrl_i.i_addr.poke(testPC)
      dut.clock.step(2) // Train 'Taken' twice more

      dut.io.br_cntrl_i.i_addr.poke(0.U)
      // Prediction should still be True (Counter is maxed out at 11)
      dut.io.prediction_o.expect(true.B)

      // --- TRAINING: NOT TAKEN (11 -> 10 -> 01) ---
      println("[TEST] Training 'Not Taken' to bring counter back down...")
      dut.io.br_cntrl_i.i_addr.poke(testPC)
      dut.io.br_cntrl_i.is_taken.poke(false.B)
      dut.clock.step(2) // Train 'Not Taken' twice

      dut.io.br_cntrl_i.i_addr.poke(0.U)
      // Counter went from 11 -> 10 -> 01. MSB is 0 again.
      // Prediction should flip back to FALSE
      println("[TEST] Checking if prediction flipped back to False...")
      dut.io.prediction_o.expect(false.B)

      println("[TEST] Bimodal predictor SUCCESS!\n")
    }
  }
}
