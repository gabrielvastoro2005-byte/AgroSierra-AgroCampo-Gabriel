package pe.upeu.agrosierra.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import pe.upeu.agrosierra.domain.model.NuevaLabor
import pe.upeu.agrosierra.domain.model.TipoLabor
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalMaterial3Api::class, ExperimentalTime::class)
@Composable
fun AgroCampoApp(viewModel: AgroViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsState()
    if (state.restaurando) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
            CircularProgressIndicator()
            Text("Verificando sesión segura…", Modifier.padding(top = 12.dp))
        }
        return
    }
    if (state.sesion == null) {
        LoginScreen(state, viewModel)
        return
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(if (state.mostrarPerfil) "Mi perfil" else state.loteSeleccionado?.nombre ?: "AgroCampo") },
            navigationIcon = {
                if (state.loteSeleccionado != null || state.mostrarPerfil) {
                    TextButton(onClick = { viewModel.seleccionarLote(null); viewModel.verPerfil(false) }) { Text("‹ Atrás") }
                }
            },
            actions = {
                TextButton(onClick = viewModel::sincronizar) { Text(if (state.sincronizando) "Sincronizando…" else "Sincronizar") }
                TextButton(onClick = { viewModel.verPerfil(true) }) { Text(state.sesion?.usuario ?: "Perfil") }
            }
        )
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            if (!state.enLinea) {
                Text("Modo sin conexión · los datos mostrados provienen del almacenamiento local",
                    color = MaterialTheme.colorScheme.error, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
            }
            state.ultimaSincronizacion?.let {
                Text("Última sincronización: ${it.replace('T', ' ').take(16)}", style = MaterialTheme.typography.labelMedium)
            } ?: Text("Última sincronización: Nunca sincronizado", style = MaterialTheme.typography.labelMedium)
            state.mensaje?.let { Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 6.dp)) }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 6.dp)) }
            when {
                state.mostrarPerfil -> ProfileScreen(state, viewModel)
                state.loteSeleccionado != null -> LoteDetailScreen(state, viewModel)
                else -> LotesScreen(state, viewModel)
            }
        }
    }
    if (state.mostrarFormularioLabor && state.loteSeleccionado != null) {
        LaborFormDialog(state, viewModel)
    }
}

