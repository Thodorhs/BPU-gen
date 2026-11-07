package predictor_core

import chisel3._
import chisel3.util.experimental.loadMemoryFromFileInline
import java.io.{File, PrintWriter}

// Gives a Chisel Mem known starting values without a reset:
// writes a hex file with `size` copies of `value` and tells firtool to emit
//   initial $readmemh("<absolute path>", <mem>);
object pht_init {
  def apply[T <: Data](mem: MemBase[T], size: Int, value: BigInt = 0): Unit = {
    val dir  = new File("pht_init"); dir.mkdirs()
    val file = new File(dir, s"pht_init_$size.hex").getAbsoluteFile
    val pw   = new PrintWriter(file)
    try { (0 until size).foreach(_ => pw.println(value.toString(16))) }
    finally pw.close()
    // forward slashes: backslashes would be read as escape characters in Verilog strings
    loadMemoryFromFileInline(mem, file.getPath.replace('\\', '/'))
  }
}