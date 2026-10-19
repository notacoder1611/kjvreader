package com.realsneed.biblereader.data

import android.content.Context

/** How the reader itself is presented: typography and page colour. */
class ReadingPrefs(context: Context) {

    private val prefs = context.getSharedPreferences("reading", Context.MODE_PRIVATE)

    var textSizeSp: Float
        get() = prefs.getFloat(KEY_SIZE, DEFAULT_SIZE)
        set(value) = prefs.edit().putFloat(KEY_SIZE, value).apply()

    var lineSpacing: Float
        get() = prefs.getFloat(KEY_SPACING, DEFAULT_SPACING)
        set(value) = prefs.edit().putFloat(KEY_SPACING, value).apply()

    var serif: Boolean
        get() = prefs.getBoolean(KEY_SERIF, false)
        set(value) = prefs.edit().putBoolean(KEY_SERIF, value).apply()

    /** Theme id from [com.realsneed.biblereader.ui.ReadingThemes.ALL]; "default" follows the system. */
    var background: String
        get() = prefs.getString(KEY_BACKGROUND, "default") ?: "default"
        set(value) = prefs.edit().putString(KEY_BACKGROUND, value).apply()

    /**
     * The words the KJV translators supplied, printed in italics as they are in
     * every printed KJV - and the reason those words have no Hebrew or Greek
     * behind them.
     */
    var showItalics: Boolean
        get() = prefs.getBoolean(KEY_ITALICS, true)
        set(value) = prefs.edit().putBoolean(KEY_ITALICS, value).apply()

    /** The translators' marginal notes. */
    var showFootnotes: Boolean
        get() = prefs.getBoolean(KEY_FOOTNOTES, true)
        set(value) = prefs.edit().putBoolean(KEY_FOOTNOTES, value).apply()

    /**
     * Underline the words an author is quoting from an earlier passage.
     *
     * Off is a perfectly reasonable choice: which parallels count as a quotation
     * is a judgement call, so the mark is offered rather than imposed.
     */
    var showQuotations: Boolean
        get() = prefs.getBoolean(KEY_QUOTATIONS, true)
        set(value) = prefs.edit().putBoolean(KEY_QUOTATIONS, value).apply()

    companion object {
        const val DEFAULT_SIZE = 18f
        const val MIN_SIZE = 14f
        const val MAX_SIZE = 32f
        const val DEFAULT_SPACING = 1.35f
        const val MIN_SPACING = 1.0f
        const val MAX_SPACING = 2.2f

        private const val KEY_SIZE = "text_size"
        private const val KEY_SPACING = "line_spacing"
        private const val KEY_SERIF = "serif"
        private const val KEY_BACKGROUND = "background"
        private const val KEY_ITALICS = "show_italics"
        private const val KEY_FOOTNOTES = "show_footnotes"
        private const val KEY_QUOTATIONS = "show_quotations"
    }
}
