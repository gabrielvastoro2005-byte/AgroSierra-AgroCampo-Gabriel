package pe.upeu.agrosierra.data.local

import app.cash.sqldelight.db.SqlDriver
import pe.upeu.agrosierra.db.AgroDb

expect fun crearDriver(): SqlDriver

fun crearBaseLocal(): AgroDb = AgroDb(crearDriver())
