package ru.akkordnik.app

import android.app.Application
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/** Если приложение упадёт, сохраняем отчёт — при следующем запуске его можно скопировать и прислать. */
class AkkApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                val sw = StringWriter()
                e.printStackTrace(PrintWriter(sw))
                val ver = try { packageManager.getPackageInfo(packageName, 0).versionName } catch (_: Throwable) { "?" }
                crashFile(this).writeText(
                    "Аккордник $ver · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}\n" +
                        "Поток: ${t.name}\n\n" + sw.toString().take(12000)
                )
            } catch (_: Throwable) {}
            prev?.uncaughtException(t, e)
        }
    }

    companion object {
        fun crashFile(app: android.content.Context) = File(app.filesDir, "last_crash.txt")
    }
}
