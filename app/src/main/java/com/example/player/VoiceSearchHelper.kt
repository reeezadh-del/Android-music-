package com.example.player

import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent

object VoiceSearchHelper {

    fun buildSpeechIntent(
        prompt: String,
        languageTag: String = "fa-IR"
    ): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageTag)
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
            putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
        }
    }

    fun isSpeechRecognitionAvailable(context: Context): Boolean {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        return intent.resolveActivity(context.packageManager) != null
    }

    fun extractBestQuery(data: Intent?): String? {
        val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS) ?: return null
        return results
            .asSequence()
            .map { normalizeSpokenSongQuery(it) }
            .firstOrNull { it.isNotBlank() }
    }

    fun normalizeSpokenSongQuery(raw: String): String {
        var cleaned = raw
            .replace('\u064A', '\u06CC') // Arabic Yeh -> Persian Yeh
            .replace('\u0643', '\u06A9') // Arabic Kaf -> Persian Kaf
            .replace('\u200C', ' ')
            .replace(Regex("[\\s]+"), " ")
            .trim()
            .trimEnd('؟', '?', '!', '.', '،', ',')
            .trim()

        if (cleaned.isBlank()) return ""

        val leadingCommandPrefixes = listOf(
            "برام پخش کن ",
            "پخش کن ",
            "جستجو کن ",
            "جستوجو کن ",
            "سرچ کن ",
            "پیدا کن ",
            "play "
        )
        for (prefix in leadingCommandPrefixes) {
            if (cleaned.startsWith(prefix, ignoreCase = true)) {
                val remainder = cleaned.substring(prefix.length).trim()
                if (remainder.isNotBlank()) {
                    cleaned = remainder
                }
                break
            }
        }

        val trailingCommandSuffixes = listOf(
            " رو پخش کن",
            " را پخش کن",
            " رو سرچ کن",
            " را جستجو کن",
            " رو پیدا کن"
        )
        for (suffix in trailingCommandSuffixes) {
            if (cleaned.endsWith(suffix)) {
                val remainder = cleaned.removeSuffix(suffix).trim()
                if (remainder.isNotBlank()) {
                    cleaned = remainder
                }
                break
            }
        }

        return cleaned
    }
}
