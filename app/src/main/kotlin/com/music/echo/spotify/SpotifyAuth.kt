/*
 * EchoMusic (2026)
 * © Chartreux Westia — github.com/koiverse
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package echo.music.iad1tya.spotify

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import androidx.browser.customtabs.CustomTabsIntent
import echo.music.iad1tya.spotify.models.SpotifyInternalToken
import echo.music.iad1tya.spotify.models.SpotifyToken
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.floor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Handles Spotify authentication using either:
 * 1. Secure Chrome Custom Tabs with Spotify OAuth 2.0 (Authorization Code Flow with PKCE)
 * 2. Web player's internal token endpoint using sp_dc cookies and TOTP.
 *
 * Reference: https://github.com/sonic-liberation/spotube-plugin-spotify
 */
object SpotifyAuth {
  const val DEFAULT_CLIENT_ID = "279221262d164627b0c39be415f33d06"
  const val REDIRECT_URI = "echomusic://spotify-callback"
  private const val AUTH_ENDPOINT = "https://accounts.spotify.com/authorize"
  private const val OAUTH_TOKEN_ENDPOINT = "https://accounts.spotify.com/api/token"
  private const val SCOPES =
    "user-read-private user-read-email playlist-read-private playlist-read-collaborative user-library-read"

  @Volatile var currentCodeVerifier: String? = null

  private const val TOKEN_URL = "https://open.spotify.com/api/token"
  private const val SERVER_TIME_URL = "https://open.spotify.com/api/server-time"
  private const val NUANCE_GIST_URL =
    "https://gist.githubusercontent.com/sonic-liberation/22ed9c6ba463899e933427f7de1f0eef/raw/"
  private const val USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

  const val LOGIN_URL =
    "https://accounts.spotify.com/login?continue=https%3A%2F%2Fopen.spotify.com%2F"

  fun generateCodeVerifier(): String {
    val secureRandom = SecureRandom()
    val bytes = ByteArray(64)
    secureRandom.nextBytes(bytes)
    return Base64.encodeToString(
      bytes,
      Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
    )
  }

  fun generateCodeChallenge(verifier: String): String {
    val bytes = verifier.toByteArray(Charsets.US_ASCII)
    val messageDigest = MessageDigest.getInstance("SHA-256")
    val digest = messageDigest.digest(bytes)
    return Base64.encodeToString(
      digest,
      Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
    )
  }

  fun buildOAuthUrl(
    clientId: String = DEFAULT_CLIENT_ID,
    redirectUri: String = REDIRECT_URI,
    usePkce: Boolean = true,
  ): String {
    val effectiveClientId = clientId.ifBlank { DEFAULT_CLIENT_ID }
    val builder =
      Uri.parse(AUTH_ENDPOINT)
        .buildUpon()
        .appendQueryParameter("client_id", effectiveClientId)
        .appendQueryParameter("response_type", "code")
        .appendQueryParameter("redirect_uri", redirectUri)
        .appendQueryParameter("scope", SCOPES)
        .appendQueryParameter("show_dialog", "true")

    if (usePkce) {
      val verifier = generateCodeVerifier()
      currentCodeVerifier = verifier
      val challenge = generateCodeChallenge(verifier)
      builder.appendQueryParameter("code_challenge_method", "S256")
      builder.appendQueryParameter("code_challenge", challenge)
    }

    return builder.build().toString()
  }

  fun launchCustomTabs(context: Context, url: String) {
    val customTabsIntent =
      CustomTabsIntent.Builder()
        .setShowTitle(true)
        .build()
    customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    customTabsIntent.launchUrl(context, Uri.parse(url))
  }

