// use an integer for version numbers
version = 17


cloudstream {
    language = "my"
    // All of these properties are optional, you can safely remove them

    // description = "MTS Provider"
    authors = listOf("Hexated")

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
     * */
    status = 1 // will be 3 if unspecified
    tvTypes = listOf(
        "AsianDrama",
        "TvSeries",
        "Movie",
    )

    iconUrl = "https://www.google.com/s2/favicons?domain=tv5.lk21official.wiki&sz=%size%"
}
