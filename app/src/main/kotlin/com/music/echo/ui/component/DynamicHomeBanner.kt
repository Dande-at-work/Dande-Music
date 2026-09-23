package echo.music.iad1tya.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import echo.music.iad1tya.R
import java.time.LocalDate
import java.util.Calendar
import timber.log.Timber

const val DANNY_MEMORIAL_TITLE = "Danny's Memorial"
const val DANNY_MEMORIAL_DESCRIPTION =
  "On 23rd September 2024, 1:46 am Daniel Ricciardo left the paddock for the final time as a Formula 1 driver."

/**
 * State representing the dynamic banner content and visibility.
 */
data class DynamicBannerState(
  val title: String = "",
  val description: String = "",
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

        val hasText = bannerText.isNotEmpty()
        val hasDesc = bannerDescription.isNotEmpty()

        if (hasText || hasDesc) {
          bannerState =
            DynamicBannerState(
              title = if (hasText) bannerText else bannerDescription,
              description = if (hasText && hasDesc) bannerDescription else "",
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
 * Prominent dynamic banner view displayed at the top of the Home screen layout.
 * Includes a main title text view and a smaller subtitle description text view beneath it.
 */
@Composable
fun DynamicHomeBanner(
  title: String,
  description: String,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier =
      modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 8.dp),
    shape = RoundedCornerShape(20.dp),
    color = MaterialTheme.colorScheme.surfaceContainerHigh,
    tonalElevation = 4.dp,
    border =
      BorderStroke(
        width = 1.dp,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
      ),
  ) {
    Box(
      modifier =
        Modifier.fillMaxWidth()
          .background(
            Brush.linearGradient(
              colors =
                listOf(
                  MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                  MaterialTheme.colorScheme.surfaceContainerHigh,
                )
            )
          )
          .padding(16.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        Surface(
          shape = CircleShape,
          color = MaterialTheme.colorScheme.primaryContainer,
          modifier = Modifier.size(42.dp),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              painter = painterResource(R.drawable.star),
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(22.dp),
            )
          }
        }

        Column(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          if (title.isNotEmpty()) {
            Text(
              text = title,
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSurface,
            )
          }

          if (description.isNotEmpty()) {
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
