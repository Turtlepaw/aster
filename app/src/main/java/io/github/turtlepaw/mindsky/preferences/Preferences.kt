package io.github.turtlepaw.mindsky.preferences

enum class DarkModePreference {
    Dim,
    Dark
}

object AppPrefs {
    val DarkTheme = BooleanPreference(
        key = "dark_theme",
        defaultValue = false,
    )

    val ShowLabelerAvatars = BooleanPreference(
        key = "show_labeler_avatars",
        defaultValue = true
    )

    val UseCatppuccinTheme = BooleanPreference(
        key = "use_catppuccin_theme",
        defaultValue = false
    )

    val CatppuccinVariant = StringPreference(
        key = "catppuccin_variant",
        defaultValue = "Mocha"
    )

    val CatppuccinAccent = StringPreference(
        key = "catppuccin_accent",
        defaultValue = "Blue"
    )

    val Font = StringPreference(
        key = "app_font",
        defaultValue = "Sora"
    )

    val FontWeight = IntPreference(
        key = "app_font_weight",
        defaultValue = 400
    )

    val FontWidth = IntPreference(
        key = "app_font_width",
        defaultValue = 100
    )

    val FontRoundness = IntPreference(
        key = "app_font_roundness",
        defaultValue = 0
    )

    val DefaultFeed = IntPreference(
        key = "default_feed",
        defaultValue = 0
    )

    val BaseUrl = StringPreference(
        key = "base_url",
        defaultValue = "https://bsky.app"
    )

    val DeveloperMode = BooleanPreference(
        key = "developer_mode",
        defaultValue = false
    )

    val ShowHandle = BooleanPreference(
        key = "show_handle",
        defaultValue = true
    )

    val ShowRichTextIcons = BooleanPreference(
        key = "show_rich_text_icons",
        defaultValue = true
    )

    val EnableBskyModeration = BooleanPreference(
        key = "enable_bsky_moderation",
        defaultValue = false
    )

    val SearchHistory = StringListPreference(
        key = "search_history",
        defaultValue = emptyList()
    )

    val UserSearchHistory = StringListPreference(
        key = "user_search_history",
        defaultValue = emptyList()
    )

    val PdsInput = StringPreference(
        key = "pds_input",
        defaultValue = ""
    )

    val TrackingEnabled = BooleanPreference(
        key = "tracking_enabled",
        defaultValue = false
    )

    val ScrobblingDid = StringPreference(
        key = "scrobbling_did",
        defaultValue = ""
    )

    val VideoSoundEnabled = BooleanPreference(
        key = "video_sound_initial",
        defaultValue = false
    )

    val ALL = listOf(
        DarkTheme,
        ShowLabelerAvatars,
        UseCatppuccinTheme,
        CatppuccinVariant,
        CatppuccinAccent,
        Font,
        FontWeight,
        FontWidth,
        DefaultFeed,
        BaseUrl,
        DeveloperMode,
        ShowHandle,
        ShowRichTextIcons,
        EnableBskyModeration,
        SearchHistory,
        UserSearchHistory,
        PdsInput,
        TrackingEnabled,
        ScrobblingDid
    )
}
