package io.github.abhishekkaisar.glassos

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings

/**
 * The permissions GlassOS needs that only ADB can grant on Fire TV, how to check each, and the shell commands that
 * grant it. Every feature checks its own permission and does without it (see the README).
 */
object Grants {
    class Grant(val title: String, val why: String, val isGranted: (Context) -> Boolean, val commands: (Context) -> List<String>)

    private fun pkg(ctx: Context) = ctx.packageName

    val ALL = listOf(
        Grant("Usage access", "Comes back after the Home button", { HomeWatcher.hasAccess(it) }) { c ->
            listOf("appops set ${pkg(c)} GET_USAGE_STATS allow")
        },
        Grant("Display over other apps", "Shows the home at once after Home", { canDrawOverlays(it) }) { c ->
            listOf("appops set ${pkg(c)} SYSTEM_ALERT_WINDOW allow")
        },
        // The secure setting works on every Fire OS version (`cmd notification allow_listener` needs API 26+). It is
        // appended to, never replaced, so other listeners stay enabled.
        Grant("Notification access", "Now Playing and Do Not Disturb", { listenerEnabled(it) }) { listOf("LISTENERS") },
        Grant("Device admin", "Sleep and the sleep timer", { Power.adminActive(it) }) { c ->
            listOf("dpm set-active-admin ${Power.admin(c).flattenToString()}")
        },
        Grant("Storage", "Your profile photo and own backgrounds", { storage(it) }) { c ->
            listOf("pm grant ${pkg(c)} ${Manifest.permission.READ_EXTERNAL_STORAGE}")
        }
    )

    fun canDrawOverlays(ctx: Context) = Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(ctx)

    fun storage(ctx: Context) = Build.VERSION.SDK_INT < 23 ||
        ctx.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    fun listenerEnabled(ctx: Context): Boolean = try {
        val v = Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners") ?: ""
        v.split(':').any { it == MediaListener.component(ctx).flattenToString() || it == MediaListener.component(ctx).flattenToShortString() }
    } catch (e: Throwable) { false }

    fun allGranted(ctx: Context) = ALL.all { it.isGranted(ctx) }

    /** Result of one grant run: per grant, whether it now holds, and the output of anything that failed. */
    class Result(val ok: Map<Grant, Boolean>, val log: String)

    /** Runs every missing grant over [adb] and checks each afterwards. Call off the UI thread. */
    fun run(ctx: Context, adb: AdbClient): Result {
        val log = StringBuilder()
        for (g in ALL) {
            if (g.isGranted(ctx)) continue
            for (cmd in g.commands(ctx)) {
                val real = if (cmd == "LISTENERS") listenerCommand(ctx, adb) else cmd
                val out = try { adb.shell(real) } catch (e: Throwable) { "error: ${e.message}" }
                WLog.i("grant: $real -> ${out.trim().take(200)}")
                if (out.isNotBlank() && !out.contains("Success", true)) log.append(g.title).append(": ").append(out.trim().take(160)).append('\n')
            }
        }
        // Grants land asynchronously on some builds; give them a moment.
        Thread.sleep(400)
        HomeWatcher.sync(ctx)
        return Result(ALL.associateWith { it.isGranted(ctx) }, log.toString())
    }

    private fun listenerCommand(ctx: Context, adb: AdbClient): String {
        val comp = MediaListener.component(ctx).flattenToString()
        val cur = try { adb.shell("settings get secure enabled_notification_listeners").trim() } catch (e: Throwable) { "" }
        val list = cur.split(':').filter { it.isNotBlank() && it != "null" && it != comp }
        return "settings put secure enabled_notification_listeners " + (list + comp).joinToString(":")
    }

    /** The same commands for a computer, for the README and the setup screen. */
    fun manualCommands(ctx: Context): List<String> = listOf(
        "adb shell appops set ${pkg(ctx)} GET_USAGE_STATS allow",
        "adb shell appops set ${pkg(ctx)} SYSTEM_ALERT_WINDOW allow",
        "adb shell settings put secure enabled_notification_listeners ${MediaListener.component(ctx).flattenToString()}",
        "adb shell dpm set-active-admin ${Power.admin(ctx).flattenToString()}",
        "adb shell pm grant ${pkg(ctx)} android.permission.READ_EXTERNAL_STORAGE"
    )
}