  suspend fun exchangeAuthCode(
    code: String,
    clientId: String = DEFAULT_CLIENT_ID,
    redirectUri: String = REDIRECT_URI,
    codeVerifier: String? = currentCodeVerifier,
  ): Result<SpotifyToken> =
    withContext(Dispatchers.IO) {
      runCatching {
        val effectiveClientId = clientId.ifBlank { DEFAULT_CLIENT_ID }
        val connection = URL(OAUTH_TOKEN_ENDPOINT).openConnection() as HttpURLConnection
        try {
          connection.requestMethod = "POST"
          connection.doOutput = true
          connection.connectTimeout = 15_000
          connection.readTimeout = 15_000
          connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
          connection.setRequestProperty("User-Agent", USER_AGENT)

          val params =
            mutableListOf(
              "grant_type=authorization_code",
              "code=${URLEncoder.encode(code, "UTF-8")}",
              "redirect_uri=${URLEncoder.encode(redirectUri, "UTF-8")}",
              "client_id=${URLEncoder.encode(effectiveClientId, "UTF-8")}",
            )
          if (!codeVerifier.isNullOrBlank()) {
            params.add("code_verifier=${URLEncoder.encode(codeVerifier, "UTF-8")}")
          }

          val postData = params.joinToString("&")
          connection.outputStream.use { it.write(postData.toByteArray(Charsets.UTF_8)) }

          val responseCode = connection.responseCode
          if (responseCode !in 200..299) {
            val errorText = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            throw Spotify.SpotifyException(responseCode, "Token exchange failed: $errorText")
          }
          val body = connection.inputStream.bufferedReader().use { it.readText() }
          json.decodeFromString<SpotifyToken>(body)
        } finally {
          connection.disconnect()
        }
      }
    }

  suspend fun refreshOAuthToken(
    refreshToken: String,
    clientId: String = DEFAULT_CLIENT_ID,
  ): Result<SpotifyToken> =
    withContext(Dispatchers.IO) {
      runCatching {
        val effectiveClientId = clientId.ifBlank { DEFAULT_CLIENT_ID }
        val connection = URL(OAUTH_TOKEN_ENDPOINT).openConnection() as HttpURLConnection
        try {
          connection.requestMethod = "POST"
          connection.doOutput = true
          connection.connectTimeout = 15_000
          connection.readTimeout = 15_000
          connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
          connection.setRequestProperty("User-Agent", USER_AGENT)

          val postData =
            "grant_type=refresh_token" +
              "&refresh_token=${URLEncoder.encode(refreshToken, "UTF-8")}" +
              "&client_id=${URLEncoder.encode(effectiveClientId, "UTF-8")}"
          connection.outputStream.use { it.write(postData.toByteArray(Charsets.UTF_8)) }

          val responseCode = connection.responseCode
          if (responseCode !in 200..299) {
            val errorText = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            throw Spotify.SpotifyException(responseCode, "Token refresh failed: $errorText")
          }
          val body = connection.inputStream.bufferedReader().use { it.readText() }
          json.decodeFromString<SpotifyToken>(body)
        } finally {
          connection.disconnect()
        }
      }
    }

  private val json = Json {
    isLenient = true
    ignoreUnknownKeys = true
  }

  @Serializable private data class Nuance(val s: String, val v: Int)

  @Serializable private data class ServerTimeResponse(val serverTime: Long)

  /**
   * Fetches an internal web-player access token using session cookies and TOTP.
   * 1. Fetches the TOTP secret from the community Gist
   * 2. Gets the server time from Spotify
   * 3. Generates a 6-digit TOTP (SHA1, 30s interval)
   * 4. Calls /api/token with the TOTP and sp_dc cookie
   */
  suspend fun fetchAccessToken(
    spDc: String,
    spKey: String = "",
  ): Result<SpotifyInternalToken> = runCatching {
    val nuance = fetchNuance()
    val serverTimeSec = fetchServerTime()
    val totp = generateTotp(nuance.s, serverTimeSec)

    val tokenUrl = buildString {
      append(TOKEN_URL)
      append("?reason=transport")
      append("&productType=web-player")
      append("&totp=$totp")
      append("&totpServer=$totp")
      append("&totpVer=${nuance.v}")
    }

    val cookieHeader = buildString {
      append("sp_dc=$spDc")
      if (spKey.isNotEmpty()) {
        append("; sp_key=$spKey")
      }
    }

    val body = withContext(Dispatchers.IO) { httpGet(tokenUrl, mapOf("Cookie" to cookieHeader)) }

    val token = json.decodeFromString<SpotifyInternalToken>(body)

    if (token.isAnonymous || token.accessToken.isBlank()) {
      throw Spotify.SpotifyException(
        401,
        "Received anonymous token — sp_dc cookie is invalid or expired",
      )
    }

    token
  }

