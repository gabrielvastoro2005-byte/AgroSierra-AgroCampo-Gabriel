package pe.upeu.agrosierra.di

import android.content.Context
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import pe.upeu.agrosierra.data.local.AgroLocalDataSource
import pe.upeu.agrosierra.data.local.AgroRuntime
import pe.upeu.agrosierra.data.local.AlmacenSeguro
import pe.upeu.agrosierra.data.local.AlmacenSeguroAndroid
import pe.upeu.agrosierra.data.local.crearBaseLocal
import pe.upeu.agrosierra.data.remote.AgroApi
import pe.upeu.agrosierra.data.remote.AgroRemoteDataSource
import pe.upeu.agrosierra.data.remote.EstadoRedAndroid
import pe.upeu.agrosierra.data.remote.crearClienteHttp
import pe.upeu.agrosierra.data.repository.AgroRepositorioOfflineFirst
import pe.upeu.agrosierra.data.repository.AuthRepositorioRest
import pe.upeu.agrosierra.domain.repository.AgroRepository
import pe.upeu.agrosierra.domain.repository.AuthRepository
import pe.upeu.agrosierra.domain.repository.EstadoRed
import pe.upeu.agrosierra.domain.usecase.AgroCampoUseCases
import pe.upeu.agrosierra.presentation.AgroViewModel

private val agroModule = module {
    single<AlmacenSeguro> { AlmacenSeguroAndroid(androidContext()) }
    single { crearBaseLocal() }
    single { AgroLocalDataSource(get()) }
    single { crearClienteHttp(get()) }
    single { AgroApi(get()) }
    single<AgroRemoteDataSource> { get<AgroApi>() }
    single<AuthRepository> { AuthRepositorioRest(get(), get()) }
    single<AgroRepository> { AgroRepositorioOfflineFirst(get(), get(), get()) }
    single<EstadoRed> { EstadoRedAndroid(androidContext()) }
    single { AgroCampoUseCases(get(), get()) }
    viewModel { AgroViewModel(get(), get()) }
}

fun initAgroKoin(context: Context) {
    AgroRuntime.appContext = context.applicationContext
    startKoin {
        androidContext(context)
        modules(agroModule)
    }
}
