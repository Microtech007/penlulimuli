package com.hexated

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.extractors.Filesim
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class LayarKacaProvider : MainAPI() {
    override var mainUrl = "https://ww57.pencurimovie.autos"
    private var seriesUrl = "https://ww57.pencurimovie.autos/series"
    override var name = "PencuriMovie"
    override val hasMainPage = true
    override var lang = "my"
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Drama,
        TvType.Top IMBD,
    )

    override val mainPage = mainPageOf(
        "$mainUrl/movies/" to "Semua Movies",
        "$mainUrl/series/" to "Semua Series",
        "$mainUrl/episode/" to "Episode Baru",
        "$seriesUrl/latest/page/" to "Series Terbaru",
        "$seriesUrl/top-imdb/" to "Top IMDB",
        "$mainUrl/latest/page/" to "Film Upload Terbaru",
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val document = app.get(request.data + page).document
        val home = document.select("article.mega-item").mapNotNull {
            it.toSearchResult()
        }
        return newHomePageResponse(request.name, home)
    }

    private suspend fun getProperLink(url: String): String? {
        val res = app.get(url).document
        return if (res.select("title").text().contains("- Nontondrama", true)) {
            res.selectFirst("div#content a")?.attr("href")
        } else {
            url
        }
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("h1.grid-title > a")?.ownText()?.trim() ?: return null
        val href = fixUrl(this.selectFirst("a")!!.attr("href"))
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src"))
        val type =
            if (this.selectFirst("div.last-episode") == null) TvType.Movie else TvType.TvSeries
        return if (type == TvType.TvSeries) {
            val episode = this.selectFirst("div.last-episode span")?.text()?.filter { it.isDigit() }
                ?.toIntOrNull()
            newAnimeSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
                addSub(episode)
            }
        } else {
            val quality = this.select("div.quality").text().trim()
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
                addQuality(quality)
            }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/search.php?s=$query").document
        return document.select("div.search-item").mapNotNull {
            val title = it.selectFirst("a")?.attr("title") ?: ""
            val href = fixUrl(it.selectFirst("a")?.attr("href") ?: return@mapNotNull null)
            val posterUrl = fixUrlNull(it.selectFirst("img.img-thumbnail")?.attr("src"))
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
            }
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val fixUrl = getProperLink(url)
        val document = app.get(fixUrl ?: return null).document

        val title = document.selectFirst("li.last > span[itemprop=name]")?.text()?.trim().toString()
        val poster = fixUrl(document.select("img.img-thumbnail").attr("src").toString())
        val tags = document.select("div.content > div:nth-child(5) > h3 > a").map { it.text() }

        val year = Regex("\\d, (\\d+)").find(
            document.select("div.content > div:nth-child(7) > h3").text().trim()
        )?.groupValues?.get(1).toString().toIntOrNull()
        val tvType = if (document.select("div.serial-wrapper")
                .isNotEmpty()
        ) TvType.TvSeries else TvType.Movie
        val description = document.select("div.content > blockquote").text().trim()
        val trailer = document.selectFirst("div.action-player li > a.fancybox")?.attr("href")
        val rating =
            document.selectFirst("div.content > div:nth-child(6) > h3")?.text()?.toRatingInt()
        val actors =
            document.select("div.col-xs-9.content > div:nth-child(3) > h3 > a").map { it.text() }

        val recommendations = document.select("div.row.item-media").map {
            val recName = it.selectFirst("h3")?.text()?.trim().toString()
            val recHref = it.selectFirst(".content-media > a")!!.attr("href")
            val recPosterUrl =
                fixUrl(it.selectFirst(".poster-media > a > img")?.attr("src").toString())
            newTvSeriesSearchResponse(recName, recHref, TvType.TvSeries) {
                this.posterUrl = recPosterUrl
            }
        }

        return if (tvType == TvType.TvSeries) {
            val episodes = document.select("div.episode-list > a:matches(\\d+)").map {
                val href = fixUrl(it.attr("href"))
                val episode = it.text().toIntOrNull()
                val season =
                    it.attr("href").substringAfter("season-").substringBefore("-").toIntOrNull()
                Episode(
                    href,
                    "Episode $episode",
                    season,
                    episode,
                )
            }.reversed()
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.rating = rating
                addActors(actors)
                this.recommendations = recommendations
                addTrailer(trailer)
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.rating = rating
                addActors(actors)
                this.recommendations = recommendations
                addTrailer(trailer)
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        val document = app.get(data).document
        document.select("ul#loadProviders > li").map {
            fixUrl(it.select("a").attr("href"))
        }.apmap {
            loadExtractor(it.getIframe(), "https://nganunganu.sbs", subtitleCallback, callback)
        }

        return true
    }

    private suspend fun String.getIframe() : String {
        return app.get(this, referer = "$seriesUrl/").document.select("div.embed iframe").attr("src")
    }

}

open class Emturbovid : ExtractorApi() {
    override val name = "Emturbovid"
    override val mainUrl = "https://emturbovid.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val response = app.get(url, referer = referer)
        val m3u8 = Regex("[\"'](.*?master\\.m3u8.*?)[\"']").find(response.text)?.groupValues?.getOrNull(1)
        M3u8Helper.generateM3u8(
            name,
            m3u8 ?: return,
            mainUrl
        ).forEach(callback)
    }

}

class Furher : Filesim() {
    override val name = "Furher"
    override var mainUrl = "https://furher.in"
}