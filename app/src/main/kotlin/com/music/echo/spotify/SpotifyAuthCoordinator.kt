package echo.music.iad1tya.spotify

import android.net.Uri
import kotlinx.coroutines.flow.MutableSharedFlow

object SpotifyAuthCoordinator {
  val redirects =
    MutableSharedFlow<Uri>(
      replay = 1,
      extraBufferCapacity = 1,
    )

  fun emit(uri: Uri) {
    redirects.tryEmit(uri)
  }
}
