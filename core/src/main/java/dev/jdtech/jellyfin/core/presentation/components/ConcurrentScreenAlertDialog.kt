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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.jdtech.jellyfin.core.R as CoreR

@Composable
fun ConcurrentScreenAlertDialog(
    header: String,
    message: String,
    isBlocking: Boolean = true,
    confirmLabel: String = if (isBlocking) "Entendido · Salir" else "Entendido",
    onDismiss: () -> Unit,
) {
    val confirmFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        runCatching { confirmFocusRequester.requestFocus() }
    }

    Dialog(
        onDismissRequest = { if (!isBlocking) onDismiss() },
        properties =
            DialogProperties(
                dismissOnBackPress = !isBlocking,
                dismissOnClickOutside = false,
            ),
    ) {
        Column(
            modifier =
                Modifier.widthIn(min = 300.dp, max = 460.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xF51A1306))
                    .border(2.dp, Color(0xFFF59E0B), RoundedCornerShape(18.dp))
                    .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    painter = painterResource(CoreR.drawable.ic_alert_circle),
                    contentDescription = null,
                    tint = Color(0xFFFBBF24),
                    modifier = Modifier.size(24.dp),
                )
                Box(
                    modifier =
                        Modifier.clip(RoundedCornerShape(8.dp))
                            .background(Color(0x33F59E0B))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text =
                            if (isBlocking) "ADVERTENCIA · LÍMITE DE PANTALLAS"
                            else "AVISO DEL SERVIDOR",
                        color = Color(0xFFFCD34D),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Text(
                text = header.ifBlank { "Límite de pantallas alcanzado" },
                color = Color(0xFFFDE68A),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )

            Text(
                text = message.ifBlank {
                    "Se detectó otra pantalla activa en tu cuenta. La reproducción se ha bloqueado."
                },
                color = Color(0xFFF8FAFC),
                fontSize = 15.sp,
                lineHeight = 21.sp,
            )

            Spacer(Modifier.height(4.dp))

            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().focusRequester(confirmFocusRequester),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFF59E0B),
                        contentColor = Color(0xFF140D02),
                    ),
            ) {
                Text(text = confirmLabel, fontWeight = FontWeight.Bold)
            }
        }
    }
}
