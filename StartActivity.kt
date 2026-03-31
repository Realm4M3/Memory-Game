package com.example.memorygame

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class StartActivity : AppCompatActivity() {

    private lateinit var btnStartGame: Button
    private lateinit var btnExitGame: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_start)

        btnStartGame = findViewById(R.id.btnStartGame)
        btnExitGame = findViewById(R.id.btnExitGame)

        btnStartGame.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        }

        btnExitGame.setOnClickListener {
            finishAffinity()
        }
    }
}