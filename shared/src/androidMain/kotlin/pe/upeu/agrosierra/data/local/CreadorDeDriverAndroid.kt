package pe.upeu.agrosierra.data.local

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import app.cash.sqldelight.db.SqlDriver
import pe.upeu.agrosierra.db.AgroDb

object AgroRuntime {
    lateinit var appContext: Context
}

actual fun crearDriver(): SqlDriver = AndroidSqliteDriver(
    schema = AgroDb.Schema,
    context = AgroRuntime.appContext,
    name = "agrosierra.db"
)
