package echo.music.iad1tya.ui.component

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import echo.music.iad1tya.constants.PlayerBackgroundStyle
import echo.music.iad1tya.constants.PlayerBackgroundStyleKey
import echo.music.iad1tya.utils.rememberEnumPreference
import java.time.LocalDate
import java.time.LocalTime
import java.util.Calendar
import timber.log.Timber

const val DANNY_MEMORIAL_TITLE = "Danny's Memorial"
const val DANNY_MEMORIAL_DESCRIPTION =
  "On 23rd September 2024, 1:46 am Daniel Ricciardo left the paddock for the final time as a Formula 1 driver."
const val DANNY_MEMORIAL_CLICK_URL = "https://www.youtube.com/watch?v=9HHQMbEdxBM"

/**
 * State representing the dynamic banner content and visibility.
 */
data class DynamicBannerState(
  val title: String = "",
  val description: String = "",
  val imageUrl: String? = null,
  val clickUrl: String? = null,
  val isVisible: Boolean = false,
)

/**
 * Checks whether the current system date is September 23.
 */
fun isSeptember23(): Boolean {
  return try {
    val today = LocalDate.now()
    today.monthValue == 9 && today.dayOfMonth == 23
  } catch (e: Throwable) {
    val cal = Calendar.getInstance()
    cal.get(Calendar.MONTH) == Calendar.SEPTEMBER && cal.get(Calendar.DAY_OF_MONTH) == 23
  }
}

/**
 * Manages the state for the dynamic home banner, honoring the hardcoded September 23 memorial
 * event and fetching from Firebase Remote Config on other dates.
 */
@Composable
fun rememberDynamicHomeBannerState(): DynamicBannerState {
  val isMemorial = remember { isSeptember23() }

  if (isMemorial) {
    return remember {
      DynamicBannerState(
        title = DANNY_MEMORIAL_TITLE,
        description = DANNY_MEMORIAL_DESCRIPTION,
        imageUrl = null, // hide ImageView on Sept 23
        clickUrl = DANNY_MEMORIAL_CLICK_URL,
        isVisible = true,
      )
    }
  }

  var bannerState by remember { mutableStateOf(DynamicBannerState()) }

  LaunchedEffect(Unit) {
    try {
      val remoteConfig = FirebaseRemoteConfig.getInstance()
      val configSettings =
        FirebaseRemoteConfigSettings.Builder()
          .setMinimumFetchIntervalInSeconds(0L)
          .build()
      remoteConfig.setConfigSettingsAsync(configSettings)

      fun updateBannerFromRemote() {
        val bannerText = remoteConfig.getString("banner_text").trim()
        val bannerDescription = remoteConfig.getString("banner_description").trim()
        val bannerImageUrl = remoteConfig.getString("banner_image_url").trim()
        val bannerClickUrl = remoteConfig.getString("banner_click_url").trim()

        val hasText = bannerText.isNotEmpty()
        val hasDesc = bannerDescription.isNotEmpty()
        val hasImage = bannerImageUrl.isNotEmpty()

        if (hasText || hasDesc || hasImage) {
          bannerState =
            DynamicBannerState(
              title = bannerText,
              description = bannerDescription,
              imageUrl = if (hasImage) bannerImageUrl else null,
              clickUrl = if (bannerClickUrl.isNotEmpty()) bannerClickUrl else null,
              isVisible = true,
            )
        } else {
          bannerState = DynamicBannerState(isVisible = false)
        }
      }

      // Check current/cached values first
      updateBannerFromRemote()

      // Fetch and activate latest values from Firebase Remote Config
      remoteConfig.fetchAndActivate().addOnCompleteListener { task ->
        if (task.isSuccessful) {
          updateBannerFromRemote()
        }
      }
    } catch (e: Exception) {
      Timber.e(e, "Error initializing or fetching Firebase Remote Config for banner")
    }
  }

  return bannerState
}

/**
 * Prominent dynamic banner view displayed on the Home screen layout.
 * Contains an ImageView (via Coil), Title TextView, and Subtitle TextView.
 * Adapts its background container to the active Appearance theme.
 */
@Composable
fun DynamicHomeBanner(
  state: DynamicBannerState,
  modifier: Modifier = Modifier,
) {
  DynamicHomeBanner(
    title = state.title,
    description = state.description,
    imageUrl = state.imageUrl,
    clickUrl = state.clickUrl,
    modifier = modifier,
  )
}

@Composable
fun DynamicHomeBanner(
  title: String,
  description: String,
  modifier: Modifier = Modifier,
) {
  DynamicHomeBanner(
    title = title,
    description = description,
    imageUrl = null,
    clickUrl = null,
    modifier = modifier,
  )
}

