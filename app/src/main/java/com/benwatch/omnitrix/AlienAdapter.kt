package com.benwatch.omnitrix

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AlienAdapter(
    private val aliens: List<Alien>,
    private val onClick: (Alien) -> Unit
) : RecyclerView.Adapter<AlienAdapter.AlienViewHolder>() {

    class AlienViewHolder(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
        val image: ImageView = itemView.findViewById(R.id.imageAlien)
        val label: TextView  = itemView.findViewById(R.id.textNumber)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlienViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_alien, parent, false)
        return AlienViewHolder(view)
    }

    override fun onBindViewHolder(holder: AlienViewHolder, position: Int) {
        val alien = aliens[position]
        val ctx = holder.itemView.context
        holder.image.setImageResource(AlienRoster.resolveDrawable(ctx, alien.imageResName))
        holder.label.text = alien.displayName
        holder.itemView.setOnClickListener { onClick(alien) }
    }

    override fun getItemCount() = aliens.size
}
