package com.example.memorygame

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView

class CardAdapter(
    private val cards: List<CardItem>,
    private val onCardClick: (Int) -> Unit
) : RecyclerView.Adapter<CardAdapter.CardViewHolder>() {

    class CardViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imgCard: ImageView = view.findViewById(R.id.imgCard)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CardViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_card, parent, false)

        return CardViewHolder(view)
    }

    override fun onBindViewHolder(holder: CardViewHolder, position: Int) {

        val card = cards[position]

        if (card.isFlipped || card.isMatched) {

            when {
                card.imageRes != null -> {
                    holder.imgCard.setImageResource(card.imageRes)
                }

                card.imagePath != null -> {
                    val bitmap = BitmapFactory.decodeFile(card.imagePath)
                    holder.imgCard.setImageBitmap(bitmap)
                }

                else -> {
                    holder.imgCard.setImageResource(R.drawable.card_back)
                }
            }

            holder.imgCard.alpha = if (card.isMatched) 0.5f else 1f

        } else {

            holder.imgCard.setImageResource(R.drawable.card_back)
            holder.imgCard.alpha = 1f
        }

        holder.itemView.setOnClickListener {

            if (!card.isMatched && !card.isFlipped) {

                // smooth flip animation
                holder.itemView.animate()
                    .rotationYBy(180f)
                    .setDuration(250)
                    .start()

                onCardClick(position)
            }
        }
    }

    override fun getItemCount(): Int {
        return cards.size
    }
}