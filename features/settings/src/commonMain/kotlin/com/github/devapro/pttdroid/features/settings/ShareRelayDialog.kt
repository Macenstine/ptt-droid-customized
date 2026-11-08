package com.github.devapro.pttdroid.features.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.github.devapro.pttdroid.data.qr.QrMatrix
import com.github.devapro.pttdroid.data.settings.AppSettings
import com.github.devapro.pttdroid.data.settings.RelayLink
import com.github.devapro.pttdroid.shared.resources.*
import org.jetbrains.compose.resources.stringResource

/**
 * Shows the *saved* custom relay as a QR code another phone can scan.
 *
 * Built from [settings] (what is on disk), not from the form being edited, so the code always
 * matches a relay this device actually uses. The access token is left out unless the user turns
 * the switch on, because anyone who can see the code could then use the relay.
 */
@Composable
internal fun ShareRelayDialog(
    settings: AppSettings,
    onDismiss: () -> Unit,
) {
    var includeToken by remember { mutableStateOf(false) }
    val hasToken = settings.accessToken.isNotBlank()
    val link = remember(settings, includeToken) { RelayLink.from(settings, includeToken && hasToken) }
    val matrix = remember(link) { link?.let { QrMatrix.encode(it.toUri()) } }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.settings_share_close)) }
        },
        title = { Text(stringResource(Res.string.settings_share_title)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (matrix != null) {
                    QrImage(matrix, modifier = Modifier.size(240.dp))
                    Text(
                        text = stringResource(
                            Res.string.settings_share_relay,
                            "${settings.customHost}:${settings.customPort}",
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    Text(stringResource(Res.string.settings_share_failed))
                }
                if (hasToken) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = includeToken,
                                role = Role.Switch,
                                onValueChange = { includeToken = it },
                            ),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(Res.string.settings_share_include_token),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                text = stringResource(Res.string.settings_share_include_token_summary),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = includeToken, onCheckedChange = null)
                    }
                }
            }
        },
    )
}

/** Dark modules on a white square with a four-module quiet zone, which scanners need in any theme. */
@Composable
private fun QrImage(matrix: List<List<Boolean>>, modifier: Modifier = Modifier) {
    val quiet = 4
    val cells = matrix.size + quiet * 2
    Box(modifier = modifier.background(Color.White)) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val cell = size.minDimension / cells
            for (r in matrix.indices) {
                for (c in matrix[r].indices) {
                    if (matrix[r][c]) {
                        drawRect(
                            color = Color.Black,
                            topLeft = Offset((c + quiet) * cell, (r + quiet) * cell),
                            // A hair over one cell so rounding never leaves seams between modules.
                            size = Size(cell + 0.5f, cell + 0.5f),
                        )
                    }
                }
            }
        }
    }
}
