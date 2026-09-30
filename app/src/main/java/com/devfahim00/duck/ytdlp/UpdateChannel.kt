package com.devfahim00.duck.ytdlp

/**
 * yt-dlp release channels (see yt-dlp's README, "Update").
 *
 *  - stable: monthly releases; mostly tested but often stale when sites change.
 *  - nightly: published shortly before midnight UTC on any day the code
 *    changed. This is the channel yt-dlp itself recommends for regular use.
 *  - master: a canary build after every push; newest fixes, may regress.
 *
 * Each channel is its own GitHub repository with a plain `yt-dlp` zipapp
 * asset per release, so both "latest" and a pinned tag are CDN redirects and
 * never touch the rate-limited api.github.com endpoints.
 */
enum class UpdateChannel(
    val id: String,
    val label: String,
    val description: String,
    private val repo: String
) {
    STABLE(
        "stable", "Stable",
        "Monthly releases. Most tested, but can lag behind site changes.",
        "yt-dlp/yt-dlp"
    ),
    NIGHTLY(
        "nightly", "Nightly",
        "Daily builds. Recommended by yt-dlp for regular use.",
        "yt-dlp/yt-dlp-nightly-builds"
    ),
    MASTER(
        "master", "Master",
        "Every commit. Newest fixes, may contain regressions.",
        "yt-dlp/yt-dlp-master-builds"
    );

    /** Redirects to the newest release's zipapp asset. */
    val latestAssetUrl: String get() = "https://github.com/$repo/releases/latest/download/yt-dlp"

    /** Redirects to `.../releases/tag/<TAG>`; the tag IS the yt-dlp version. */
    val latestTagUrl: String get() = "https://github.com/$repo/releases/latest"

    /** `--update-to CHANNEL@TAG` equivalent. */
    fun assetUrlForTag(tag: String): String = "https://github.com/$repo/releases/download/$tag/yt-dlp"

    companion object {
        val DEFAULT = NIGHTLY

        fun fromId(id: String?): UpdateChannel = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}
