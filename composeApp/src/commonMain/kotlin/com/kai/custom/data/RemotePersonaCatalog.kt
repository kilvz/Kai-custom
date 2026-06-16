package com.kai.custom.data

import com.kai.custom.httpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class RemotePersonaEntry(
    val id: String,
    val name: String,
    val description: String = "",
)

enum class PersonaFormat(val displayName: String) {
    AUTO("Auto (match provider)"),
    CONDENSED("Condensed"),
    SYNTHESIZED("Full (synthesized)"),
    CHATGPT("ChatGPT"),
    CLAUDE("Claude"),
    GEMINI("Gemini"),
    CHARACTERAI("Character.AI"),
    ALTERNATIVE("Alternative"),
}

object PersonaIndexCache {
    val entries: List<String> = listOf(
        "adam_grant", "addison_rae", "ady_barkan", "ai_weiwei", "alan_watts",
        "alexandria_ocasio-cortez", "alicia_garza", "alix_earle", "anderson_cooper", "andy_jassy",
        "anna_wintour", "bad_bunny_(benito_antonio_martinez_ocasio)", "banksy", "benjamin_netanyahu", "bernie_sanders",
        "beyonce_(beyonce_knowles-carter)", "bill_gates", "bob_iger", "brene_brown", "brian_cornell",
        "bts", "caitlin_clark", "charles_koch", "charli_d'amelio", "christine_lagarde",
        "claudia_sheinbaum", "conan_o'brien", "confucius", "cornel_west", "corpse_husband",
        "cristiano_ronaldo", "dalai_lama_(tenzin_gyatso)", "david_attenborough", "david_dobrik", "david_zaslav",
        "donald_trump", "doug_mcmillon", "drake_(aubrey_graham)", "dream_(clay)", "dwayne_\"the_rock\"_johnson",
        "elizabeth_warren", "ellen_degeneres", "elon_musk", "emma_chamberlain", "emmanuel_macron",
        "gavin_newsom", "george_soros", "giorgia_meloni", "gordon_ramsay", "greg_abbott",
        "greta_thunberg", "gretchen_whitmer", "hakeem_jeffries", "howard_schultz", "j.d._vance",
        "jacinda_ardern", "jack_ma_(ma_yun)", "jake_paul", "jalen_hurts", "jamie_dimon",
        "janet_yellen", "jeff_bezos", "jeff_koons", "jensen_huang", "jerome_powell",
        "jimmy_fallon", "joe_rogan", "john_oliver", "john_roberts", "john_thune",
        "jordan_peterson", "judith_butler", "kanye_west_(ye)", "keir_starmer", "kendrick_lamar_(kendrick_lamar_duckworth)",
        "khaby_lame_(khabane_lame)", "kim_kardashian", "kylie_jenner", "larry_fink", "larry_page",
        "lebron_james", "leonardo_da_vinci", "lionel_messi", "logan_paul", "lula_da_silva",
        "mackenzie_scott", "malala_yousafzai", "malcolm_gladwell", "marco_rubio", "margot_robbie",
        "marina_abramovic", "mark_zuckerberg", "markiplier_(mark_fischbach)", "mary_barra", "melinda_gates",
        "michael_bloomberg", "michio_kaku", "mike_johnson", "mitzi_jonelle_tan", "mohammed_bin_salman",
        "mrbeast_(jimmy_donaldson)", "mukesh_ambani", "narendra_modi", "nassim_nicholas_taleb", "neil_degrasse_tyson",
        "ninja_(richard_tyler_blevins)", "noam_chomsky", "oprah_winfrey", "patrisse_khan-cullors", "pete_hegseth",
        "peter_singer", "pewdiepie_(felix_kjellberg)", "phish", "pokimane_(imane_anys)", "pope_francis",
        "rachel_maddow", "recep_tayyip_erdogan", "reed_hastings", "rihanna_(robyn_rihanna_fenty)", "robert_f._kennedy_jr.",
        "rupert_murdoch", "ryan_reynolds", "sabrina_carpenter", "sam_altman", "sam_harris",
        "satya_nadella", "sean_hannity", "serena_williams", "sergey_brin", "shohei_ohtani",
        "slavoj_zizek", "stephen_colbert", "steve_jobs", "steven_pinker", "sundar_pichai",
        "ta-nehisi_coates", "tarana_burke", "taylor_swift", "thomas_piketty", "tim_cook",
        "timothee_chalamet", "trevor_noah", "tucker_carlson", "ursula_von_der_leyen", "vanessa_nakate",
        "virgil_abloh", "vladimir_putin", "volodymyr_zelensky", "warren_buffett", "xi_jinping",
        "yayoi_kusama", "yuval_noah_harari", "zendaya",
    )
}

