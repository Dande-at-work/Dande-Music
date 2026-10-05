package echo.music.iad1tya.echomusic.component

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import echo.music.iad1tya.R
import echo.music.iad1tya.echomusic.updater.ChangelogSection
import echo.music.iad1tya.echomusic.updater.saveLastPromptedUpdateVersion
import echo.music.iad1tya.ui.utils.parseMarkdownToSections
import echo.music.iad1tya.ui.utils.parseSimpleMarkdown
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

/**
 * Displays a dialog informing the user that a newer version of the application is available
 * and allows downloading the APK directly to trigger the system package installer.
 *
 * @param version The latest version tag or display string.
 * @param changelog Pre-parsed structured changelog sections, if available.
 * @param description Raw release description or fallback Markdown text.
 * @param apkUrl Direct download URL of the APK asset from GitHub Releases.
 * @param apkSize Approximate size in MB.
 * @param onDismiss Invoked when the user dismisses the dialog.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateAvailableDialog(
  version: String,
  changelog: List<ChangelogSection>,
  description: String?,
  apkUrl: String? = null,
  apkSize: String? = null,
  onDismiss: () -> Unit
) {
  val context = LocalContext.current

  LaunchedEffect(version) {
    if (version.isNotBlank()) {
      saveLastPromptedUpdateVersion(context, version)
    }
  }
  val cardShape =
    AbsoluteSmoothCornerShape(
      cornerRadiusTL = 30.dp,
      cornerRadiusTR = 30.dp,
      cornerRadiusBL = 30.dp,
      cornerRadiusBR = 30.dp,
      smoothnessAsPercentTL = 60,
      smoothnessAsPercentTR = 60,
      smoothnessAsPercentBL = 60,
      smoothnessAsPercentBR = 60,
    )
  val blockShape = AbsoluteSmoothCornerShape(22.dp, 60)
  val actionShape = AbsoluteSmoothCornerShape(18.dp, 60)

  BasicAlertDialog(onDismissRequest = onDismiss) {
    Surface(
      modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
      shape = cardShape,
      color = MaterialTheme.colorScheme.surfaceContainerHigh,
      tonalElevation = 8.dp,
    ) {
      Column(
        modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        Surface(
          shape = blockShape,
          color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
          Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Surface(
                shape = AbsoluteSmoothCornerShape(12.dp, 60),
                color = MaterialTheme.colorScheme.primaryContainer,
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                  Icon(
                    painter = painterResource(R.drawable.update),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(16.dp),
                  )
                  Text(
                    text = stringResource(R.string.update_available_title),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                  )
                }
              }
            }

            Text(
              text = "Version $version is available",
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.Bold,
            )
          }
        }

        if (changelog.isNotEmpty() || !description.isNullOrEmpty()) {
          Surface(
            modifier = Modifier.weight(1f, fill = false),
            shape = blockShape,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
          ) {
            Column(
              modifier =
                Modifier.fillMaxWidth().padding(14.dp).verticalScroll(rememberScrollState()),
              verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Text(
                text = stringResource(R.string.changelog),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
              )

              val (effectiveDescription, effectiveSections) =
                remember(changelog, description) {
                  if (changelog.isNotEmpty()) {
                    Pair(description?.takeIf { it.isNotBlank() }, changelog)
                  } else if (!description.isNullOrBlank()) {
                    parseMarkdownToSections(description)
                  } else {
                    Pair(null, emptyList())
                  }
                }

              if (!effectiveDescription.isNullOrBlank()) {
                Text(
                  text =
                    parseSimpleMarkdown(effectiveDescription, MaterialTheme.colorScheme.primary),
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.padding(bottom = 4.dp)
                )
              }

              if (effectiveSections.isNotEmpty()) {
                effectiveSections.forEach { section ->
                  if (section.title.isNotBlank()) {
                    Text(
                      text = section.title,
                      style = MaterialTheme.typography.titleSmall,
                      fontWeight = FontWeight.Bold,
                      color = MaterialTheme.colorScheme.primary,
                      modifier = Modifier.padding(top = 6.dp)
                    )
                  }
                  section.items.forEach { item ->
                    Row(
                      modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                      horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                      Text(
                        text = "•",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                      )
                      Text(
                        text = parseSimpleMarkdown(item.trim(), MaterialTheme.colorScheme.primary),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                      )
                    }
                  }
                }
              }
            }
          }
        }

        val scope = rememberCoroutineScope()
        var isDownloading by remember { mutableStateOf(false) }
        var downloadProgress by remember { mutableStateOf(0f) }
        var downloadedBytesText by remember { mutableStateOf("") }
        var downloadedApkFile by remember { mutableStateOf<File?>(null) }
        var downloadError by remember { mutableStateOf<String?>(null) }

        if (isDownloading) {
          Surface(
            shape = blockShape,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(
              modifier = Modifier.padding(12.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              LinearProgressIndicator(
                progress = { downloadProgress },
                modifier = Modifier.fillMaxWidth().clip(CircleShape),
              )
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Text(
                  text = "${(downloadProgress * 100).toInt()}%",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.primary,
                  fontWeight = FontWeight.Bold,
                )
                Text(
                  text = downloadedBytesText,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
        }

        downloadError?.let { err ->
          Text(
            text = err,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
          )
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.End,
        ) {
          TextButton(
            onClick = onDismiss,
            shape = actionShape,
            enabled = !isDownloading,
          ) {
            Text(text = "Next time")
          }
          Spacer(modifier = Modifier.width(8.dp))
          Button(
            onClick = {
              val completedFile = downloadedApkFile
              if (completedFile != null && completedFile.exists()) {
                triggerPackageInstaller(context, completedFile)
                return@Button
              }

              if (!apkUrl.isNullOrBlank()) {
                isDownloading = true
                downloadError = null
                scope.launch(Dispatchers.IO) {
                  try {
                    val downloadDir =
                      File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "echo_updates")
                    if (!downloadDir.exists()) downloadDir.mkdirs()
                    val targetFile = File(downloadDir, "DandeMusic-$version.apk")

                    var currentUrl = apkUrl
                    var connection: HttpURLConnection
                    var redirectCount = 0
                    while (true) {
                      connection = URL(currentUrl).openConnection() as HttpURLConnection
                      connection.instanceFollowRedirects = true
                      connection.connectTimeout = 15_000
                      connection.readTimeout = 30_000
                      connection.connect()
                      val responseCode = connection.responseCode
                      if (responseCode in 300..399) {
                        val newLocation = connection.getHeaderField("Location")
                        if (!newLocation.isNullOrEmpty() && redirectCount < 5) {
                          redirectCount++
                          currentUrl = newLocation
                          connection.disconnect()
                          continue
                        }
                      }
                      break
                    }

                    if (connection.responseCode !in 200..299) {
                      throw Exception("HTTP ${connection.responseCode}")
                    }

                    val totalLength = connection.contentLengthLong
                    val inputStream = connection.inputStream
                    val outputStream = FileOutputStream(targetFile)
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalBytesRead = 0L

                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                      outputStream.write(buffer, 0, bytesRead)
                      totalBytesRead += bytesRead
                      if (totalLength > 0) {
                        val prog = totalBytesRead.toFloat() / totalLength.toFloat()
                        withContext(Dispatchers.Main) {
                          downloadProgress = prog
                          val currentMb = String.format("%.1f", totalBytesRead / (1024.0 * 1024.0))
                          val totalMb = String.format("%.1f", totalLength / (1024.0 * 1024.0))
                          downloadedBytesText = "$currentMb MB / $totalMb MB"
                        }
                      }
                    }
                    outputStream.flush()
                    outputStream.close()
                    inputStream.close()
                    connection.disconnect()

                    withContext(Dispatchers.Main) {
                      isDownloading = false
                      downloadedApkFile = targetFile
                      triggerPackageInstaller(context, targetFile)
                    }
                  } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                      isDownloading = false
                      downloadError = "Download failed: ${e.message}"
                    }
                  }
                }
              } else {
                onDismiss()
                val intent =
                  Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://github.com/Dande-at-work/Dande-Music/releases")
                  )
                context.startActivity(intent)
              }
            },
            shape = actionShape,
            enabled = !isDownloading,
          ) {
            Text(
              text =
                when {
                  isDownloading -> "Downloading..."
                  downloadedApkFile != null -> "Install Now"
                  !apkUrl.isNullOrBlank() -> "Download & Install"
                  else -> "Update"
                }
            )
          }
        }
      }
    }
  }
}

private fun triggerPackageInstaller(context: Context, apkFile: File) {
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
    if (!context.packageManager.canRequestPackageInstalls()) {
      val intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
          data = Uri.parse("package:${context.packageName}")
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
      context.startActivity(intent)
      return
    }
  }

  val uri =
    FileProvider.getUriForFile(
      context,
      "${context.packageName}.FileProvider",
      apkFile
    )
  val installIntent =
    Intent(Intent.ACTION_VIEW).apply {
      setDataAndType(uri, "application/vnd.android.package-archive")
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
  context.startActivity(installIntent)
}
