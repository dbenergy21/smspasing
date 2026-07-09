package com.sms2notion.app.data.notion

import kotlinx.serialization.json.JsonObject
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

interface NotionApi {
    @POST("v1/pages")
    suspend fun createPage(
        @Header("Authorization") auth: String,
        @Header("Notion-Version") version: String = "2022-06-28",
        @Header("Content-Type") contentType: String = "application/json",
        @Body body: JsonObject
    ): NotionPageResponse

    @GET("v1/databases/{id}")
    suspend fun getDatabase(
        @Header("Authorization") auth: String,
        @Header("Notion-Version") version: String = "2022-06-28",
        @Path("id") id: String
    ): JsonObject
}
