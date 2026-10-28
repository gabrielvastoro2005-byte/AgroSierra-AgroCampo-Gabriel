package pe.upeu.agrosierra

import android.app.Application
import pe.upeu.agrosierra.di.initAgroKoin

class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initAgroKoin(this)
    }
}
