package com.iron.breakfast.settings

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

/** About, Version: "0.1 (10)", the versionName then the versionCode. */
internal fun versionLabel(versionName: String, versionCode: Int): String = "$versionName ($versionCode)"

/** About, Licences: the bundled font licence, shown as plain text. */
internal const val LICENCES_ASSET = "OFL.txt"
