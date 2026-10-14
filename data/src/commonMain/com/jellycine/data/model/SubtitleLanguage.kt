package com.jellycine.data.model

/**
 * A selectable subtitle language. [code] is the 3-letter ISO 639-2 identifier the server's
 * RemoteSearch/Subtitles endpoint and subtitle providers expect; [name] is the display label.
 */
data class SubtitleLanguage(
    val code: String,
    val name: String
)

object SubtitleLanguages {
    val PRESETS: List<SubtitleLanguage> = listOf(
        SubtitleLanguage("eng", "English"),
        SubtitleLanguage("spa", "Spanish"),
        SubtitleLanguage("fre", "French"),
        SubtitleLanguage("ger", "German"),
        SubtitleLanguage("ita", "Italian"),
        SubtitleLanguage("por", "Portuguese"),
        SubtitleLanguage("rus", "Russian"),
        SubtitleLanguage("jpn", "Japanese"),
        SubtitleLanguage("kor", "Korean"),
        SubtitleLanguage("chi", "Chinese"),
        SubtitleLanguage("ara", "Arabic"),
        SubtitleLanguage("hin", "Hindi"),
        SubtitleLanguage("tam", "Tamil"),
        SubtitleLanguage("tel", "Telugu"),
        SubtitleLanguage("mal", "Malayalam"),
        SubtitleLanguage("kan", "Kannada"),
        SubtitleLanguage("ben", "Bengali"),
        SubtitleLanguage("mar", "Marathi"),
        SubtitleLanguage("tur", "Turkish"),
        SubtitleLanguage("dut", "Dutch"),
        SubtitleLanguage("pol", "Polish"),
        SubtitleLanguage("swe", "Swedish"),
        SubtitleLanguage("vie", "Vietnamese"),
        SubtitleLanguage("tha", "Thai"),
        SubtitleLanguage("ind", "Indonesian"),
        SubtitleLanguage("heb", "Hebrew"),
        SubtitleLanguage("ukr", "Ukrainian"),
        SubtitleLanguage("ces", "Czech"),
        SubtitleLanguage("ell", "Greek"),
        SubtitleLanguage("ron", "Romanian"),
        SubtitleLanguage("hun", "Hungarian"),
        SubtitleLanguage("fin", "Finnish"),
        SubtitleLanguage("dan", "Danish"),
        SubtitleLanguage("nor", "Norwegian"),
        SubtitleLanguage("per", "Persian")
    )

    val OPTIONS: List<String> = PRESETS.map { it.name }

    private val BY_NAME: Map<String, SubtitleLanguage> = PRESETS.associateBy { it.name }
    private val BY_CODE: Map<String, SubtitleLanguage> = PRESETS.associateBy { it.code.lowercase() }

    fun byName(name: String): SubtitleLanguage? = BY_NAME[name]

    fun byCode(code: String?): SubtitleLanguage? = code?.let { BY_CODE[it.lowercase()] }

    /** The language matching [preferredCode], or the first preset when there is no match. */
    fun default(preferredCode: String?): SubtitleLanguage = byCode(preferredCode) ?: PRESETS.first()
}