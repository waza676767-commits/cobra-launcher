package dev.aura.launcher

import android.app.Application
import dev.aura.launcher.core.Prefs
import dev.aura.launcher.core.Theme
import dev.aura.launcher.core.Ui
import dev.aura.launcher.data.AppRepository
import dev.aura.launcher.data.WeatherRepo
import dev.aura.launcher.gfx.Backdrop
import dev.aura.launcher.gfx.IconFactory
import dev.aura.launcher.lock.LockController

class AuraApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        Ui.init(this)
        Theme.update(this)
        AppRepository.init(this)
        IconFactory.init(this)
        WeatherRepo.init()
        LockController.init(this)
        Backdrop.ensure(this)
    }
}
