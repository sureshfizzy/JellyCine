package com.jellycine.data.model

object SeerrCatalog {
    fun popularStudios(limit: Int = 12): List<SeerrStudio> = studios.take(limit)

    private val studios = listOf(
        SeerrStudio("2", "Disney", "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/wdrCwmRnLFJhEoH8GSfymY85KHT.png"),
        SeerrStudio("127928", "20th Century Studios", "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/h0rjX5vjW5r8yEnUBStFarjcLT4.png"),
        SeerrStudio("34", "Sony Pictures", "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/GagSvqWlyPdkFHMfQ3pNq6ix9P.png"),
        SeerrStudio("174", "Warner Bros. Pictures", "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/ky0xOc5OrhzkZ1N6KyUxacfQsCk.png"),
        SeerrStudio("33", "Universal", "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/8lvHyhjr8oUKOOy2dKXoALWKdp0.png"),
        SeerrStudio("4", "Paramount", "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/fycMZt242LVjagMByZOLUGbCvv3.png"),
        SeerrStudio("3", "Pixar", "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/1TjvGVDMYsj6JBxOAkUHpPEwLf7.png"),
        SeerrStudio("521", "Dreamworks", "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/kP7t6RwGz2AvvTkvnI1uteEwHet.png"),
        SeerrStudio("420", "Marvel Studios", "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/hUzeosd33nzE5MCNsZxCGEKTXaQ.png"),
        SeerrStudio("9993", "DC", "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/2Tc1P3Ac8M479naPp1kYT3izLS5.png"),
        SeerrStudio("41077", "A24", "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/1ZXsGaFPgrgS6ZZGS37AqD5uU12.png")
    )
}