val PERSONA_INDEX_CACHE: List<RemotePersonaEntry> = PersonaIndexCache.entries.map { name ->
    val display = name.replace("-", " ").replace("_", " ")
        .split(" ").joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
    RemotePersonaEntry(id = name, name = display, description = "Community persona")
}

private val FORMAT_TO_FILE: Map<PersonaFormat, String> = mapOf(
    PersonaFormat.CONDENSED to "prompts/condensed.md",
    PersonaFormat.CHATGPT to "prompts/chatgpt.md",
    PersonaFormat.CLAUDE to "prompts/claude.md",
    PersonaFormat.GEMINI to "prompts/gemini.md",
    PersonaFormat.CHARACTERAI to "prompts/characterai.md",
    PersonaFormat.ALTERNATIVE to "prompts/alternative.md",
)

private val SERVICE_TO_FORMAT: Map<String, PersonaFormat> = mapOf(
    Service.OpenAI.id to PersonaFormat.CHATGPT,
    Service.Anthropic.id to PersonaFormat.CLAUDE,
    Service.Gemini.id to PersonaFormat.GEMINI,
)

private fun detectBestFormat(services: List<ServiceInstance>): PersonaFormat {
    for (instance in services) {
        val match = SERVICE_TO_FORMAT[instance.serviceId]
        if (match != null) return match
    }
    return PersonaFormat.CONDENSED
}

class RemotePersonaCatalog {

    companion object {
        private const val INDEX_URL = "https://raw.githubusercontent.com/kilvz/Kai-custom/main/docs/community-personas.json"
        private const val RAW_BASE = "https://raw.githubusercontent.com/kilvz/personas/main/personas"
    }

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun listPersonas(): List<RemotePersonaEntry> = try {
        val client = httpClient()
        val raw = client.get(INDEX_URL) {
            header("User-Agent", "Kai-custom/3.20.0")
        }.bodyAsText()
        client.close()
        val idx = json.decodeFromString<PersonaIndex>(raw)
        idx.personas.ifEmpty { PERSONA_INDEX_CACHE }
    } catch (_: Exception) {
        PERSONA_INDEX_CACHE
    }

    private suspend fun fetchUrl(url: String): String? = try {
        val client = httpClient()
        val raw = client.get(url) {
            header("User-Agent", "Kai-custom/3.20.0")
        }.bodyAsText()
        client.close()
        if (raw.isBlank()) null else raw
    } catch (_: Exception) {
        null
    }

    suspend fun downloadPersona(id: String, format: PersonaFormat = PersonaFormat.CONDENSED, configuredServices: List<ServiceInstance> = emptyList()): PersonaConfig? {
        return try {
            val effectiveFormat = if (format == PersonaFormat.AUTO) detectBestFormat(configuredServices) else format
            val raw = if (effectiveFormat == PersonaFormat.SYNTHESIZED) {
                fetchUrl("$RAW_BASE/$id/synthesized.md")
            } else {
                val file = FORMAT_TO_FILE[effectiveFormat]
                if (file != null) {
                    fetchUrl("$RAW_BASE/$id/$file") ?: fetchUrl("$RAW_BASE/$id/synthesized.md")
                } else {
                    fetchUrl("$RAW_BASE/$id/synthesized.md")
                }
            }
            if (raw == null || raw.isBlank()) return null
            val cleaned = raw.trim().substringAfter("---").trim().ifEmpty { raw.trim() }
            val name = id.replace("-", " ").replace("_", " ")
                .split(" ").joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
            PersonaConfig(
                id = "community_$id",
                name = name,
                description = "Community persona from kilvz/personas",
                behaviorStyle = BehaviorStyle.CUSTOM,
                languageStyle = LanguageStyle.NONE,
                characterType = CharacterType.NONE,
                defaultSoul = cleaned,
                renderMode = RenderMode.CHARACTER,
                isBuiltIn = false,
            )
        } catch (_: Exception) {
            null
        }
    }
}

@Serializable
private data class PersonaIndex(val personas: List<RemotePersonaEntry>)
