package com.mival.ilmiometeo.model

data class NewsItem(
    val title: String,
    val link: String,
    val imageUrl: String? = null,
    val date: String = ""
)
