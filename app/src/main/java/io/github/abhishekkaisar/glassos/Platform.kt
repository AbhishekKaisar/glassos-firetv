package io.github.abhishekkaisar.glassos

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings

/**
 * Which TV GlassOS runs on, and what that changes: Fire TV (Amazon's home, which apps cannot replace, so the home
 * watcher brings GlassOS back) or Android TV / Google TV (where GlassOS can usually be the default home app, and
 * then needs no watcher at all).
 */
object Platform {
    /** Amazon's home on Fire TV. */
    const val FIRE_HOME = "com.amazon.tv.launcher"

    /** Stock homes the watcher covers on Android TV and Google TV builds that keep their own home. */
    val ANDROID_TV_HOMES = listOf(
        "com.google.android.apps.tv.launcherx",
        "com.google.android.tvlauncher",
        "com.google.android.leanbacklauncher"
    )

    @Volatile private var fire: Boolean? = null

    fun isFireTv(ctx: Context): Boolean = fire ?: (
        Build.MANUFACTURER.equals("Amazon", ignoreCase = true) ||
            ctx.packageManager.hasSystemFeature("amazon.hardware.fire_tv")
        ).also { fire = it }

    fun isGoogleTv(ctx: Context): Boolean = !isFireTv(ctx) && installed(ctx, "com.google.android.apps.tv.launcherx")

    /** "Fire TV", "Google TV" or "Android TV", for labels. */
    fun name(ctx: Context): String = when {
        isFireTv(ctx) -> "Fire TV"
        isGoogleTv(ctx) -> "Google TV"
        else -> "Android TV"
    }

    /** Installed and enabled (a stock launcher turned off with `pm disable-user` does not count). */
    fun installed(ctx: Context, pkg: String): Boolean = try {
        ctx.packageManager.getApplicationInfo(pkg, 0).enabled
    } catch (e: Throwable) { false }

    private fun homeIntent() = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)

    /** The package the system opens for Home, or null when it would ask (no default chosen yet). */
    fun defaultHome(ctx: Context): String? = try {
        val ri = ctx.packageManager.resolveActivity(homeIntent(), PackageManager.MATCH_DEFAULT_ONLY)
        val pkg = ri?.activityInfo?.packageName
        // With no default the resolver itself comes back (package "android", or a chooser activity).
        if (pkg == null || pkg == "android" || ri.activityInfo.name.endsWith("ResolverActivity")) null else pkg
    } catch (e: Throwable) { null }

    /**
     * True when Home opens GlassOS itself: no watcher and no overlay are needed. Never on Fire TV, whose Home key
     * always opens Amazon's home whatever the package manager reports.
     */
    fun isDefaultHome(ctx: Context): Boolean = !isFireTv(ctx) && defaultHome(ctx) == ctx.packageName

    /** The TV's own home: Amazon's on Fire TV, else the first stock Android TV home installed. */
    fun stockHome(ctx: Context): String? =
        if (isFireTv(ctx)) FIRE_HOME else ANDROID_TV_HOMES.firstOrNull { installed(ctx, it) }

    /** Every home activity other than GlassOS (package/class), for the watcher on Android TV. */
    fun otherHomeActivities(ctx: Context): Set<String> = try {
        ctx.packageManager.queryIntentActivities(homeIntent(), 0)
            .map { it.activityInfo }
            .filter { it.packageName != ctx.packageName && it.packageName in ANDROID_TV_HOMES }
            .map { it.packageName + "/" + it.name }
            .toSet()
    } catch (e: Throwable) { emptySet() }

    /** True for a stock home package on this platform. */
    fun isStockHomePkg(ctx: Context, pkg: String?): Boolean =
        pkg != null && (if (isFireTv(ctx)) pkg == FIRE_HOME else pkg in ANDROID_TV_HOMES)

    /** The component the system's `cmd package set-home-activity` takes. */
    fun homeComponent(ctx: Context) = ComponentName(ctx, MainActivity::class.java)

    /** The system page where the user picks the default home app, when this TV has one. */
    fun homeSettingsIntent(ctx: Context): Intent? {
        if (isFireTv(ctx)) return null
        val i = Intent(Settings.ACTION_HOME_SETTINGS)
        return if (i.resolveActivity(ctx.packageManager) != null) i else null
    }

    /** The Home intent shows the system's chooser (with Always) while no default home is set. */
    fun homeChooserAvailable(ctx: Context): Boolean = !isFireTv(ctx) && defaultHome(ctx) == null

    fun homeChooserIntent(): Intent = homeIntent()

    /** The TV's main Settings: Fire TV's stand-alone grid, else the standard Settings. */
    fun settingsIntent(ctx: Context): Intent {
        if (isFireTv(ctx)) {
            val grid = FireIntents.settings()
            if (grid.resolveActivity(ctx.packageManager) != null) return grid
        }
        return Intent(Settings.ACTION_SETTINGS)
    }

    /** The package that handles [i], for the overlay's bookkeeping. */
    fun handler(ctx: Context, i: Intent): String =
        try { ctx.packageManager.resolveActivity(i, 0)?.activityInfo?.packageName } catch (e: Throwable) { null } ?: "android"

    /** The first of [actions] that resolves to a page GlassOS may open (exported, no permission it lacks). */
    fun openable(ctx: Context, actions: List<String>): Intent? {
        for (a in actions) {
            val i = Intent(a)
            val ri = try { ctx.packageManager.resolveActivity(i, 0) } catch (e: Throwable) { null } ?: continue
            val perm = ri.activityInfo.permission
            if (ri.activityInfo.exported && (perm == null || ctx.checkCallingOrSelfPermission(perm) == PackageManager.PERMISSION_GRANTED)) return i
        }
        return null
    }

    /** Account and profile settings on Android TV / Google TV, or null when this TV has none Glass can open. */
    fun accountsIntent(ctx: Context): Intent? =
        if (isFireTv(ctx)) null else openable(ctx, listOf(Settings.ACTION_SYNC_SETTINGS, "android.settings.USER_SETTINGS"))
}
