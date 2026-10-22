package io.github.abhishekkaisar.glassos

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.os.UserManager

/**
 * Starts the home watcher (which brings Glass up over Amazon's home) after a boot. Fire OS sends BOOT_COMPLETED as one ordered broadcast to about
 * 100 receivers in turn, so a priority-0 receiver gets it about 1 m 50 s after boot. Glass listens with a high
 * priority and also for LOCKED_BOOT_COMPLETED, which arrives within seconds of boot. USER_PRESENT and
 * MY_PACKAGE_REPLACED restart only the watcher. Two alarm kicks after boot cover a watcher that failed to start.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val a = intent.action ?: return
        // Credential storage (Prefs, files/) is not readable before the user is unlocked; the later broadcasts cover it.
        if (Build.VERSION.SDK_INT >= 24 && !(context.getSystemService(Context.USER_SERVICE) as UserManager).isUserUnlocked) {
            android.util.Log.i("GlassWatch", "boot receiver: $a before unlock, skipped")
            return
        }
        WLog.init(context)
        WLog.i("boot receiver: $a at uptime ${SystemClock.elapsedRealtime()} ms")
        when (a) {
            Intent.ACTION_LOCKED_BOOT_COMPLETED, Intent.ACTION_BOOT_COMPLETED, QUICKBOOT -> {
                if (!booted) { booted = true; scheduleKicks(context) }
                // With the watcher, Glass comes up when Amazon's home does, after the boot logo has gone: started
                // earlier it covers Amazon's home before that signals Fire OS, and the logo stays up for 2 minutes.
                // Without usage access there is no watcher, so start Glass directly once boot has completed.
                val watcher = Prefs(context).replaceHome && HomeWatcher.hasAccess(context)
                if (!watcher && a != Intent.ACTION_LOCKED_BOOT_COMPLETED && !started && !HomeShare.mainResumed) {
                    started = true
                    val i = Intent(context, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                    try { context.startActivity(i) } catch (e: Exception) { }
                }
                HomeWatcher.sync(context, lookBack = true)
            }
            KICK -> HomeWatcher.sync(context, lookBack = true)
            else -> HomeWatcher.sync(context)
        }
    }

    private fun scheduleKicks(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        for ((n, delay) in listOf(20_000L, 60_000L).withIndex()) {
            val pi = PendingIntent.getBroadcast(ctx, n, Intent(ctx, BootReceiver::class.java).setAction(KICK), PendingIntent.FLAG_UPDATE_CURRENT)
            try { am.set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + delay, pi) } catch (e: Throwable) { }
        }
    }

    companion object {
        const val QUICKBOOT = "android.intent.action.QUICKBOOT_POWERON"
        const val KICK = "io.github.abhishekkaisar.glassos.KICK"
        /** Once per process: LOCKED_BOOT_COMPLETED and BOOT_COMPLETED both arrive after one boot. */
        @Volatile private var booted = false
        @Volatile private var started = false
    }
}
