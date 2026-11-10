package dev.aether.vpn.ui

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.DrawableRes
import androidx.core.content.edit
import dev.aether.vpn.R

/** Selectable launcher icons — all variants of the Aether mark; [Default] is the primary one. */
enum class AppIcon(
    val label: String,
    val alias: String,
    @param:DrawableRes val background: Int,
    @param:DrawableRes val foreground: Int,
    /** Tint for the notification small icon. */
    val accent: Int,
) {
    Default("Aether", "IconDefault", R.drawable.ic_launcher_background, R.drawable.ic_launcher_foreground, 0xFF6A4CFF.toInt()),
    Midnight("Midnight", "IconMidnight", R.drawable.ic_launcher_background_midnight, R.drawable.ic_launcher_foreground, 0xFF8F86FF.toInt()),
    Aurora("Aurora", "IconAurora", R.drawable.ic_launcher_background_aurora, R.drawable.ic_launcher_foreground, 0xFF00897B.toInt()),
    Sunset("Sunset", "IconSunset", R.drawable.ic_launcher_background_sunset, R.drawable.ic_launcher_foreground, 0xFFD63F6B.toInt()),
    Ocean("Ocean", "IconOcean", R.drawable.ic_launcher_background_ocean, R.drawable.ic_launcher_foreground, 0xFF1565C0.toInt()),
    Light("Light", "IconLight", R.drawable.ic_launcher_background_light, R.drawable.ic_launcher_foreground_light, 0xFF5B4FC8.toInt());

    // Aliases are declared relative to the namespace, not the (debug-suffixed) applicationId
    private fun component(context: Context) = ComponentName(context.packageName, "dev.aether.vpn.$alias")

    companion object {
        private const val PREFS = "app_icon"
        private const val KEY_PENDING = "pending"

        /** Notified when the selection changes (the VPN notification refreshes itself). */
        val listeners = java.util.concurrent.CopyOnWriteArrayList<(AppIcon) -> Unit>()

        private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        private fun pending(context: Context): AppIcon? =
            prefs(context).getString(KEY_PENDING, null)?.let { name -> entries.firstOrNull { it.name == name } }

        /** The icon the launcher currently has enabled. */
        fun active(context: Context): AppIcon {
            val pm = context.packageManager
            return entries.firstOrNull { icon ->
                when (pm.getComponentEnabledSetting(icon.component(context))) {
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
                    PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> icon == Default
                    else -> false
                }
            } ?: Default
        }

        /** What the user picked — may not be applied to the launcher yet. */
        fun current(context: Context): AppIcon = pending(context) ?: active(context)

        /**
         * Remembers the choice; the launcher entry is swapped later by [applyPending].
         * OEM launchers (ColorOS / OxygenOS, MIUI, One UI) often ignore a component swap made
         * while the app is in the foreground, so we wait until it goes to the background.
         */
        fun select(context: Context, icon: AppIcon) {
            prefs(context).edit { putString(KEY_PENDING, icon.name) }
            listeners.forEach { it(icon) }
        }

        /** Called when the app leaves the foreground (and on start, in case it was killed before). */
        fun applyPending(context: Context) {
            val icon = pending(context) ?: return
            if (icon != active(context)) {
                val pm = context.packageManager
                // Enable the new entry first so the app never ends up without a launcher icon
                pm.setComponentEnabledSetting(
                    icon.component(context),
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP,
                )
                entries.filter { it != icon }.forEach {
                    pm.setComponentEnabledSetting(
                        it.component(context),
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP,
                    )
                }
            }
            prefs(context).edit { remove(KEY_PENDING) }
        }
    }
}
