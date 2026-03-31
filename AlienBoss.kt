package com.example.memorygame

class AlienBoss(var hp: Int = 3) {
    fun takeDamage() {
        if (hp > 0) hp--
    }

    fun isDefeated(): Boolean {
        return hp <= 0
    }
}