@Composable
private fun LoginScreen(state: AgroUiState, viewModel: AgroViewModel) {
    Column(Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center) {
        Text("AgroSierra", style = MaterialTheme.typography.headlineLarge)
        Text("AgroCampo · registro de labores de campo", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(state.usuario, viewModel::cambiarUsuario, label = { Text("Usuario") },
            singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(state.contrasena, viewModel::cambiarContrasena, label = { Text("Contraseña") },
            singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
        Button(onClick = viewModel::iniciarSesion, enabled = !state.cargando,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
            Text(if (state.cargando) "Ingresando…" else "Iniciar sesión")
        }
        Text("Usuarios de evaluación: tecnico1, jefe1 o admin", style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun LotesScreen(state: AgroUiState, viewModel: AgroViewModel) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text("Padrón de lotes", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(vertical = 12.dp))
        OutlinedTextField(state.filtro.texto, viewModel::cambiarTexto, label = { Text("Buscar por código o nombre") },
            singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            ValleyFilter(state, viewModel)
            Spacer(Modifier.width(8.dp))
            FilterChip(selected = state.filtro.soloAbiertas, onClick = { viewModel.soloAbiertas(!state.filtro.soloAbiertas) },
                label = { Text("Solo campañas abiertas") })
        }
        if (state.lotes.isEmpty()) {
            Text("No hay lotes guardados con este filtro. Sincroniza cuando tengas conexión.",
                modifier = Modifier.padding(vertical = 24.dp))
        } else state.lotes.forEach { lote ->
            val pending = state.pendientesPorLote[lote.id] ?: 0L
            Card(Modifier.fillMaxWidth().padding(vertical = 5.dp).clickable { viewModel.seleccionarLote(lote) }) {
                Column(Modifier.padding(14.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${lote.codigo} · ${lote.nombre}", style = MaterialTheme.typography.titleMedium)
                        if (pending > 0) Text("$pending pendiente${if (pending == 1L) "" else "s"}", color = MaterialTheme.colorScheme.error)
                    }
                    Text("${lote.cultivo} · ${lote.valle} · ${lote.hectareas} ha")
                    Text(if (lote.campaniaCerrada) "Campaña cerrada" else "Campaña abierta",
                        color = if (lote.campaniaCerrada) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun ValleyFilter(state: AgroUiState, viewModel: AgroViewModel) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        OutlinedButton(onClick = { expanded = true }) { Text(state.filtro.valle ?: "Todos los valles") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("Todos los valles") }, onClick = { viewModel.cambiarValle(null); expanded = false })
            state.valles.forEach { valley -> DropdownMenuItem(text = { Text(valley) },
                onClick = { viewModel.cambiarValle(valley); expanded = false }) }
        }
    }
}

@Composable
private fun LoteDetailScreen(state: AgroUiState, viewModel: AgroViewModel) {
    val lote = state.loteSeleccionado ?: return
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Card(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
            Column(Modifier.padding(14.dp)) {
                Text("${lote.codigo} · ${lote.nombre}", style = MaterialTheme.typography.titleLarge)
                Text("Cultivo ${lote.cultivo} · ${lote.valle} · ${lote.hectareas} ha")
                Text("Dosis máxima: ${lote.dosisMaximaPorHectarea} por ha")
                Text(if (lote.campaniaCerrada) "Campaña cerrada" else "Campaña abierta")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.sesion?.privilegios?.contains("LABOR_CREAR") == true && !lote.campaniaCerrada)
                Button(onClick = { viewModel.mostrarFormulario() }) { Text("Registrar labor") }
            if (state.sesion?.privilegios?.contains("CAMPANIA_CERRAR") == true && !lote.campaniaCerrada)
                OutlinedButton(onClick = viewModel::cerrarCampania) { Text("Cerrar campaña") }
        }
        Text("Labores", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
        if (state.labores.isEmpty()) Text("Este lote aún no tiene labores registradas.", modifier = Modifier.padding(8.dp))
        state.labores.forEach { labor ->
            val session = state.sesion
            val editAll = session?.privilegios?.contains("LABOR_EDITAR_TODAS") == true
            val ownRecent = session?.usuario == labor.responsable &&
                labor.creadoEnEpochMillis > 0L && Clock.System.now().toEpochMilliseconds() - labor.creadoEnEpochMillis < 86_400_000L
            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("${labor.tipo} · ${labor.fecha}", style = MaterialTheme.typography.titleMedium)
                    Text("Responsable: ${labor.responsable} · Cantidad: ${labor.cantidad}")
                    if (labor.observacion.isNotBlank()) Text(labor.observacion)
                    if (labor.pendiente) Text("Pendiente de sincronización · id local ${labor.id}", color = MaterialTheme.colorScheme.error)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (session?.privilegios?.contains("LABOR_EDITAR") == true && (editAll || ownRecent))
                            TextButton(onClick = { viewModel.mostrarFormulario(labor) }) { Text("Corregir") }
                        if (session?.privilegios?.contains("LABOR_ELIMINAR") == true)
                            TextButton(onClick = { viewModel.eliminarLabor(labor.id) }) { Text("Eliminar") }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ProfileScreen(state: AgroUiState, viewModel: AgroViewModel) {
    val session = state.sesion ?: return
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 16.dp)) {
        Text("Usuario: ${session.usuario}", style = MaterialTheme.typography.titleLarge)
        Text("Rol: ${session.rol}", modifier = Modifier.padding(top = 8.dp))
        Text("Privilegios del token", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
        session.privilegios.sorted().forEach { Text("• $it", modifier = Modifier.padding(start = 8.dp, top = 4.dp)) }
        Button(onClick = viewModel::cerrarSesion, modifier = Modifier.padding(top = 20.dp)) { Text("Cerrar sesión") }
    }
}

@Composable
private fun LaborFormDialog(state: AgroUiState, viewModel: AgroViewModel) {
    val editing = state.laborEditando
    var tipo by remember(editing?.id) { mutableStateOf(editing?.tipo ?: TipoLabor.RIEGO) }
    var fecha by remember(editing?.id) { mutableStateOf(editing?.fecha ?: Clock.System.now().toString().take(10)) }
    var cantidad by remember(editing?.id) { mutableStateOf(editing?.cantidad?.toString().orEmpty()) }
    var observacion by remember(editing?.id) { mutableStateOf(editing?.observacion.orEmpty()) }
    var expanded by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = viewModel::cerrarFormulario,
        title = { Text(if (editing == null) "Registrar labor" else "Corregir labor") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Column {
                    OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text(tipo.name) }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        TipoLabor.entries.forEach { option -> DropdownMenuItem(text = { Text(option.name) },
                            onClick = { tipo = option; expanded = false }) }
                    }
                }
                OutlinedTextField(fecha, { fecha = it }, label = { Text("Fecha (AAAA-MM-DD)") },
                    isError = state.erroresCampos.containsKey("fecha"), modifier = Modifier.fillMaxWidth())
                state.erroresCampos["fecha"]?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                OutlinedTextField(cantidad, { cantidad = it }, label = { Text("Cantidad") },
                    isError = state.erroresCampos.containsKey("cantidad"), modifier = Modifier.fillMaxWidth())
                state.erroresCampos["cantidad"]?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                OutlinedTextField(observacion, { observacion = it }, label = { Text("Observación") },
                    isError = state.erroresCampos.containsKey("observacion"), modifier = Modifier.fillMaxWidth())
                state.erroresCampos["observacion"]?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 6.dp)) }
            }
        },
        confirmButton = {
            Button(onClick = {
                val loteId = state.loteSeleccionado?.id ?: return@Button
                viewModel.guardarLabor(NuevaLabor(loteId, tipo, fecha.trim(), cantidad.toDoubleOrNull() ?: 0.0, observacion.trim()))
            }, enabled = !state.cargando) { Text(if (state.cargando) "Guardando…" else "Guardar") }
        },
        dismissButton = { TextButton(onClick = viewModel::cerrarFormulario) { Text("Cancelar") } }
    )
}
