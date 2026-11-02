package mk.bosa.breakfast.settings

/** A page of Settings: one of the six groups, or Licences under About. The list of groups is no page (null). */
internal enum class SettingsPage(val title: String) {
    LOGGING("Logging"), DISPLAY("Display"), ACCESSIBILITY("Accessibility"), SCANNER("Scanner"), DATA("Data"), ABOUT("About"),
    LICENCES("Licences"),
}

/** The groups the Settings list shows, in this order. */
internal val SETTINGS_GROUPS: List<SettingsPage> = listOf(
    SettingsPage.LOGGING, SettingsPage.DISPLAY, SettingsPage.ACCESSIBILITY, SettingsPage.SCANNER, SettingsPage.DATA, SettingsPage.ABOUT,
)

/** Back or the arrow on a Settings page: Licences returns to About, a group page to the list (null). */
internal fun settingsPageBack(page: SettingsPage): SettingsPage? = if (page == SettingsPage.LICENCES) SettingsPage.ABOUT else null

/** About, Version: the versionName then the versionCode, as in "0.1 (13)". */
internal fun versionLabel(versionName: String, versionCode: Int): String = "$versionName ($versionCode)"

/** About, Licences, shown as plain text: the repository's NOTICE, which the build copies in as an asset, then the font's licence. */
internal val LICENCE_ASSETS = listOf("NOTICE.txt", "OFL.txt")

/** How deep [page] lies, for the way a page change moves: the list, a group's page, Licences (opened from About). */
internal fun pageDepth(page: SettingsPage?): Int = when (page) {
    null -> 0
    SettingsPage.LICENCES -> 2
    else -> 1
}
