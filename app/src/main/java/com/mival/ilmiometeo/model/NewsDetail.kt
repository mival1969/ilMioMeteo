package com.mival.ilmiometeo.model

data class NewsDetail(
    val title: String,
    val htmlContent: String, // Simplified to just pass HTML or plain text paragraphs
    val images: List<String>
)
