package com.example.ai

import android.util.Log
import com.example.player.AppTab
import com.example.player.MusicTrack
import com.example.player.TrendingPreset
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private const val TAG = "AiVoiceSearchRepo"
private const val GEMINI_MODEL = "gemini-3.8-flash"
private const val AI_TIMEOUT_MS = 60_000L

data class AiVoiceSearchResult(
    val heardText: String,
    val searchQuery: String,
    val matchedTrackIds: List<String>,
    val suggestedMoodPreset: TrendingPreset?,
    val aiNote: String
)

class AiVoiceSearchRepository {

    private val responseSchema = Schema.obj(
        properties = mapOf(
            "heardText" to Schema.string(
                description = "Exact transcription or concise summary of what the user said, hummed, or sang"
            ),
            "searchQuery" to Schema.string(
                description = "Clean, concise song title and/or artist name to search for (1 to 5 words in Persian or English, without filler commands)"
            ),
            "matchedTrackIds" to Schema.array(
                items = Schema.string(),
                description = "Up to 5 track IDs from the provided library catalog that match the spoken request, ordered best first"
            ),
            "suggestedMoodPreset" to Schema.enumeration(
                values = listOf("NONE", "HOT", "REELS_CHALLENGE", "CLUB_REMIX", "SAD_SLOW", "RAP_GANG"),
                description = "Matching Iranian music mood preset if user requested a vibe/remix/mood, otherwise NONE"
            ),
            "aiNote" to Schema.string(
                description = "Friendly one-sentence Persian explanation of what AI understood from the voice"
            )
        )
    )

    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = GEMINI_MODEL,
            generationConfig = generationConfig {
                temperature = 0.2f
                responseMimeType = "application/json"
                responseSchema = this@AiVoiceSearchRepository.responseSchema
            }
        )
    }

    suspend fun analyzeVoiceAudio(
        audioBytes: ByteArray,
        mimeType: String,
        libraryTracks: List<MusicTrack>,
        targetTab: AppTab
    ): AiVoiceSearchResult {
        require(audioBytes.isNotEmpty()) { "فایل صوتی خالی است." }
        val prompt = buildInstructionPrompt(libraryTracks, targetTab)
        return runCatching {
            val response = withTimeout(AI_TIMEOUT_MS) {
                model.generateContent(
                    content {
                        inlineData(bytes = audioBytes, mimeType = mimeType)
                        text(prompt)
                    }
                )
            }
            val rawJson = response.text?.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("پاسخی از هوش مصنوعی دریافت نشد.")
            parseStructuredResponse(rawJson)
        }.getOrElse { err ->
            if (err is CancellationException && err !is TimeoutCancellationException) {
                throw err
            }
            if (err is TimeoutCancellationException) {
                Log.e(TAG, "AI voice audio analysis timed out", err)
                throw IllegalStateException("تحلیل صوتی بیش از ۶۰ ثانیه طول کشید. لطفاً دوباره تلاش کنید.")
            }
            Log.e(TAG, "AI voice audio analysis failed", err)
            throw err
        }
    }

    suspend fun analyzeTextQuery(
        spokenText: String,
        libraryTracks: List<MusicTrack>,
        targetTab: AppTab
    ): AiVoiceSearchResult {
        val trimmed = spokenText.trim()
        require(trimmed.isNotBlank()) { "متن جستجو خالی است." }
        val prompt = buildString {
            append(buildInstructionPrompt(libraryTracks, targetTab))
            append("\nUser spoken text: \"")
            append(trimmed)
            append("\"")
        }
        return runCatching {
            val response = withTimeout(AI_TIMEOUT_MS) {
                model.generateContent(prompt)
            }
            val rawJson = response.text?.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("پاسخی از هوش مصنوعی دریافت نشد.")
            parseStructuredResponse(rawJson)
        }.getOrElse { err ->
            if (err is CancellationException && err !is TimeoutCancellationException) {
                throw err
            }
            if (err is TimeoutCancellationException) {
                Log.e(TAG, "AI text interpretation timed out", err)
                throw IllegalStateException("پاسخ‌دهی هوش مصنوعی بیش از ۶۰ ثانیه طول کشید. لطفاً دوباره تلاش کنید.")
            }
            Log.e(TAG, "AI text interpretation failed", err)
            throw err
        }
    }

    private fun buildInstructionPrompt(
        libraryTracks: List<MusicTrack>,
        targetTab: AppTab
    ): String {
        val catalogLines = libraryTracks
            .take(80)
            .joinToString(separator = "\n") { track ->
                "${track.id} | ${track.title} | ${track.artist}"
            }
        return buildString {
            appendLine("You are the voice music search assistant for an Iranian music player app called Ava Music.")
            appendLine("Listen carefully to the user's voice audio (Persian and/or English speech, sung lyrics, or music mood description).")
            appendLine("Active tab: ${if (targetTab == AppTab.ONLINE) "Online Iranian music & Instagram trends" else "User local music library"}.")
            appendLine("Tasks:")
            appendLine("1. Transcribe or summarize what the user said/sang into 'heardText' (in Persian if spoken in Persian).")
            appendLine("2. Extract the cleanest, most effective 'searchQuery' (song name, singer name, or 1-4 search keywords) without filler phrases like 'پخش کن' or 'پیدا کن'.")
            appendLine("3. If any track from the library catalog below matches (even with fuzzy spelling, partial lyrics, or Persian/Finglish pronunciation), put its exact IDs in 'matchedTrackIds'. Otherwise leave 'matchedTrackIds' empty.")
            appendLine("4. If the user describes a mood or style (trending/ترند/اینستا -> HOT, reels/چالش/ریلز -> REELS_CHALLENGE, happy/club/car/remix/شاد/ماشین/بندری/ریمیکس -> CLUB_REMIX, sad/depressed/slow/غمگین/دپ/اسلو -> SAD_SLOW, rap/hiphop/رپ/گنگ -> RAP_GANG), set 'suggestedMoodPreset', otherwise NONE.")
            appendLine("5. Write a short, helpful one-sentence Persian confirmation in 'aiNote'.")
            if (catalogLines.isNotBlank()) {
                appendLine("Library catalog (id | title | artist):")
                appendLine(catalogLines)
            }
        }
    }

    companion object {
        fun parseStructuredResponse(jsonString: String): AiVoiceSearchResult {
            val json = JSONObject(jsonString)
            val heardText = json.optString("heardText", "").trim()
            val searchQuery = json.optString("searchQuery", "").trim().ifBlank { heardText }
            val matchedArray = json.optJSONArray("matchedTrackIds")
            val matchedIds = buildList {
                if (matchedArray != null) {
                    for (i in 0 until matchedArray.length()) {
                        val id = matchedArray.optString(i, "").trim()
                        if (id.isNotBlank()) add(id)
                    }
                }
            }
            val presetRaw = json.optString("suggestedMoodPreset", "NONE").trim().uppercase()
            val moodPreset = when (presetRaw) {
                "HOT" -> TrendingPreset.HOT
                "REELS_CHALLENGE" -> TrendingPreset.REELS_CHALLENGE
                "CLUB_REMIX" -> TrendingPreset.CLUB_REMIX
                "SAD_SLOW" -> TrendingPreset.SAD_SLOW
                "RAP_GANG" -> TrendingPreset.RAP_GANG
                else -> null
            }
            val aiNote = json.optString("aiNote", "").trim()
            return AiVoiceSearchResult(
                heardText = heardText,
                searchQuery = searchQuery,
                matchedTrackIds = matchedIds,
                suggestedMoodPreset = moodPreset,
                aiNote = aiNote
            )
        }
    }
}
