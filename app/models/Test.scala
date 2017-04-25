package models

object Test {
  def main(args: Array[String]): Unit = {
    for (i <- 1 to 10) {
      val j = i * 1.5
      println(j)
    }
  }
}