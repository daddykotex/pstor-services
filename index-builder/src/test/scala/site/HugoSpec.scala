package site

class TemplatingSpec extends munit.FunSuite {
  test("substitute.basic") {
    val obtained = Templating.substitute(
      Hugo.ContentTemplates.photo,
      Map(
        "title" -> "my title",
        "date" -> "the date",
        "path" -> "the path"
      )
    )
    val expected = s"""|title: "my title"
                       |date: "the date"
                       |path: "the path"
                       |""".stripMargin
    assertEquals(obtained, expected)
  }
}
