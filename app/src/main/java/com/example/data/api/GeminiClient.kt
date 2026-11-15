package com.example.data.api

import android.util.Log
import com.example.BuildConfig
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

// --- Gemini Request / Response models using Moshi ---

@JsonClass(generateAdapter = true)
data class TextPart(val text: String)

@JsonClass(generateAdapter = true)
data class ContentPart(val parts: List<TextPart>)

@JsonClass(generateAdapter = true)
data class GeminiRequest(val contents: List<ContentPart>)

@JsonClass(generateAdapter = true)
data class Candidate(val content: ContentPart?)

@JsonClass(generateAdapter = true)
data class GeminiResponse(val candidates: List<Candidate>?)

@JsonClass(generateAdapter = true)
data class PhotoAnalysisResult(
    val title: String,
    val description: String,
    val category: String, // Nature, People, Food, Places, Things, Other
    val tags: List<String>
)

interface GeminiApiService {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}

object GeminiClient {
    private const val TAG = "GeminiClient"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val api: GeminiApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GeminiApiService::class.java)
    }

    /**
     * Analyzes an image URL and returns rich categorized details.
     */
    suspend fun analyzePhoto(imageUrl: String): PhotoAnalysisResult? {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            Log.w(TAG, "Gemini API Key is not configured correctly.")
            return null
        }

        val prompt = """
            Analyze this photo URL and return a JSON object containing the metadata.
            Photo URL: $imageUrl
            
            Return precisely this JSON format, with fields:
            {
               "title": "A beautiful photo title (3-5 words)",
               "description": "A descriptive sentence about what is shown in the image.",
               "category": "Identify one primary category: Nature, People, Food, Places, Things, Other",
               "tags": ["tag1", "tag2", "tag3", "tag4", "tag5", "tag6"]
            }
            
            Return ONLY raw JSON. No markdown ticks like ```json ... ```, no extra words or text, just valid JSON output.
        """.trimIndent()

        val request = GeminiRequest(
            contents = listOf(ContentPart(parts = listOf(TextPart(text = prompt))))
        )

        return try {
            val response = api.generateContent(apiKey, request)
            val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (jsonText != null) {
                // Parse jsonText
                val cleanedJson = cleanJsonString(jsonText)
                val adapter = moshi.adapter(PhotoAnalysisResult::class.java)
                adapter.fromJson(cleanedJson)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error calling Gemini: ${e.message}", e)
            null
        }
    }

    /**
     * Uses Gemini to perform a semantic query matching.
     * Maps a user query (like "something refreshing to drink") to related concepts (like ["beverage", "soft drink", "cocktail", "ocean", "cold", "water"]).
     */
    suspend fun expandSearchQuery(query: String): List<String> {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return emptyList()
        }

        val prompt = """
            You are a semantic search aid for a photo gallery. The user is searching for: "$query".
            Generate a list of 5-8 related keywords, visual elements, or conceptual synonyms that might match this query.
            For example, if the user searches for "summer mood", related tags could be "sunset, beach, sunshine, ocean, ice cream, hot, vacation".
            
            Return a raw JSON list of strings only, e.g.:
            ["sea", "sand", "coast", "blue"]
            
            Return ONLY valid JSON. No markdown ticks, no commentary.
        """.trimIndent()

        val request = GeminiRequest(
            contents = listOf(ContentPart(parts = listOf(TextPart(text = prompt))))
        )

        return try {
            val response = api.generateContent(apiKey, request)
            val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (jsonText != null) {
                val cleanedJson = cleanJsonString(jsonText)
                val type = com.squareup.moshi.Types.newParameterizedType(List::class.java, String::class.java)
                val adapter = moshi.adapter<List<String>>(type)
                adapter.fromJson(cleanedJson) ?: emptyList()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error expanding search query: ${e.message}", e)
            emptyList()
        }
    }

    private fun cleanJsonString(rawJson: String): String {
        return rawJson.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
    }
}
