package models

class FilesSpec extends munit.FunSuite {
  test("stripExtension.basic") {
    val obtained = File.stripExtension("00000PORTRAIT_00000_BURST20200412164204788.jpg")
    val expected = "00000PORTRAIT_00000_BURST20200412164204788"
    assertEquals(obtained, expected)
  }
}
