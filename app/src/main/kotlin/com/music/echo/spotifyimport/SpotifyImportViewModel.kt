/*
 * EchoMusic (2026)
 * © Chartreux Westia — github.com/koiverse
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package echo.music.iad1tya.spotifyimport

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import echo.music.iad1tya.spotify.SpotifyAuth
import echo.music.iad1tya.spotify.SpotifyAuthCoordinator
import echo.music.iad1tya.utils.reportException
import javax.inject.Inject
import kotlin.jvm.Volatile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@HiltViewModel
class SpotifyImportViewModel
@Inject
constructor(
  private val repository: SpotifyImportRepository,
) : ViewModel() {
  private val _uiState = MutableStateFlow(SpotifyImportUiState(isLoading = true))
  val uiState: StateFlow<SpotifyImportUiState> = _uiState.asStateFlow()

  @Volatile private var sources: List<SpotifyImportSource> = emptyList()
  private val sourcesMutex = Mutex()
  private var importJob: Job? = null

  private suspend fun updateSources(
    updateAction:
      (List<SpotifyImportSource>) -> Pair<
          List<SpotifyImportSource>, (SpotifyImportUiState) -> SpotifyImportUiState
        >
  ) {
    sourcesMutex.withLock {
      val (nextSources, uiStateUpdate) = updateAction(sources)
      sources = nextSources
      _uiState.update(uiStateUpdate)
    }
  }

  init {
    restoreSession()
    observeAuthRedirects()
  }

  private fun observeAuthRedirects() {
    viewModelScope.launch {
      SpotifyAuthCoordinator.redirects.collect { uri ->
        handleAuthRedirect(uri)
      }
    }
  }

  fun handleAuthRedirect(uri: Uri) {
    val error = uri.getQueryParameter("error")
    if (!error.isNullOrBlank()) {
      _uiState.update {
        it.copy(
          isLoading = false,
          errorMessage = "Spotify authorization failed: $error",
        )
      }
      return
    }

    val code = uri.getQueryParameter("code")
    if (!code.isNullOrBlank()) {
      connectWithAuthCode(code)
      return
    }

    // Check fragment for implicit grant (e.g. access_token=...&expires_in=...)
    val fragment = uri.fragment.orEmpty()
    if (fragment.contains("access_token")) {
      val params = fragment.split("&").associate { param ->
        val parts = param.split("=")
        if (parts.size == 2) parts[0] to parts[1] else "" to ""
      }
      val token = params["access_token"]
      val expiresIn = params["expires_in"]?.toLongOrNull() ?: 3600L
      if (!token.isNullOrBlank()) {
        connectWithAccessToken(token, expiresIn)
        return
      }
    }

    val spDc = uri.getQueryParameter("sp_dc")
    if (!spDc.isNullOrBlank()) {
      val spKey = uri.getQueryParameter("sp_key").orEmpty()
      connectWithCookies(spDc, spKey)
      return
    }
  }

  fun launchCustomTabsLogin(context: Context, clientId: String = "") {
    val url = SpotifyAuth.buildOAuthUrl(clientId = clientId)
    SpotifyAuth.launchCustomTabs(context, url)
  }

  fun connectWithAuthCode(code: String, clientId: String = "") {
    viewModelScope.launch(Dispatchers.IO) {
      _uiState.update { it.copy(isLoading = true, errorMessage = null) }
      runCatching { repository.connectWithAuthCode(code = code, clientId = clientId) }
        .onSuccess { session ->
          _uiState.update {
            it.copy(
              isAuthenticated = true,
              accountName = session.accountName,
              accountAvatarUrl = session.accountAvatarUrl,
              isLoading = false,
            )
          }
          loadSources()
        }
        .onFailure { error ->
          if (error is CancellationException) throw error
          reportException(error)
          _uiState.update {
            it.copy(
              isLoading = false,
              errorMessage = error.message,
            )
          }
        }
    }
  }

  fun connectWithAccessToken(accessToken: String, expiresInSeconds: Long = 3600L) {
    viewModelScope.launch(Dispatchers.IO) {
      _uiState.update { it.copy(isLoading = true, errorMessage = null) }
      runCatching { repository.connectWithAccessToken(accessToken = accessToken, expiresInSeconds = expiresInSeconds) }
        .onSuccess { session ->
          _uiState.update {
            it.copy(
              isAuthenticated = true,
              accountName = session.accountName,
              accountAvatarUrl = session.accountAvatarUrl,
              isLoading = false,
            )
          }
          loadSources()
        }
        .onFailure { error ->
          if (error is CancellationException) throw error
          reportException(error)
          _uiState.update {
            it.copy(
              isLoading = false,
              errorMessage = error.message,
            )
          }
        }
    }
  }

  fun restoreSession() {
    viewModelScope.launch(Dispatchers.IO) {
      runCatching { repository.restoreSession() }
        .onSuccess { session ->
          _uiState.update {
            it.copy(
              isAuthenticated = session.isAuthenticated,
              accountName = session.accountName,
              accountAvatarUrl = session.accountAvatarUrl,
              isLoading = false,
            )
          }
          if (session.isAuthenticated) {
            loadSources()
          }
        }
        .onFailure { error ->
          if (error is CancellationException) throw error
          reportException(error)
          _uiState.update {
            it.copy(
              isAuthenticated = false,
              isLoading = false,
              errorMessage = error.message,
            )
          }
        }
    }
  }

  fun connectWithCookies(
    spDc: String,
    spKey: String,
  ) {
    if (spDc.isBlank()) return
    viewModelScope.launch(Dispatchers.IO) {
      _uiState.update { it.copy(isLoading = true, errorMessage = null) }
      runCatching { repository.connectWithCookies(spDc = spDc, spKey = spKey) }
        .onSuccess { session ->
          _uiState.update {
            it.copy(
              isAuthenticated = true,
              accountName = session.accountName,
              accountAvatarUrl = session.accountAvatarUrl,
              isLoading = false,
            )
          }
          loadSources()
        }
        .onFailure { error ->
          if (error is CancellationException) throw error
          reportException(error)
          _uiState.update {
            it.copy(
              isLoading = false,
              errorMessage = error.message,
            )
          }
        }
    }
  }

  fun loadSources() {
    viewModelScope.launch(Dispatchers.IO) {
      _uiState.update { it.copy(isLoading = true, errorMessage = null) }
      runCatching { repository.loadSources() }
        .onSuccess { loadedSources ->
          updateSources {
            val selectedIds = loadedSources.mapTo(LinkedHashSet()) { it.id }
            loadedSources to
              { state ->
                state.copy(
                  isAuthenticated = true,
                  sources = loadedSources.map(SpotifyImportSource::toUi),
                  selectedSourceIds = selectedIds,
                  isLoading = false,
                )
              }
          }
        }
        .onFailure { error ->
          if (error is CancellationException) throw error
          reportException(error)
          _uiState.update {
            it.copy(
              isLoading = false,
              errorMessage = error.message,
            )
          }
        }
    }
  }

  fun addPlaylistByUrl(url: String) {
    val trimmed = url.trim()
    if (trimmed.isBlank() || uiState.value.progress != null) return
    viewModelScope.launch(Dispatchers.IO) {
      _uiState.update { it.copy(isLoading = true, errorMessage = null) }
      runCatching { repository.addPlaylistByUrl(trimmed) }
        .onSuccess { source ->
          updateSources { currentSources ->
            val existingIndex = currentSources.indexOfFirst { it.id == source.id }
            val nextSources =
              if (existingIndex >= 0) {
                currentSources.toMutableList().also { it[existingIndex] = source }
              } else {
                listOf(source) + currentSources
              }
            nextSources to
              { state ->
                state.copy(
                  isAuthenticated = true,
                  sources = nextSources.map(SpotifyImportSource::toUi),
                  selectedSourceIds = state.selectedSourceIds + source.id,
                  isLoading = false,
                )
              }
          }
        }
        .onFailure { error ->
          if (error is CancellationException) throw error
          reportException(error)
          _uiState.update {
            it.copy(
              isLoading = false,
              errorMessage = error.message,
            )
          }
        }
    }
  }

  fun toggleSource(sourceId: String) {
    _uiState.update { state ->
      val selected =
        if (sourceId in state.selectedSourceIds) {
          state.selectedSourceIds - sourceId
        } else {
          state.selectedSourceIds + sourceId
        }
      state.copy(selectedSourceIds = selected)
    }
  }

  fun selectAllSources() {
    _uiState.update { state ->
      state.copy(selectedSourceIds = state.sources.mapTo(LinkedHashSet()) { it.id })
    }
  }

  fun clearSelection() {
    _uiState.update { it.copy(selectedSourceIds = emptySet()) }
  }

  fun logout() {
    if (uiState.value.progress != null) return
    viewModelScope.launch(Dispatchers.IO) {
      _uiState.update { it.copy(isLoading = true, errorMessage = null) }
      runCatching { repository.logout() }
        .onSuccess {
          updateSources { emptyList<SpotifyImportSource>() to { SpotifyImportUiState() } }
        }
        .onFailure { error ->
          if (error is CancellationException) throw error
          reportException(error)
          _uiState.update {
            it.copy(
              isLoading = false,
              errorMessage = error.message,
            )
          }
        }
    }
  }

  fun importSelectedSources() {
    val selectedIds = uiState.value.selectedSourceIds
    if (selectedIds.isEmpty() || importJob?.isActive == true || uiState.value.progress != null)
      return
    val selectedSources = sources.filter { it.id in selectedIds }
    if (selectedSources.isEmpty()) return

    val job =
      viewModelScope.launch(Dispatchers.IO) {
        _uiState.update { it.copy(summary = null, errorMessage = null) }
        try {
          val summary =
            repository.importSources(selectedSources) { progress ->
              _uiState.update { it.copy(progress = progress) }
            }
          _uiState.update {
            it.copy(
              progress = null,
              summary = summary,
            )
          }
        } catch (error: CancellationException) {
          _uiState.update { it.copy(progress = null) }
          throw error
        } catch (error: Throwable) {
          reportException(error)
          _uiState.update {
            it.copy(
              progress = null,
              errorMessage = error.message,
            )
          }
        } finally {
          if (importJob === coroutineContext[Job]) {
            importJob = null
          }
        }
      }
    importJob = job
  }

  fun cancelImport() {
    importJob?.cancel()
    importJob = null
    _uiState.update { it.copy(progress = null) }
  }

  fun dismissSummary() {
    _uiState.update { it.copy(summary = null) }
  }

  fun dismissError() {
    _uiState.update { it.copy(errorMessage = null) }
  }
}

private fun SpotifyImportSource.toUi(): SpotifyImportSourceUi =
  SpotifyImportSourceUi(
    id = id,
    title = title,
    subtitle = subtitle,
    thumbnailUrl = thumbnailUrl,
    trackCount = trackCount,
    type = type,
  )
