package com.example.myapplication.data.remote

import kotlinx.serialization.Serializable

@Serializable
data class CommunityPick(
    val id: String = "",
    val title: String = "",
    val summary: String = "",
    val url: String = "",
    val upvotes: Int = 0,
    val date: Long = 0L
)
