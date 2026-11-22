package dev.jdtech.jellyfin.core.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.jdtech.jellyfin.update.AppUpdateInfo
import kotlin.math.roundToInt

@Composable
fun AppUpdateDialog(
    updateInfo: AppUpdateInfo,
    downloadProgress: Float?,
    errorMessage: String?,
    onUpdateClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    val primaryFocusRequester = remember { FocusRequester() }
    val isDownloading = downloadProgress != null

    LaunchedEffect(Unit) {
        runCatching { primaryFocusRequester.requestFocus() }
    }

    Dialog(
        onDismissRequest = { if (!updateInfo.mandatory && !isDownloading) onDismiss() },
        properties =
            DialogProperties(
                dismissOnBackPress = !updateInfo.mandatory && !isDownloading,
                dismissOnClickOutside = false,
            ),
    ) {
        Column(
            modifier =
                Modifier.widthIn(min = 300.dp, max = 460.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xF5111827))
                    .border(1.5.dp, Color(0xFF38BDF8), RoundedCornerShape(18.dp))
                    .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier =
                    Modifier.clip(RoundedCornerShape(8.dp))
                        .background(Color(0x2E38BDF8))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "ACTUALIZACIÓN DISPONIBLE",
                    color = Color(0xFF7DD3FC),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            Text(
                text = "Jellyx.tv v${updateInfo.versionName}",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )

            if (updateInfo.releaseNotes.isNotBlank()) {
                Text(
                    text = updateInfo.releaseNotes,
                    color = Color(0xFFCBD5E1),
                    fontSize = 13.sp,
                    modifier = Modifier.height(110.dp).verticalScroll(rememberScrollState()),
                )
            } else {
                Text(
                    text = "Hay una nueva versión estable lista para instalar.",
                    color = Color(0xFFCBD5E1),
                    fontSize = 14.sp,
                )
            }

            if (downloadProgress != null) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LinearProgressIndicator(
                        progress = { downloadProgress },
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFF38BDF8),
                    )
                    Text(
                        text = "Descargando actualización: ${(downloadProgress * 100).roundToInt()}%",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp,
                    )
                }
            }

            if (!errorMessage.isNullOrBlank()) {
                Text(
                    text = errorMessage,
                    color = Color(0xFFFBBF24),
                    fontSize = 12.sp,
                )
            }

            Spacer(Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!updateInfo.mandatory && !isDownloading) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(text = "Más tarde", color = Color.White)
                    }
                }
                Button(
                    onClick = onUpdateClick,
                    enabled = !isDownloading,
                    modifier = Modifier.weight(1f).focusRequester(primaryFocusRequester),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF38BDF8),
                            contentColor = Color(0xFF082F49),
                        ),
                ) {
                    Text(
                        text = if (isDownloading) "Descargando..." else "Actualizar ahora",
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