@Composable
fun DynamicHomeBanner(
  title: String,
  description: String,
  imageUrl: String?,
  clickUrl: String?,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val (playerBackground) =
    rememberEnumPreference(
      key = PlayerBackgroundStyleKey,
      defaultValue = PlayerBackgroundStyle.GRADIENT,
    )
  val glassConfig = LocalGlassEffectConfig.current

  val isLiquidGlass =
    glassConfig.globalEnabled || playerBackground == PlayerBackgroundStyle.LIQUID_GLASS
  val isBlur = playerBackground == PlayerBackgroundStyle.BLUR
  val isGlow = playerBackground == PlayerBackgroundStyle.GLOW_ANIMATED
  val isLiveMesh = playerBackground == PlayerBackgroundStyle.LIVE_MESH

  val shape = RoundedCornerShape(20.dp)

  val containerColor =
    when {
      isLiquidGlass -> MaterialTheme.colorScheme.surface.copy(alpha = 0.45f)
      isBlur -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.65f)
      isLiveMesh -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f)
      else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }

  val borderStroke =
    when {
      isLiquidGlass -> BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f))
      isBlur -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
      isGlow -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
      isLiveMesh -> BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
      else -> BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
    }

  val gradientBrush: Brush? =
    when {
      isLiveMesh ->
        Brush.linearGradient(
          colors =
            listOf(
              MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
              MaterialTheme.colorScheme.tertiary.copy(alpha = 0.18f),
              MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f),
            )
        )
      isGlow ->
        Brush.linearGradient(
          colors =
            listOf(
              MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
              MaterialTheme.colorScheme.surfaceContainerHigh,
            )
        )
      isLiquidGlass -> null
      isBlur ->
        Brush.linearGradient(
          colors =
            listOf(
              MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
              MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.8f),
            )
        )
      else ->
        Brush.linearGradient(
          colors =
            listOf(
              MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
              MaterialTheme.colorScheme.surfaceContainerHigh,
            )
        )
    }

  val onClickBanner: (() -> Unit)? =
    remember(clickUrl, context) {
      if (!clickUrl.isNullOrBlank()) {
        {
          try {
            val intent =
              Intent(Intent.ACTION_VIEW, Uri.parse(clickUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
              }
            context.startActivity(intent)
          } catch (e: Throwable) {
            Timber.e(e, "Failed to open banner URL: $clickUrl")
          }
        }
      } else {
        null
      }
    }

  val glassModifier =
    if (isLiquidGlass && isGlassSupported()) {
      Modifier.liquidGlass(config = glassConfig, shape = shape)
    } else {
      Modifier
    }

  Surface(
    modifier =
      modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 6.dp)
        .then(glassModifier)
        .clip(shape)
        .then(
          if (onClickBanner != null) {
            Modifier.clickable(onClick = onClickBanner)
          } else {
            Modifier
          }
        ),
    shape = shape,
    color = containerColor,
    tonalElevation = if (isLiquidGlass) 0.dp else 4.dp,
    border = borderStroke,
  ) {
    Box(
      modifier =
        Modifier.fillMaxWidth()
          .then(
            if (gradientBrush != null) {
              Modifier.background(gradientBrush)
            } else {
              Modifier
            }
          )
          .padding(16.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        if (!imageUrl.isNullOrBlank()) {
          AsyncImage(
            model =
              ImageRequest.Builder(context)
                .data(imageUrl)
                .crossfade(true)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier =
              Modifier.size(60.dp)
                .clip(RoundedCornerShape(12.dp)),
          )
        }

        Column(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          if (title.isNotBlank()) {
            Text(
              text = title,
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSurface,
            )
          }

          if (description.isNotBlank()) {
            Text(
              text = description,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              lineHeight = 20.sp,
            )
          }
        }
      }
    }
  }
}

/**
 * Theme-adaptive dynamic greeting TextView.
 * Computes greeting from local time:
 * - "Good morning" (05:00-11:59)
 * - "Good afternoon" (12:00-16:59)
 * - "Good evening" (17:00-04:59)
 *
 * Uses an annotated string where "Good " is set in a normal font and bound to the standard dynamic
 * theme foreground token (MaterialTheme.colorScheme.onBackground) so it shifts with the music palette.
 * The time of day word is italicized and styled based on the user's active Appearance setting:
 * - 70% alpha + subtle blur for Liquid Glass or Blur
 * - Glowing text shadow for Glow animated
 * - Dynamic color gradient for Live Mesh
 * - Standard accent color for Default
 */
