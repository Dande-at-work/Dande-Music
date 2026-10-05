package echo.music.iad1tya.spotify

import android.app.Activity
import android.content.Intent
import android.os.Bundle

open class SpotifyOAuthCallbackActivity : Activity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    handleIntent(intent)
    finish()
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    handleIntent(intent)
    finish()
  }

  private fun handleIntent(intent: Intent?) {
    intent?.data?.let(SpotifyAuthCoordinator::emit)
  }
}
