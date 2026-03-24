package com.example.memorygame

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream

class CardEditorActivity : AppCompatActivity() {

    private lateinit var drawingView: DrawingView
    private lateinit var btnBlack: Button
    private lateinit var btnRed: Button
    private lateinit var btnBlue: Button
    private lateinit var btnClear: Button
    private lateinit var btnSaveCard: Button

    private var editIndex: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_card_editor)

        drawingView = findViewById(R.id.drawingView)
        btnBlack = findViewById(R.id.btnBlack)
        btnRed = findViewById(R.id.btnRed)
        btnBlue = findViewById(R.id.btnBlue)
        btnClear = findViewById(R.id.btnClear)
        btnSaveCard = findViewById(R.id.btnSaveCard)

        editIndex = intent.getIntExtra("edit_index", -1)

        btnBlack.setOnClickListener { drawingView.setBrushColor(Color.BLACK) }
        btnRed.setOnClickListener { drawingView.setBrushColor(Color.RED) }
        btnBlue.setOnClickListener { drawingView.setBrushColor(Color.BLUE) }
        btnClear.setOnClickListener { drawingView.clearCanvas() }

        btnSaveCard.setOnClickListener {
            val fileName = "card_${System.currentTimeMillis()}.png"
            val file = File(filesDir, fileName)

            FileOutputStream(file).use { out ->
                drawingView.getBitmap()
                    .compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
            }

            intent.putExtra("saved_card_path", file.absolutePath)
            intent.putExtra("edit_index", editIndex)
            setResult(Activity.RESULT_OK, intent)
            finish()
        }
    }
}