@Composable
fun HomeGreeting(
  modifier: Modifier = Modifier,
) {
  val (playerBackground) =
    rememberEnumPreference(
      key = PlayerBackgroundStyleKey,
      defaultValue = PlayerBackgroundStyle.GRADIENT,
    )
  val glassConfig = LocalGlassEffectConfig.current

  val isLiquidGlassOrBlur =
    glassConfig.globalEnabled ||
      playerBackground == PlayerBackgroundStyle.LIQUID_GLASS ||
      playerBackground == PlayerBackgroundStyle.BLUR

  val isGlowAnimated = playerBackground == PlayerBackgroundStyle.GLOW_ANIMATED
  val isLiveMesh = playerBackground == PlayerBackgroundStyle.LIVE_MESH

  val hour = remember {
    try {
      LocalTime.now().hour
    } catch (e: Throwable) {
      Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    }
  }

  val timeOfDayWord =
    remember(hour) {
      when (hour) {
        in 5..11 -> "morning"
        in 12..16 -> "afternoon"
        else -> "evening"
      }
    }

  val foregroundToken = MaterialTheme.colorScheme.onBackground
  val primaryColor = MaterialTheme.colorScheme.primary
  val tertiaryColor = MaterialTheme.colorScheme.tertiary
  val secondaryColor = MaterialTheme.colorScheme.secondary

  val annotatedGreeting =
    remember(
      timeOfDayWord,
      isLiquidGlassOrBlur,
      isGlowAnimated,
      isLiveMesh,
      foregroundToken,
      primaryColor,
      tertiaryColor,
      secondaryColor,
    ) {
      buildAnnotatedString {
        withStyle(
          SpanStyle(
            color = foregroundToken,
            fontWeight = FontWeight.Normal,
            fontStyle = FontStyle.Normal,
          )
        ) {
          append("Good ")
        }

        val timeSpanStyle =
          when {
            isLiquidGlassOrBlur ->
              SpanStyle(
                color = primaryColor.copy(alpha = 0.7f),
                fontStyle = FontStyle.Italic,
                fontWeight = FontWeight.SemiBold,
                shadow =
                  Shadow(
                    color = primaryColor.copy(alpha = 0.7f),
                    blurRadius = 6f,
                  ),
              )
            isGlowAnimated ->
              SpanStyle(
                color = primaryColor,
                fontStyle = FontStyle.Italic,
                fontWeight = FontWeight.SemiBold,
                shadow =
                  Shadow(
                    color = primaryColor,
                    blurRadius = 20f,
                  ),
              )
            isLiveMesh ->
              SpanStyle(
                brush =
                  Brush.linearGradient(
                    colors = listOf(primaryColor, tertiaryColor, secondaryColor)
                  ),
                fontStyle = FontStyle.Italic,
                fontWeight = FontWeight.SemiBold,
              )
            else ->
              SpanStyle(
                color = primaryColor,
                fontStyle = FontStyle.Italic,
                fontWeight = FontWeight.SemiBold,
              )
          }

        withStyle(timeSpanStyle) {
          append(timeOfDayWord)
        }
      }
    }

  Text(
    text = annotatedGreeting,
    style =
      MaterialTheme.typography.headlineMedium.copy(
        fontSize = 26.sp,
        fontWeight = FontWeight.Bold,
      ),
    modifier =
      modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 6.dp),
  )
}

/**
 * Builds a SpannableString representation of the dynamic greeting for compatibility with
 * Android View / Spannable consumers.
 */
fun buildGreetingSpannable(
  timeOfDayWord: String,
  foregroundTokenColorInt: Int,
  accentColorInt: Int,
  isLiquidGlassOrBlur: Boolean = false,
): android.text.SpannableString {
  val fullText = "Good $timeOfDayWord"
  val spannable = android.text.SpannableString(fullText)

  // "Good " normal font & bound to foreground token
  spannable.setSpan(
    android.text.style.ForegroundColorSpan(foregroundTokenColorInt),
    0,
    5,
    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
  )
  spannable.setSpan(
    android.text.style.StyleSpan(android.graphics.Typeface.NORMAL),
    0,
    5,
    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
  )

  // time-of-day word in italic
  val start = 5
  val end = fullText.length
  spannable.setSpan(
    android.text.style.StyleSpan(android.graphics.Typeface.ITALIC),
    start,
    end,
    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
  )

  if (isLiquidGlassOrBlur) {
    val alphaColor =
      androidx.core.graphics.ColorUtils.setAlphaComponent(accentColorInt, (255 * 0.7f).toInt())
    spannable.setSpan(
      android.text.style.ForegroundColorSpan(alphaColor),
      start,
      end,
      android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
    )
  } else {
    spannable.setSpan(
      android.text.style.ForegroundColorSpan(accentColorInt),
      start,
      end,
      android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
    )
  }

  return spannable
}
