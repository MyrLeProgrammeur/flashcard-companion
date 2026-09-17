package com.matheo.flashcardcompanion

import android.app.Application
import android.os.Build
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.matheo.flashcardcompanion.ai.ExplainService
import com.matheo.flashcardcompanion.data.DeckNode
import com.matheo.flashcardcompanion.data.Repository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

data class HomeState(
    val tree: List<DeckNode> = emptyList(),
    val archived: List<String> = emptyList(),
    val totalDue: Int = 0,
    val totalCards: Int = 0,
    val loading: Boolean = true,
    val error: String? = null,
)

class AppViewModel(app: Application) : AndroidViewModel(app) {

    val repo = Repository(app)
    val explainService = ExplainService(app, repo)

    private val _home = MutableStateFlow(HomeState())
    val home: StateFlow<HomeState> = _home.asStateFlow()

    private val _lang = MutableStateFlow(repo.prefs.lang)
    val lang: StateFlow<String> = _lang.asStateFlow()

    private val _theme = MutableStateFlow(repo.prefs.theme)
    val theme: StateFlow<String> = _theme.asStateFlow()

    /** Infercom reachability — colours the header pill, gates nothing else. */
    private val _aiOnline = MutableStateFlow(false)
    val aiOnline: StateFlow<Boolean> = _aiOnline.asStateFlow()

    private val _storageGranted = MutableStateFlow(hasStorageAccess())
    val storageGranted: StateFlow<Boolean> = _storageGranted.asStateFlow()

    init {
        refresh()
        pingAi()
    }

    /**
     * Reading the Syncthing folders needs all-files access on Android 11+.
     * Everything else works without it, so this gates only the library.
     */
    fun hasStorageAccess(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager()
        else true

    fun recheckStorage() {
        val granted = hasStorageAccess()
        _storageGranted.value = granted
        if (granted) refresh(force = true)
    }

    fun refresh(force: Boolean = false) {
        viewModelScope.launch {
            _home.value = _home.value.copy(loading = true, error = null)
            runCatching {
                val cards = repo.loadCards(force)
                val tree = repo.tree()
                val due = repo.dueCount()
                HomeState(
                    tree = tree,
                    archived = repo.store.getArchivedSubjects(),
                    totalDue = due,
                    totalCards = cards.size,
                    loading = false,
                )
            }.onSuccess { _home.value = it }
                .onFailure {
                    _home.value = HomeState(loading = false, error = it.message ?: "error")
                }
        }
    }

    fun pingAi() {
        viewModelScope.launch {
            _aiOnline.value = runCatching { explainService.client().ping() }.getOrDefault(false)
        }
    }

    fun setLang(value: String) {
        repo.prefs.lang = value
        _lang.value = value
    }

    fun setTheme(value: String) {
        repo.prefs.theme = value
        _theme.value = value
    }

    fun setArchived(subject: String, archived: Boolean) {
        viewModelScope.launch {
            repo.store.setArchived(subject, archived)
            refresh()
        }
    }

    fun setDeckGroup(subject: String, group: String?) {
        viewModelScope.launch {
            repo.store.setDeckGroup(subject, group)
            refresh()
        }
    }

    fun dissolveGroup(name: String) {
        viewModelScope.launch {
            repo.store.dissolveDeckGroup(name)
            refresh()
        }
    }

    fun renameGroup(from: String, to: String) {
        viewModelScope.launch {
            repo.store.renameDeckGroup(from, to)
            refresh()
        }
    }

    /** Existing folder names, for the "file into…" picker. */
    fun groupNames(): List<String> = repo.store.getDeckGroups().values.distinct().sorted()

    // ---- one-time import of the Termux backend's review history ----

    fun legacyDbCandidates(): List<File> = listOf(
        File("/data/data/com.termux/files/home/flashcard-companion/data/companion_state.db"),
        File(Environment.getExternalStorageDirectory(), "flashcard-companion/companion_state.db"),
    ).filter { it.isFile }

    fun importLegacy(path: File, onDone: (Int) -> Unit) {
        viewModelScope.launch {
            val n = runCatching { repo.importLegacyDb(path) }.getOrDefault(0)
            repo.prefs.importPrompted = true
            refresh(force = true)
            onDone(n)
        }
    }

    fun dismissImport() {
        repo.prefs.importPrompted = true
    }
}
