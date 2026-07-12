package com.benwatch.omnitrix

import android.content.Context

data class Alien(
    val slot: Int,
    val displayName: String,
    val imageResName: String,   // maps to res/drawable/alien_N.jpg
    val soundResName: String    // maps to res/raw/alien_N.mp3  (optional)
)

object AlienRoster {

    val ALIENS: List<Alien> = listOf(
        Alien(1,  "GREY MATTER",   "alien_1", "alien_1"),
        Alien(2,  "FOUR ARMS",     "alien_2", "alien_2"),
        Alien(3,  "UPGRADE",       "alien_3", "alien_3"),
        Alien(4,  "DIAMONDHEAD",   "alien_4", "alien_4"),
        Alien(5,  "STINKFLY",      "alien_5", "alien_5"),
        Alien(6,  "XLR8",          "alien_6", "alien_6"),
        Alien(7,  "HEATBLAST",     "alien_7", "alien_7"),
        // slots 8-10 keep placeholder until you add alien_8/9/10.jpg
        Alien(8,  "WILDMUTT",      "alien_8", "alien_8"),
        Alien(9,  "GHOSTFREAK",    "alien_9", "alien_9"),
        Alien(10, "RIPJAWS",       "alien_10","alien_10")
    )

    fun resolveDrawable(context: Context, name: String): Int {
        val id = context.resources.getIdentifier(name, "drawable", context.packageName)
        return if (id != 0) id else R.drawable.ic_alien_placeholder
    }

    fun resolveSound(context: Context, name: String): Int =
        context.resources.getIdentifier(name, "raw", context.packageName)
}
