package com.jagr.fridamusic.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jagr.fridamusic.db.MusicDatabase
import com.jagr.fridamusic.db.entities.SearchHistory
import com.music.innertube.YouTube
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject

/**
 * ViewModel de prueba: NO es parte de la app final, solo sirve para validar
 * que el backend portado (Hilt + Room + innertube) funciona correctamente
 * antes de empezar a construir la UI real.
 */
@HiltViewModel
class BackendTestViewModel @Inject constructor(
    private val database: MusicDatabase,
) : ViewModel() {

    private val _log = MutableStateFlow("Ready to test. Tap a button.")
    val log: StateFlow<String> = _log.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /** Prueba 1: Hilt + Room. Escribe y lee de la base de datos real. */
    fun testDatabase() {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val testQuery = "prueba_backend_${System.currentTimeMillis()}"

                // Escritura (Room bloqueante: fuera del hilo principal)
                withContext(Dispatchers.IO) {
                    database.insert(SearchHistory(query = testQuery))
                }

                // Lectura: tomamos un snapshot del Flow con first()
                val matches = database.searchHistory(testQuery).first()
                val totalSongs = database.allSongs().first().size

                _log.value = buildString {
                    appendLine("✅ HILT: ViewModel injected correctly.")
                    appendLine("✅ ROOM (write): inserted '$testQuery' into search_history.")
                    appendLine("✅ ROOM (read): ${matches.size} match(es) for that search.")
                    appendLine("ℹ️ Total songs in local library: $totalSongs")
                    appendLine()
                    appendLine("The database is working.")
                }
            } catch (e: Exception) {
                _log.value = "❌ Error testing database:\n${e.stackTraceToString()}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /** Test 2: Real network against YouTube Music via the innertube module. */
    fun testNetwork(query: String = "Bohemian Rhapsody") {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val result = YouTube.search(query, YouTube.SearchFilter.FILTER_SONG)
                result.onSuccess { searchResult ->
                    _log.value = buildString {
                        appendLine("✅ NETWORK (innertube/YouTube Music): response received.")
                        appendLine("Results for \"$query\": ${searchResult.items.size}")
                        appendLine()
                        searchResult.items.take(5).forEach { item ->
                            appendLine("• $item")
                        }
                        appendLine()
                        appendLine("The connection to YouTube Music is working.")
                    }
                }.onFailure { e ->
                    _log.value = "❌ Error in network search:\n${e.stackTraceToString()}"
                }
            } catch (e: Exception) {
                _log.value = "❌ Exception testing network:\n${e.stackTraceToString()}"
            } finally {
                _isLoading.value = false
            }
        }
    }
}
