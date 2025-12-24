package com.openear.maestro.data

class ProgressionAnswerEvaluator {

  /**
   * Compares expected progression and user answer using
   * normalized numeric tokens only (e.g. ["1","4","5","4"]).
   */
  fun isCorrect(
    expectedProgression: List<String>,
    userAnswer: List<String>
  ): Boolean {

    // Defensive normalization (cheap, safe)
    val expected = expectedProgression
      .map { it.trim() }
      .filter { it.isNotBlank() }

    val answer = userAnswer
      .map { it.trim() }
      .filter { it.isNotBlank() }

    // Must match exactly in length and order
    return expected == answer
  }

  /*
  ----------------------------------------------------------------------
  Roman numeral logic (kept for future use, currently NOT used)
  ----------------------------------------------------------------------

  fun isCorrectRoman(
      expectedProgression: List<String>,
      userAnswer: List<String>
  ): Boolean {
      val romans = toRomanSequence(userAnswer)
      return romans == expectedProgression.map { it.uppercase() }
  }

  private fun toRomanSequence(tokens: List<String>): List<String> {
      val out = mutableListOf<String>()
      var i = 0
      while (i < tokens.size) {
          val numeral = when (tokens[i]) {
              "1" -> "I"; "2" -> "II"; "3" -> "III"; "4" -> "IV"
              "5" -> "V"; "6" -> "VI"; "7" -> "VII"
              else -> null
          }
          if (numeral != null) {
              val sb = StringBuilder(numeral)
              if (i + 1 < tokens.size && isQuality(tokens[i + 1])) {
                  sb.append(" ").append(tokens[i + 1])
                  i += 1
              }
              out.add(sb.toString())
          }
          i += 1
      }
      return out
  }

  private fun isQuality(token: String): Boolean =
      token in listOf("major", "minor", "perfect", "augmented", "suspended")
  */
}
