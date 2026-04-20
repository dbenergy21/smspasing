package com.sms2notion.app.data.notion

import kotlinx.serialization.Serializable

@Serializable
data class NotionPageResponse(
    val id: String? = null,
    val `object`: String? = null,
    val url: String? = null,
)
