package com.metrowin.launcher.data

/** The three looks the first run offers, each a handful of settings put together (the user can change every one of them later). */
enum class FirstLook {
    /** As faithful to Windows Phone 8.1 as the launcher gets: flat tiles, the retro bar, the original launch animation, compact pages. */
    Wp,
    Glass,
    Droid;

    fun apply(settings: LauncherSettings): LauncherSettings = when (this) {
        Wp -> wpLook(settings)
        Glass -> settings.copy(themeStyle = ThemeStyle.Glass)
        Droid -> settings.copy(themeStyle = ThemeStyle.Droid, dynamicColor = true)
    }

    companion object {
        /** The look of Windows Phone 8.1 in one go (also the button of the Look page). */
        fun wpLook(settings: LauncherSettings) = settings.copy(
            themeStyle = ThemeStyle.Classic, actionBar = ActionBarStyle.Retro, launchAnimation = LaunchAnimation.Original, pagePadding = PagePadding.Compact,
        )
    }
}
