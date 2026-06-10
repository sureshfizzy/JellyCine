package com.jellycine.data.model

enum class AwardMode { WINNERS, NOMINEES }

data class AwardTitleRef(
    val tmdbId: String,
    val mediaType: String,
    val year: Int? = null
)

data class AwardCategory(
    val qid: String,
    val label: String
)

data class AwardDefinition(
    val id: String,
    val label: String,
    val shortLabel: String,
    val categories: List<AwardCategory>
)

data class AwardRow(
    val key: String,
    val title: String,
    val categoryLabel: String,
    val categoryQid: String,
    val mode: AwardMode,
    val refs: List<AwardTitleRef>
)

data class AwardRefsState(
    val rows: List<AwardRow>,
    val minYear: Int?,
    val maxYear: Int?,
    val decades: List<Int>
)

object AwardsCatalog {
    fun byId(id: String): AwardDefinition? = awards.firstOrNull { it.id == id }

    val awards: List<AwardDefinition> = listOf(
        AwardDefinition(
            id = "oscars",
            label = "Academy Awards",
            shortLabel = "Oscars",
            categories = listOf(
                AwardCategory("Q102427", "Best Picture"),
                AwardCategory("Q103360", "Best Director"),
                AwardCategory("Q103916", "Best Actor"),
                AwardCategory("Q103618", "Best Actress"),
                AwardCategory("Q106291", "Best Supporting Actor"),
                AwardCategory("Q106301", "Best Supporting Actress"),
                AwardCategory("Q107258", "Best Adapted Screenplay"),
                AwardCategory("Q41417", "Best Original Screenplay"),
                AwardCategory("Q281939", "Best Film Editing"),
                AwardCategory("Q131520", "Best Cinematography"),
                AwardCategory("Q277751", "Best Production Design"),
                AwardCategory("Q277536", "Best Costume Design"),
                AwardCategory("Q488651", "Best Original Score"),
                AwardCategory("Q112243", "Best Original Song"),
                AwardCategory("Q830079", "Best Sound"),
                AwardCategory("Q393686", "Best Visual Effects"),
                AwardCategory("Q487136", "Best Makeup and Hairstyling"),
                AwardCategory("Q105304", "Best International Feature"),
                AwardCategory("Q106800", "Best Animated Feature"),
                AwardCategory("Q111332", "Best Documentary Feature")
            )
        ),
        AwardDefinition(
            id = "bafta",
            label = "British Academy Film Awards",
            shortLabel = "BAFTA",
            categories = listOf(
                AwardCategory("Q139184", "Best Film"),
                AwardCategory("Q2663714", "Best British Film"),
                AwardCategory("Q787131", "Best Direction"),
                AwardCategory("Q400007", "Best Actor in a Leading Role"),
                AwardCategory("Q687123", "Best Actress in a Leading Role"),
                AwardCategory("Q548389", "Best Supporting Actor"),
                AwardCategory("Q787123", "Best Supporting Actress"),
                AwardCategory("Q41375", "Best Original Screenplay"),
                AwardCategory("Q739694", "Best Adapted Screenplay"),
                AwardCategory("Q778870", "Best Cinematography"),
                AwardCategory("Q787145", "Best Editing"),
                AwardCategory("Q787098", "Best Original Music"),
                AwardCategory("Q739633", "Best Sound"),
                AwardCategory("Q787127", "Best Special Visual Effects"),
                AwardCategory("Q2925687", "Best Film Not in the English Language")
            )
        ),
        AwardDefinition(
            id = "berlin",
            label = "Berlin International Film Festival",
            shortLabel = "Berlin",
            categories = listOf(
                AwardCategory("Q154590", "Golden Bear"),
                AwardCategory("Q664212", "Silver Bear – Grand Jury Prize"),
                AwardCategory("Q321207", "Silver Bear – Jury Prize"),
                AwardCategory("Q706031", "Silver Bear – Best Director"),
                AwardCategory("Q819973", "Silver Bear – Best Actor"),
                AwardCategory("Q182836", "Teddy Award"),
                AwardCategory("Q28799939", "Berlinale Documentary Award")
            )
        ),
        AwardDefinition(
            id = "cannes",
            label = "Cannes Film Festival",
            shortLabel = "Cannes",
            categories = listOf(
                AwardCategory("Q179808", "Palme d'Or"),
                AwardCategory("Q844804", "Grand Prix"),
                AwardCategory("Q510175", "Best Director"),
                AwardCategory("Q840286", "Best Actress"),
                AwardCategory("Q586140", "Best Actor")
            )
        ),
        AwardDefinition(
            id = "emmys",
            label = "Primetime Emmy Awards",
            shortLabel = "Emmys",
            categories = listOf(
                AwardCategory("Q20714679", "Outstanding Limited Series"),
                AwardCategory("Q989453", "Lead Actor – Limited Series or Movie"),
                AwardCategory("Q989447", "Lead Actress – Limited Series or Movie"),
                AwardCategory("Q945887", "Supporting Actor – Limited Series or Movie"),
                AwardCategory("Q989449", "Supporting Actress – Limited Series or Movie"),
                AwardCategory("Q3403229", "Directing – Limited Series or Movie"),
                AwardCategory("Q2604800", "Guest Actor – Drama Series"),
                AwardCategory("Q17009259", "Music Composition – Limited Series or Movie")
            )
        ),
        AwardDefinition(
            id = "golden_globes",
            label = "Golden Globe Awards",
            shortLabel = "Golden Globes",
            categories = listOf(
                AwardCategory("Q1011509", "Best Motion Picture – Drama"),
                AwardCategory("Q670282", "Best Motion Picture – Musical or Comedy"),
                AwardCategory("Q593098", "Best Actor – Drama"),
                AwardCategory("Q463085", "Best Actress – Drama"),
                AwardCategory("Q181883", "Best Actor – Musical or Comedy"),
                AwardCategory("Q822907", "Best Supporting Actress – Motion Picture"),
                AwardCategory("Q586356", "Best Director"),
                AwardCategory("Q849124", "Best Screenplay"),
                AwardCategory("Q1422140", "Best Original Score"),
                AwardCategory("Q387380", "Best Non-English Language Film"),
                AwardCategory("Q1255198", "Best Television Series – Drama"),
                AwardCategory("Q1257501", "Best Actress – Television Drama")
            )
        ),
        AwardDefinition(
            id = "independent_spirit",
            label = "Film Independent Spirit Awards",
            shortLabel = "Independent Spirit",
            categories = listOf(
                AwardCategory("Q2295041", "Best Director"),
                AwardCategory("Q2544851", "Best Female Lead"),
                AwardCategory("Q2544859", "Best Male Lead"),
                AwardCategory("Q2294705", "Best Supporting Female"),
                AwardCategory("Q2294693", "Best Supporting Male"),
                AwardCategory("Q1170507", "Best Screenplay"),
                AwardCategory("Q1171956", "Best First Screenplay"),
                AwardCategory("Q1170500", "Best First Feature"),
                AwardCategory("Q1170493", "Best Cinematography"),
                AwardCategory("Q2295011", "Best International Film"),
                AwardCategory("Q3910543", "John Cassavetes Award")
            )
        ),
        AwardDefinition(
            id = "saturn",
            label = "Saturn Awards",
            shortLabel = "Saturn",
            categories = listOf(
                AwardCategory("Q1131772", "Best Science Fiction Film"),
                AwardCategory("Q956597", "Best Fantasy Film"),
                AwardCategory("Q115705", "Best Horror Film"),
                AwardCategory("Q115737", "Best Action or Adventure Film"),
                AwardCategory("Q16294258", "Best Thriller Film"),
                AwardCategory("Q1231643", "Best Animated Film"),
                AwardCategory("Q1267697", "Best International Film"),
                AwardCategory("Q1265702", "Best Director"),
                AwardCategory("Q1259362", "Best Actor"),
                AwardCategory("Q1413741", "Best Actress"),
                AwardCategory("Q1258647", "Best Supporting Actor"),
                AwardCategory("Q1257399", "Best Supporting Actress"),
                AwardCategory("Q981030", "Best Writing"),
                AwardCategory("Q2226481", "Best Production Design"),
                AwardCategory("Q7426776", "Best Editing"),
                AwardCategory("Q971008", "Best Costume"),
                AwardCategory("Q2116650", "Best Make-up"),
                AwardCategory("Q8555", "Best Special Effects"),
                AwardCategory("Q1757366", "Best Music")
            )
        ),
        AwardDefinition(
            id = "sundance",
            label = "Sundance Film Festival",
            shortLabel = "Sundance",
            categories = listOf(
                AwardCategory("Q3774974", "U.S. Dramatic Grand Jury Prize"),
                AwardCategory("Q2366088", "Grand Jury Prize – Documentary"),
                AwardCategory("Q969394", "World Cinema Grand Jury Prize"),
                AwardCategory("Q1419872", "U.S. Dramatic Audience Award"),
                AwardCategory("Q2366084", "U.S. Directing Award")
            )
        ),
        AwardDefinition(
            id = "toronto",
            label = "Toronto International Film Festival",
            shortLabel = "Toronto",
            categories = listOf(
                AwardCategory("Q39087364", "People's Choice Award"),
                AwardCategory("Q16034468", "Best Canadian Film")
            )
        ),
        AwardDefinition(
            id = "venice",
            label = "Venice Film Festival",
            shortLabel = "Venice",
            categories = listOf(
                AwardCategory("Q209459", "Golden Lion"),
                AwardCategory("Q944480", "Grand Jury Prize"),
                AwardCategory("Q2089923", "Volpi Cup – Best Actor"),
                AwardCategory("Q2089918", "Volpi Cup – Best Actress"),
                AwardCategory("Q2504862", "Queer Lion")
            )
        )
    )
}