package com.jellycine.data.model

object SeerrCatalog {
    fun popularStudios(limit: Int = 12): List<SeerrCatalogItem> = studios.take(limit)

    fun popularNetworks(): List<SeerrCatalogItem> = networks

    private val studios = listOf(
        SeerrCatalogItem("2", "Disney", "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/wdrCwmRnLFJhEoH8GSfymY85KHT.png"),
        SeerrCatalogItem("127928", "20th Century Studios", "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/h0rjX5vjW5r8yEnUBStFarjcLT4.png"),
        SeerrCatalogItem("34", "Sony Pictures", "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/GagSvqWlyPdkFHMfQ3pNq6ix9P.png"),
        SeerrCatalogItem("174", "Warner Bros. Pictures", "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/ky0xOc5OrhzkZ1N6KyUxacfQsCk.png"),
        SeerrCatalogItem("33", "Universal", "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/8lvHyhjr8oUKOOy2dKXoALWKdp0.png"),
        SeerrCatalogItem("4", "Paramount", "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/fycMZt242LVjagMByZOLUGbCvv3.png"),
        SeerrCatalogItem("3", "Pixar", "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/1TjvGVDMYsj6JBxOAkUHpPEwLf7.png"),
        SeerrCatalogItem("521", "Dreamworks", "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/kP7t6RwGz2AvvTkvnI1uteEwHet.png"),
        SeerrCatalogItem("420", "Marvel Studios", "file:///android_asset/logos/marvel_studios.svg"),
        SeerrCatalogItem("9993", "DC", coloredTmdbLogo("2Tc1P3Ac8M479naPp1kYT3izLS5.png", "0078F0")),
        SeerrCatalogItem("41077", "A24", "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/1ZXsGaFPgrgS6ZZGS37AqD5uU12.png")
    )

    private val networks = listOf(
        SeerrCatalogItem("213", "Netflix", coloredTmdbLogo("wwemzKWzjKYJFfCeiB57q3r4Bcm.png", "E50914")),
        SeerrCatalogItem("2739", "Disney+", "file:///android_asset/logos/disney_plus.svg"),
        SeerrCatalogItem("1024", "Prime Video", coloredTmdbLogo("ifhbNuuVnlwYy5oXA5VIb2YR8AZ.png", "00A8E1")),
        SeerrCatalogItem("2552", "Apple TV+", "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/4KAy34EHvRM25Ih8wb82AuGU7zJ.png"),
        SeerrCatalogItem("453", "Hulu", coloredTmdbLogo("pqUTCleNUiTLAVlelGxUgWn1ELh.png", "1CE783")),
        SeerrCatalogItem("49", "HBO", "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/tuomPhY2UtuPTqqFnKMVHvSb724.png"),
        SeerrCatalogItem("4353", "Discovery+", "file:///android_asset/logos/discovery_plus.svg"),
        SeerrCatalogItem("2", "ABC", "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/ndAvF4JLsliGreX87jAc9GdjmJY.png"),
        SeerrCatalogItem("19", "FOX", "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/1DSpHrWyOORkL9N2QHX7Adt31mQ.png"),
        SeerrCatalogItem("359", "Cinemax", "file:///android_asset/logos/cinemax.svg"),
        SeerrCatalogItem("174", "AMC", "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/pmvRmATOCaDykE6JrVoeYxlFHw3.png"),
        SeerrCatalogItem("67", "Showtime", coloredTmdbLogo("Allse9kbjiP6ExaQrnSpIhkurEi.png", "D0021B")),
        SeerrCatalogItem("318", "Starz", "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/8GJjw3HHsAJYwIWKIPBPfqMxlEa.png"),
        SeerrCatalogItem("71", "The CW", "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/ge9hzeaU7nMtQ4PjkFlc68dGAJ9.png"),
        SeerrCatalogItem("6", "NBC", "file:///android_asset/logos/nbc.svg"),
        SeerrCatalogItem("16", "CBS", "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/nm8d7P7MJNiBLdgIzUK0gkuEA4r.png"),
        SeerrCatalogItem("4330", "Paramount+", coloredTmdbLogo("fi83B1oztoS47xxcemFdPMhIzK.png", "0064FF")),
        SeerrCatalogItem("4", "BBC One", "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/mVn7xESaTNmjBUyUtGNvDQd3CT1.png"),
        SeerrCatalogItem("56", "Cartoon Network", "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/c5OC6oVCg6QP4eqzW6XIq17CQjI.png"),
        SeerrCatalogItem("80", "Adult Swim", "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/9AKyspxVzywuaMuZ1Bvilu8sXly.png"),
        SeerrCatalogItem("13", "Nickelodeon", coloredTmdbLogo("ikZXxg6GnwpzqiZbRPhJGaZapqB.png", "FF6700")),
        SeerrCatalogItem("3353", "Peacock", "file:///android_asset/logos/peacock.svg")
    )

    private fun coloredTmdbLogo(path: String, color: String): String =
        "https://image.tmdb.org/t/p/w780_filter(duotone,$color,$color)/$path"
}
