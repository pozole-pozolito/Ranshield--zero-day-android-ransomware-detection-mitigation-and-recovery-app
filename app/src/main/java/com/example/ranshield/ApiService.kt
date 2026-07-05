package com.example.ranshield

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface ApiService {
    @Multipart
    @POST("upload")
    suspend fun uploadApk(
        @Part file: MultipartBody.Part,
        @Part("packageName") packageName: RequestBody,
        @Part("appName") appName: RequestBody,
        @Part("versionName") versionName: RequestBody,
        @Part("versionCode") versionCode: RequestBody
    ): Response<ScanResponse>
}

data class ScanResponse(
    val status: String,
    @SerializedName("security_score")
    val securityScore: JsonElement?, // Use JsonElement to handle both String and Number
    @SerializedName("risk_level")
    val riskLevel: String?,
    val details: String?
) {
    /**
     * Safely extracts the score as an Int.
     * Handles cases where the server might send it as a String or a Number.
     */
    fun getScore(): Int {
        return try {
            when {
                securityScore == null || securityScore.isJsonNull -> 0
                securityScore.isJsonPrimitive -> {
                    val primitive = securityScore.asJsonPrimitive
                    when {
                        primitive.isNumber -> primitive.asInt
                        primitive.isString -> primitive.asString.toIntOrNull() ?: 0
                        else -> 0
                    }
                }
                else -> 0
            }
        } catch (e: Exception) {
            0
        }
    }
}
