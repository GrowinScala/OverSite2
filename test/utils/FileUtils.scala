package utils

import java.io._
import utils.TestGenerators._

object FileUtils {
  def generateTextFile(filename: String): File = {
    val file = new File(filename)
    val lines = genList(1, 100, genString).sample.getOrElse(Seq())
    val bw = new BufferedWriter(new FileWriter(file))
    for (line <- lines) {
      bw.write(line + '\n')
    }
    bw.close()
    file
  }
}