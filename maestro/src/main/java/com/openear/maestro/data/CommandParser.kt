package com.openear.maestro.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.text.Normalizer
import java.util.Locale

class CommandParser(context: Context) {

  private val data: CommandsData = context.assets.open("commands.json").use {
    Json { ignoreUnknownKeys = true }.decodeFromString(
      CommandsData.serializer(),
      it.readBytes().toString(Charsets.UTF_8)
    )
  }

  sealed interface Result {
    data class Command(val keyword: String) : Result
    data class Answer(val tokens: List<String>) : Result
    data object None : Result
  }

  fun parse(transcript: String): Result {
    val normalized = normalize(transcript)
    val tokens = normalized.split("\\s+".toRegex()).filter { it.isNotBlank() }

    data.commands.forEach { cmd ->
      if (tokens.any { tok -> tok.equals(cmd.keyword, true) || cmd.aliases.any { it.equals(tok, true) } }) {
        return Result.Command(cmd.keyword)
      }
    }

    val answerTokens = tokens.mapNotNull { token -> aliasLookup(token) }
    if (answerTokens.isNotEmpty()) return Result.Answer(answerTokens)

    return Result.None
  }

  private fun aliasLookup(token: String): String? {
    data.answers.numbers.forEach { (k, alts) ->
      if (token.equals(k, true) || alts.any { it.equals(token, true) }) return k
    }
    data.answers.qualities.forEach { (k, alts) ->
      if (token.equals(k, true) || alts.any { it.equals(token, true) }) return k
    }
    data.answers.modifiers.forEach { (k, alts) ->
      if (token.equals(k, true) || alts.any { it.equals(token, true) }) return k
    }
    return null
  }

  private fun normalize(input: String): String {
    val lower = input.lowercase(Locale.US)
    val clean = Normalizer.normalize(lower, Normalizer.Form.NFD)
      .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
    return clean.replace(Regex("[^a-z0-9#\\s]"), " ").trim()
  }
}

@Serializable data class CommandsData(
  val commands: List<CommandItem>,
  val answers: Answers,
  val responses: Responses? = null
)

@Serializable data class CommandItem(
  val keyword: String,
  val type: String,
  val aliases: List<String> = emptyList()
)

@Serializable data class Answers(
  val numbers: Map<String, List<String>> = emptyMap(),
  val qualities: Map<String, List<String>> = emptyMap(),
  val modifiers: Map<String, List<String>> = emptyMap()
)

@Serializable data class Responses(val notFound: String? = null)
