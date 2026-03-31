package com.example.memorygame

data class CardItem(
    val id: Int,
    val imageRes: Int? = null,
    val imagePath: String? = null,
    var isFlipped: Boolean = false,
    var isMatched: Boolean = false,
    var hitCount: Int = 0,
    var isBroken: Boolean = false
)
