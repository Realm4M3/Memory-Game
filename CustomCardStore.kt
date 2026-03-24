package com.example.memorygame

object CustomCardStore {
    val customCardPaths = mutableListOf<String>()

    fun clear() {
        customCardPaths.clear()
    }

    fun addCard(path: String) {
        customCardPaths.add(path)
    }

    fun updateCard(index: Int, path: String) {
        if (index in customCardPaths.indices) {
            customCardPaths[index] = path
        }
    }

    fun hasEnoughCards(requiredPairs: Int): Boolean {
        return customCardPaths.size >= requiredPairs
    }
}