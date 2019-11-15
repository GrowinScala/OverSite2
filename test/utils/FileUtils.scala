package utils

import java.io._
import java.io.File
import java.nio.file.{ Files, Path, Paths }
import java.security.{ DigestInputStream, MessageDigest }

import utils.TestGenerators._

object FileUtils {
  def generateTextFile(filename: String): File = {
    val file = new File(filename)
    val lines = genList(1, 4, genString).sample.getOrElse(Seq())
    val bw = new BufferedWriter(new FileWriter(file))
    for (line <- lines) {
      bw.write(line + '\n')
    }
    bw.close()
    file
  }

  def createDirectory(path: String): Path = {
    Files.createDirectory(Paths.get(path))
  }

  def deleteRecursively(file: File): Unit = {
    if (file.isDirectory) {
      file.listFiles.foreach(deleteRecursively)
    }
    if (file.exists && !file.delete) {
      throw new Exception(s"Unable to delete ${file.getAbsolutePath}")
    }
  }

  // Compute a hash of a file
  def computeHash(path: String): String = {
    val buffer = new Array[Byte](8192)
    val md5 = MessageDigest.getInstance("MD5")

    val dis = new DigestInputStream(new FileInputStream(new File(path)), md5)
    try { while (dis.read(buffer) != -1) {} } finally { dis.close() }

    md5.digest.map("%02x".format(_)).mkString
  }
}