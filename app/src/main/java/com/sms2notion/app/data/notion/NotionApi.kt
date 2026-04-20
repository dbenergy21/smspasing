package com.sms2notion.app.data.notion

import kotlinx.serialization.json.JsonObject
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface NotionApi {
    @POST("v1/pages")
    suspend fun createPage(
        @Header("Authorization") auth: String,
        @Header("Notion-Version") version: String = "2022-06-28",
        @Header("Content-Type") contentType: String = "application/json",
        @Body body: JsonObject
    ): NotionPageResponse
}
