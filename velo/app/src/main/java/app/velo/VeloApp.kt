package app.velo

import android.app.Application
import app.velo.bt.Buds
import app.velo.data.Store

class VeloApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        Buds.init(this)
    }
}