  private suspend fun fetchNuance(): Nuance =
    withContext(Dispatchers.IO) {
      val body =
        try {
          httpGet(NUANCE_GIST_URL, emptyMap())
        } catch (e: Exception) {
          throw Spotify.SpotifyException(
            503,
            "Failed to fetch TOTP secret from gist: ${e.message}",
          )
        }
      val nuances = json.decodeFromString<List<Nuance>>(body)
      nuances.maxByOrNull { it.v }
        ?: throw Spotify.SpotifyException(500, "No nuance data found in gist")
    }

  private suspend fun fetchServerTime(): Long =
    withContext(Dispatchers.IO) {
      val body =
        try {
          httpGet(SERVER_TIME_URL, emptyMap())
        } catch (e: Exception) {
          throw Spotify.SpotifyException(
            503,
            "Failed to fetch Spotify server time: ${e.message}",
          )
        }
      val response = json.decodeFromString<ServerTimeResponse>(body)
      response.serverTime
    }

  /**
   * Generates a 6-digit TOTP using HMAC-SHA1 (RFC 6238).
   *
   * @param secret Base32-encoded shared secret
   * @param serverTimeSec Spotify server time in seconds since epoch
   */
  private fun generateTotp(secret: String, serverTimeSec: Long): String {
    val key = base32Decode(secret)
    val interval = 30L
    val timeStep = floor(serverTimeSec.toDouble() / interval).toLong()

    val timeBytes = ByteArray(8)
    var value = timeStep
    for (i in 7 downTo 0) {
      timeBytes[i] = (value and 0xFF).toByte()
      value = value shr 8
    }

    val mac = Mac.getInstance("HmacSHA1")
    mac.init(SecretKeySpec(key, "HmacSHA1"))
    val hash = mac.doFinal(timeBytes)

    val offset = hash[hash.size - 1].toInt() and 0x0F
    val code =
      ((hash[offset].toInt() and 0x7F) shl 24) or
        ((hash[offset + 1].toInt() and 0xFF) shl 16) or
        ((hash[offset + 2].toInt() and 0xFF) shl 8) or
        (hash[offset + 3].toInt() and 0xFF)

    val otp = code % 1_000_000
    return otp.toString().padStart(6, '0')
  }

  private fun base32Decode(input: String): ByteArray {
    val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
    val cleaned = input.uppercase().replace("=", "")

    val output = mutableListOf<Byte>()
    var buffer = 0
    var bitsLeft = 0

    for (c in cleaned) {
      val value = alphabet.indexOf(c)
      if (value < 0) continue
      buffer = (buffer shl 5) or value
      bitsLeft += 5
      if (bitsLeft >= 8) {
        bitsLeft -= 8
        output.add(((buffer shr bitsLeft) and 0xFF).toByte())
      }
    }

    return output.toByteArray()
  }

  private fun httpGet(urlString: String, extraHeaders: Map<String, String>): String {
    val connection = URL(urlString).openConnection() as HttpURLConnection
    try {
      connection.requestMethod = "GET"
      connection.instanceFollowRedirects = true
      connection.connectTimeout = 15_000
      connection.readTimeout = 15_000
      connection.setRequestProperty("User-Agent", USER_AGENT)
      connection.setRequestProperty("Accept", "application/json, text/plain, */*")
      connection.setRequestProperty("Accept-Language", "en")
      for ((key, value) in extraHeaders) {
        connection.setRequestProperty(key, value)
      }

      val responseCode = connection.responseCode
      if (responseCode !in 200..299) {
        val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
        throw Spotify.SpotifyException(
          responseCode,
          "HTTP $responseCode: $errorBody",
        )
      }

      return connection.inputStream.bufferedReader().use { it.readText() }
    } finally {
      connection.disconnect()
    }
  }
}
