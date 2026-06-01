package com.kai.custom.data

import androidx.compose.ui.text.intl.Locale

data class LanguageOption(
    val code: String,
    val displayName: String,
    val edgeTtsVoice: String,
)

val languageOptions: List<LanguageOption> = listOf(
    LanguageOption("en", "English", "en-US-AndrewNeural"),
    LanguageOption("id", "Indonesian", "id-ID-ArdiNeural"),
    LanguageOption("es", "Spanish", "es-ES-AlvaroNeural"),
    LanguageOption("fr", "French", "fr-FR-DeniseNeural"),
    LanguageOption("de", "German", "de-DE-KatjaNeural"),
    LanguageOption("it", "Italian", "it-IT-IsabellaNeural"),
    LanguageOption("pt", "Portuguese", "pt-BR-FranciscaNeural"),
    LanguageOption("ja", "Japanese", "ja-JP-KeitaNeural"),
    LanguageOption("zh", "Chinese (Simplified)", "zh-CN-XiaoxiaoNeural"),
    LanguageOption("ko", "Korean", "ko-KR-SunHiNeural"),
    LanguageOption("ru", "Russian", "ru-RU-SvetlanaNeural"),
    LanguageOption("ar", "Arabic", "ar-SA-ZariyahNeural"),
    LanguageOption("hi", "Hindi", "hi-IN-MadhurNeural"),
    LanguageOption("nl", "Dutch", "nl-NL-MaartenNeural"),
    LanguageOption("pl", "Polish", "pl-PL-MarekNeural"),
    LanguageOption("tr", "Turkish", "tr-TR-AhmetNeural"),
    LanguageOption("vi", "Vietnamese", "vi-VN-HoaiMyNeural"),
    LanguageOption("th", "Thai", "th-TH-PremwadeeNeural"),
    LanguageOption("sv", "Swedish", "sv-SE-SofieNeural"),
    LanguageOption("da", "Danish", "da-DK-ChristelNeural"),
    LanguageOption("fi", "Finnish", "fi-FI-SelmaNeural"),
    LanguageOption("nb", "Norwegian", "nb-NO-PernilleNeural"),
    LanguageOption("cs", "Czech", "cs-CZ-AntoninNeural"),
    LanguageOption("el", "Greek", "el-GR-AthinaNeural"),
    LanguageOption("he", "Hebrew", "he-IL-AvriNeural"),
    LanguageOption("ro", "Romanian", "ro-RO-EmilNeural"),
    LanguageOption("hu", "Hungarian", "hu-HU-NoemiNeural"),
)

fun getDefaultLanguage(): String {
    val systemLang = Locale.current.language
    return if (languageOptions.any { it.code == systemLang }) systemLang else "en"
}
