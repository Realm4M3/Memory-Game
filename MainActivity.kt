package com.example.memorygame

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.app.Activity
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var tvTimer: TextView
    private lateinit var tvScore: TextView
    private lateinit var tvBossHp: TextView
    private lateinit var spinnerDifficulty: Spinner
    private lateinit var spinnerBackground: Spinner
    private lateinit var btnRestart: Button
    private lateinit var btnCreateCustomCards: Button
    private lateinit var btnPickBackgroundImage: Button
    private lateinit var mainRoot: LinearLayout

    private lateinit var adapter: CardAdapter
    private var cards = mutableListOf<CardItem>()

    private var firstSelectedIndex: Int? = null
    private var secondSelectedIndex: Int? = null
    private var isBusy = false

    private var matchedPairs = 0
    private var totalPairs = 0
    private var requiredPairs = 4
    private var useCustomCards = false

    private var score = 0
    private var gameEnded = false
    private var timeLeftInSeconds = 60
    private var timer: CountDownTimer? = null

    private var hardMissCount = 0
    private var currentDifficulty = "Easy"

    private var alienBoss: AlienBoss? = null
    private val bossHandler = Handler(Looper.getMainLooper())
    private var bossRunnable: Runnable? = null

    private val createCardLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val path = result.data?.getStringExtra("saved_card_path")
                val editIndex = result.data?.getIntExtra("edit_index", -1) ?: -1

                if (path != null) {
                    if (editIndex >= 0 && editIndex < CustomCardStore.customCardPaths.size) {
                        CustomCardStore.updateCard(editIndex, path)
                        Toast.makeText(this, "Custom card updated", Toast.LENGTH_SHORT).show()
                    } else {
                        CustomCardStore.addCard(path)
                        Toast.makeText(
                            this,
                            "Custom card saved (${CustomCardStore.customCardPaths.size}/$requiredPairs)",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    if (CustomCardStore.hasEnoughCards(requiredPairs)) {
                        useCustomCards = true
                    }

                    buildBoardOnly()
                    startAlienBossModeIfNeeded()
                }
            }
        }

    private val pickBackgroundLauncher =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) {
                try {
                    contentResolver.openInputStream(uri).use { inputStream ->
                        val drawable = Drawable.createFromStream(inputStream, uri.toString())
                        if (drawable != null) {
                            mainRoot.background = drawable
                        } else {
                            Toast.makeText(this, "Could not load image", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(this, "Error loading image", Toast.LENGTH_SHORT).show()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        recyclerView = findViewById(R.id.recyclerViewCards)
        tvTimer = findViewById(R.id.tvTimer)
        tvScore = findViewById(R.id.tvScore)
        tvBossHp = findViewById(R.id.tvBossHp)
        spinnerDifficulty = findViewById(R.id.spinnerDifficulty)
        spinnerBackground = findViewById(R.id.spinnerBackground)
        btnRestart = findViewById(R.id.btnRestart)
        btnCreateCustomCards = findViewById(R.id.btnCreateCustomCards)
        btnPickBackgroundImage = findViewById(R.id.btnPickBackgroundImage)
        mainRoot = findViewById(R.id.mainRoot)

        setupSpinners()
        setupListeners()
        resetWholeGame()
    }

    private fun setupSpinners() {
        val difficulties = listOf("Easy", "Medium", "Hard")
        val backgrounds = listOf("Beach", "Forest", "Space", "Cartoon", "Cloud")

        spinnerDifficulty.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            difficulties
        )

        spinnerBackground.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            backgrounds
        )
    }

    private fun setupListeners() {
        btnRestart.setOnClickListener {
            resetWholeGame()
        }

        btnCreateCustomCards.setOnClickListener {
            setRequiredPairsFromDifficulty()

            if (CustomCardStore.customCardPaths.isEmpty()) {
                openCardEditor(-1)
            } else {
                showCustomCardOptionsDialog()
            }
        }

        btnPickBackgroundImage.setOnClickListener {
            pickBackgroundLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }

        spinnerBackground.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                changeBuiltInBackground(position)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        spinnerDifficulty.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                setRequiredPairsFromDifficulty()

                if (useCustomCards && CustomCardStore.customCardPaths.size < requiredPairs) {
                    useCustomCards = false
                    Toast.makeText(
                        this@MainActivity,
                        "Not enough custom cards for this difficulty. Switched to default deck.",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                resetWholeGame()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun resetWholeGame() {
        timer?.cancel()
        stopAlienBossAttacks()

        gameEnded = false
        isBusy = false
        score = 0
        setRequiredPairsFromDifficulty()
        setStartingTimeForDifficulty()
        updateScoreText()
        updateTimerText()
        buildBoardOnly()
        startAlienBossModeIfNeeded()
        startTimer()
    }

    private fun setRequiredPairsFromDifficulty() {
        val difficulty = spinnerDifficulty.selectedItem?.toString() ?: "Easy"
        requiredPairs = when (difficulty) {
            "Easy" -> 4
            "Medium" -> 6
            else -> 8
        }
    }

    private fun setStartingTimeForDifficulty() {
        val difficulty = spinnerDifficulty.selectedItem?.toString() ?: "Easy"
        timeLeftInSeconds = when (difficulty) {
            "Easy" -> 60
            "Medium" -> 75
            else -> 90
        }
    }

    private fun buildBoardOnly() {
        firstSelectedIndex = null
        secondSelectedIndex = null
        isBusy = false
        matchedPairs = 0
        totalPairs = requiredPairs
        hardMissCount = 0

        val difficulty = spinnerDifficulty.selectedItem?.toString() ?: "Easy"
        currentDifficulty = difficulty

        val columns: Int
        val defaultImages: List<Int>

        when (difficulty) {
            "Easy" -> {
                defaultImages = listOf(
                    R.drawable.ic_card_1,
                    R.drawable.ic_card_2,
                    R.drawable.ic_card_3,
                    R.drawable.ic_card_4
                )
                columns = 2
            }
            "Medium" -> {
                defaultImages = listOf(
                    R.drawable.ic_card_1,
                    R.drawable.ic_card_2,
                    R.drawable.ic_card_3,
                    R.drawable.ic_card_4,
                    R.drawable.ic_card_5,
                    R.drawable.ic_card_6
                )
                columns = 3
            }
            else -> {
                defaultImages = listOf(
                    R.drawable.ic_card_1,
                    R.drawable.ic_card_2,
                    R.drawable.ic_card_3,
                    R.drawable.ic_card_4,
                    R.drawable.ic_card_5,
                    R.drawable.ic_card_6,
                    R.drawable.ic_card_7,
                    R.drawable.ic_card_8
                )
                columns = 4
            }
        }

        cards = if (useCustomCards && CustomCardStore.customCardPaths.size >= requiredPairs) {
            createCustomShuffledCards(CustomCardStore.customCardPaths.take(requiredPairs))
        } else {
            createDefaultShuffledCards(defaultImages)
        }

        adapter = CardAdapter(cards) { position ->
            handleCardClick(position)
        }

        recyclerView.layoutManager = GridLayoutManager(this, columns)
        recyclerView.adapter = adapter
    }

    private fun createDefaultShuffledCards(images: List<Int>): MutableList<CardItem> {
        val cardList = mutableListOf<CardItem>()
        var idCounter = 0

        for (image in images) {
            cardList.add(CardItem(id = idCounter++, imageRes = image))
            cardList.add(CardItem(id = idCounter++, imageRes = image))
        }

        cardList.shuffle()
        return cardList
    }

    private fun createCustomShuffledCards(paths: List<String>): MutableList<CardItem> {
        val cardList = mutableListOf<CardItem>()
        var idCounter = 0

        for (path in paths) {
            cardList.add(CardItem(id = idCounter++, imagePath = path))
            cardList.add(CardItem(id = idCounter++, imagePath = path))
        }

        cardList.shuffle()
        return cardList
    }

    private fun handleCardClick(position: Int) {
        if (isBusy || gameEnded) return

        val selectedCard = cards[position]

        if (selectedCard.isFlipped || selectedCard.isMatched || selectedCard.isBroken) return

        selectedCard.isFlipped = true
        adapter.notifyItemChanged(position)

        if (firstSelectedIndex == null) {
            firstSelectedIndex = position
        } else if (secondSelectedIndex == null && position != firstSelectedIndex) {
            secondSelectedIndex = position
            checkForMatch()
        }
    }

    private fun checkForMatch() {
        val firstIndex = firstSelectedIndex ?: return
        val secondIndex = secondSelectedIndex ?: return

        val firstCard = cards[firstIndex]
        val secondCard = cards[secondIndex]

        val isMatch = when {
            firstCard.imageRes != null && secondCard.imageRes != null ->
                firstCard.imageRes == secondCard.imageRes

            firstCard.imagePath != null && secondCard.imagePath != null ->
                firstCard.imagePath == secondCard.imagePath

            else -> false
        }

        if (isMatch) {
            firstCard.isMatched = true
            secondCard.isMatched = true
            matchedPairs++
            score += 10
            updateScoreText()
            hardMissCount = 0

            damageBoss()

            firstSelectedIndex = null
            secondSelectedIndex = null

            adapter.notifyItemChanged(firstIndex)
            adapter.notifyItemChanged(secondIndex)

            if (alienBoss?.isDefeated() == true && currentDifficulty == "Hard") {
                Toast.makeText(this, "Alien Boss Defeated!", Toast.LENGTH_SHORT).show()
            }

            if (matchedPairs == totalPairs) {
                Handler(Looper.getMainLooper()).postDelayed({
                    if (!gameEnded) {
                        buildBoardOnly()
                        startAlienBossModeIfNeeded()
                    }
                }, 400)
            }
        } else {
            isBusy = true

            Handler(Looper.getMainLooper()).postDelayed({
                firstCard.isFlipped = false
                secondCard.isFlipped = false

                adapter.notifyItemChanged(firstIndex)
                adapter.notifyItemChanged(secondIndex)

                firstSelectedIndex = null
                secondSelectedIndex = null
                isBusy = false

                if (currentDifficulty == "Hard") {
                    hardMissCount++

                    if (hardMissCount >= 7) {
                        hardMissCount = 0
                        shuffleUnmatchedCards()
                        Toast.makeText(
                            this,
                            "Too many misses! Cards shuffled.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }, 700)
        }
    }

    private fun shuffleUnmatchedCards() {
        val childCount = recyclerView.childCount

        if (childCount == 0) {
            val unmatchedCards = cards.filter { !it.isMatched && !it.isBroken }.toMutableList()
            unmatchedCards.shuffle()

            var unmatchedIndex = 0
            for (i in cards.indices) {
                if (!cards[i].isMatched && !cards[i].isBroken) {
                    cards[i] = unmatchedCards[unmatchedIndex]
                    unmatchedIndex++
                }
            }

            adapter.notifyDataSetChanged()
            return
        }

        val views = mutableListOf<View>()
        for (i in 0 until childCount) {
            recyclerView.getChildAt(i)?.let { views.add(it) }
        }

        val radius = 35f
        var finishedAnimations = 0
        val totalAnimations = views.size

        for ((index, view) in views.withIndex()) {
            val direction = if (index % 2 == 0) 1f else -1f

            val animatorX = ObjectAnimator.ofFloat(
                view,
                "translationX",
                0f,
                radius * direction,
                0f,
                -radius * direction,
                0f
            )

            val animatorY = ObjectAnimator.ofFloat(
                view,
                "translationY",
                0f,
                -radius,
                0f,
                radius,
                0f
            )

            val rotateAnim = ObjectAnimator.ofFloat(
                view,
                "rotation",
                0f,
                12f * direction,
                -12f * direction,
                0f
            )

            AnimatorSet().apply {
                playTogether(animatorX, animatorY, rotateAnim)
                duration = 600
                interpolator = AccelerateDecelerateInterpolator()

                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        finishedAnimations++

                        if (finishedAnimations == totalAnimations) {
                            val unmatchedCards =
                                cards.filter { !it.isMatched && !it.isBroken }.toMutableList()
                            unmatchedCards.shuffle()

                            var unmatchedIndex = 0
                            for (i in cards.indices) {
                                if (!cards[i].isMatched && !cards[i].isBroken) {
                                    cards[i] = unmatchedCards[unmatchedIndex]
                                    unmatchedIndex++
                                }
                            }

                            adapter.notifyDataSetChanged()

                            recyclerView.post {
                                for (j in 0 until recyclerView.childCount) {
                                    recyclerView.getChildAt(j)?.apply {
                                        alpha = 0f
                                        scaleX = 0.9f
                                        scaleY = 0.9f
                                        animate()
                                            .alpha(1f)
                                            .scaleX(1f)
                                            .scaleY(1f)
                                            .setDuration(180)
                                            .start()
                                    }
                                }
                            }
                        }
                    }
                })

                start()
            }
        }
    }

    private fun startAlienBossModeIfNeeded() {
        stopAlienBossAttacks()

        if (currentDifficulty != "Hard") {
            alienBoss = null
            tvBossHp.visibility = View.GONE
            return
        }

        alienBoss = AlienBoss(3)
        updateBossHpText()
        tvBossHp.visibility = View.VISIBLE

        bossRunnable = object : Runnable {
            override fun run() {
                if (gameEnded) return
                val boss = alienBoss ?: return

                if (!boss.isDefeated()) {
                    bossAttack()
                    bossHandler.postDelayed(this, 2500)
                }
            }
        }

        bossHandler.postDelayed(bossRunnable!!, 2500)
    }

    private fun bossAttack() {
        val availableIndexes = cards.indices.filter {
            !cards[it].isMatched && !cards[it].isBroken
        }

        if (availableIndexes.isEmpty()) return

        val randomIndex = availableIndexes.random()
        val card = cards[randomIndex]

        card.hitCount++

        if (card.hitCount >= 3) {
            card.isBroken = true
            card.isFlipped = false
            Toast.makeText(this, "Alien broke a card!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Alien hit a card! (${card.hitCount}/3)", Toast.LENGTH_SHORT).show()
        }

        adapter.notifyItemChanged(randomIndex)
    }

    private fun damageBoss() {
        if (currentDifficulty == "Hard") {
            alienBoss?.takeDamage()
            updateBossHpText()

            if (alienBoss?.isDefeated() == true) {
                stopAlienBossAttacks()
            }
        }
    }

    private fun updateBossHpText() {
        val hp = alienBoss?.hp ?: 0
        tvBossHp.text = "Boss HP: $hp"
    }

    private fun stopAlienBossAttacks() {
        bossRunnable?.let { bossHandler.removeCallbacks(it) }
        bossRunnable = null
    }

    private fun startTimer() {
        timer?.cancel()

        timer = object : CountDownTimer((timeLeftInSeconds * 1000).toLong(), 1000) {
            override fun onTick(millisUntilFinished: Long) {
                timeLeftInSeconds = (millisUntilFinished / 1000).toInt()
                updateTimerText()
            }

            override fun onFinish() {
                gameEnded = true
                stopAlienBossAttacks()
                tvTimer.text = "Time: 0"
                Toast.makeText(this@MainActivity, "Time's up!", Toast.LENGTH_SHORT).show()
            }
        }.start()
    }

    private fun updateScoreText() {
        tvScore.text = "Score: $score"
    }

    private fun updateTimerText() {
        tvTimer.text = "Time: $timeLeftInSeconds"
    }

    private fun changeBuiltInBackground(position: Int) {
        val backgroundRes = when (position) {
            0 -> R.drawable.bg_beach
            1 -> R.drawable.bg_forest
            2 -> R.drawable.bg_space
            3 -> R.drawable.cartoon_bg
            4 -> R.drawable.cloud_bg
            else -> R.drawable.bg_beach
        }

        mainRoot.setBackgroundResource(backgroundRes)
    }

    private fun showCustomCardOptionsDialog() {
        val options = arrayOf("Create New Card", "Edit Existing Card")

        AlertDialog.Builder(this)
            .setTitle("Custom Cards")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> openCardEditor(-1)
                    1 -> showEditCardDialog()
                }
            }
            .show()
    }

    private fun showEditCardDialog() {
        if (CustomCardStore.customCardPaths.isEmpty()) {
            Toast.makeText(this, "No custom cards to edit", Toast.LENGTH_SHORT).show()
            return
        }

        val items = CustomCardStore.customCardPaths.mapIndexed { index, _ ->
            "Card ${index + 1}"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Choose card to edit")
            .setItems(items) { _, which ->
                openCardEditor(which)
            }
            .show()
    }

    private fun openCardEditor(editIndex: Int) {
        val intent = Intent(this, CardEditorActivity::class.java)
        intent.putExtra("edit_index", editIndex)
        createCardLauncher.launch(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAlienBossAttacks()
        timer?.cancel()
    }
}
