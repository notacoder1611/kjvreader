package com.realsneed.biblereader.ui

import android.content.Context
import androidx.core.content.ContextCompat
import com.realsneed.biblereader.R

/** A page colour scheme: the paper, plus the text colours that suit it. */
data class ReadingTheme(
    val id: String,
    val background: Int,
    val text: Int,
    val number: Int,
    val note: Int,
    val dark: Boolean
)

/**
 * The soft reading backgrounds.
 *
 * \"default\" is not a fixed palette - it reads the app's own theme-aware colours,
 * so it keeps following the system light/dark setting. The rest are deliberate,
 * gentle papers that stay comfortable for long readings.
 */
object ReadingThemes {

    const val DEFAULT = "default"

    /** In the order the settings sheet shows them. */
    val ALL = listOf(DEFAULT, "paper", "sepia", "grey", "night")

    private val PAPER = ReadingTheme(
        "paper", 0xFFF7F3E8.toInt(), 0xFF2B2B2B.toInt(), 0xFF9C8578.toInt(), 0xFF8E3B4E.toInt(), false
    )
    private val SEPIA = ReadingTheme(
        "sepia", 0xFFF2E7D0.toInt(), 0xFF3B3125.toInt(), 0xFF9A7B4F.toInt(), 0xFF8A3B2E.toInt(), false
    )
    private val GREY = ReadingTheme(
        "grey", 0xFFE9E9EC.toInt(), 0xFF202024.toInt(), 0xFF8A8A90.toInt(), 0xFF8E3B4E.toInt(), false
    )
    private val NIGHT = ReadingTheme(
        "night", 0xFF121212.toInt(), 0xFFE4E4E4.toInt(), 0xFFBCAAA4.toInt(), 0xFFD08D9A.toInt(), true
    )

    fun resolve(context: Context, id: String, systemDark: Boolean): ReadingTheme = when (id) {
        "paper" -> PAPER
        "sepia" -> SEPIA
        "grey" -> GREY
        "night" -> NIGHT
        else -> ReadingTheme(
            id = DEFAULT,
            background = ContextCompat.getColor(context, R.color.window_bg),
            text = ContextCompat.getColor(context, R.color.text_primary),
            number = ContextCompat.getColor(context, R.color.verse_number),
            note = ContextCompat.getColor(context, R.color.section_title),
            dark = systemDark
        )
    }

    fun labelRes(id: String): Int = when (id) {
        "paper" -> R.string.theme_paper
        "sepia" -> R.string.theme_sepia
        "grey" -> R.string.theme_grey
        "night" -> R.string.theme_night
        else -> R.string.theme_default
    }
}
