package ar.jornadas.ui

import android.app.Application
import androidx.lifecycle.*
import ar.jornadas.Servicios
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

class ProgramaModel(app: Application, private val estado: SavedStateHandle) : AndroidViewModel(app) {
    private val servicios = Servicios.de(app)
    val mesa = servicios.mesa
    private val repo = servicios.agenda
    val charlas = repo.programa.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    // null = todavía no se leyó la base
    val hayEdicion = repo.hayEdicion.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // Pedido 43: filtros en SavedStateHandle -> sobreviven rotación y muerte del proceso.
    val dia = estado.getStateFlow("dia", 0)
    val tema = estado.getStateFlow("tema", "Todos")
    fun elegirDia(valor: Int) { estado["dia"] = valor }
    fun elegirTema(valor: String) { estado["tema"] = valor }

    val falloUltimaCarga = MutableStateFlow(false)
    val cargando = MutableStateFlow(false)
    private var peticion: Job? = null

    // Pedido 44: una carga automática por proceso.
    fun cargaInicial() {
        if (servicios.cargaInicialHecha) return
        servicios.cargaInicialHecha = true
        recargar()
    }

    // Pedido 44: si ya hay una petición en curso, un segundo toque no agrega otra.
    fun recargar() {
        if (peticion?.isActive == true) return
        peticion = viewModelScope.launch {
            cargando.value = true
            try {
                repo.sincronizar()
                falloUltimaCarga.value = false
            } catch (e: IOException) {
                falloUltimaCarga.value = true
            } catch (e: HttpException) {
                falloUltimaCarga.value = true
            } finally {
                cargando.value = false
            }
        }
    }

    fun marcar(id: String) { viewModelScope.launch { repo.cambiarFavorita(id) } }
}
