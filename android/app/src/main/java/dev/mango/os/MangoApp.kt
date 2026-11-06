package dev.mango.os

import android.app.Application
import dev.mango.os.core.Prefs
import dev.mango.os.core.Theme
import dev.mango.os.core.Ui
import dev.mango.os.data.AppRepository
import dev.mango.os.data.WeatherRepo
import dev.mango.os.gfx.Backdrop
import dev.mango.os.gfx.IconFactory
import dev.mango.os.lock.LockController

class MangoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        dev.mango.os.gfx.Glyphs.mango = try {
            androidx.core.graphics.PathParser.createPathFromPathData(getString(R.string.mango_path))
        } catch (e: Exception) { null }
        Ui.init(this)
        Theme.update(this)
        AppRepository.init(this)
        IconFactory.init(this)
        WeatherRepo.init()
        LockController.init(this)
        Backdrop.ensure(this)
    }
}
