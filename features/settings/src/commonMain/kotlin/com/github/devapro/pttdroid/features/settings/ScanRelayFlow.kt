package com.github.devapro.pttdroid.features.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.devapro.pttdroid.data.settings.CertificatePin
import com.github.devapro.pttdroid.data.settings.RelayLink
import com.github.devapro.pttdroid.data.settings.ServerMode
import com.github.devapro.pttdroid.features.api.settings.SettingsAction
import com.github.devapro.pttdroid.features.api.settings.SettingsEdit
import com.github.devapro.pttdroid.shared.resources.*
import org.jetbrains.compose.resources.stringResource

/**
 * Scan → validate → confirm → fill the form.
 *
 * A QR code can say anything, so nothing it carries is applied until the user has seen what it
 * would change, and applying only edits the form: saving (and so reconnecting) stays the user's
 * explicit Save press. Text that is not a valid [RelayLink] never reaches the form.
 *
 * Fields the code does not carry are cleared, not kept: a pin or token left over from the
 * previous relay would be wrong for the new one, and a stale token would be sent to a server it
 * was never meant for.
 *
 * Renders nothing unless [active]; [onClose] is called when the flow ends, however it ends.
 */
@Composable
internal fun ScanRelayFlow(
    active: Boolean,
    onClose: () -> Unit,
    onAction: (SettingsAction) -> Unit,
) {
    var scanned by remember { mutableStateOf<String?>(null) }
    if (!active) return

    val text = scanned
    if (text == null) {
        RelayQrScanner(onScanned = { scanned = it }, onDismiss = onClose)
        return
    }

    val link = RelayLink.parse(text)
    val finish = {
        scanned = null
        onClose()
    }
    if (link == null) {
        AlertDialog(
            onDismissRequest = finish,
            confirmButton = {
                TextButton(onClick = finish) { Text(stringResource(Res.string.settings_scan_ok)) }
            },
            text = { Text(stringResource(Res.string.settings_scan_invalid)) },
        )
        return
    }

    val yes = stringResource(Res.string.settings_scan_yes)
    val no = stringResource(Res.string.settings_scan_no)
    val included = stringResource(Res.string.settings_scan_included)
    val none = stringResource(Res.string.settings_scan_none)
    AlertDialog(
        onDismissRequest = finish,
        title = { Text(stringResource(Res.string.settings_scan_confirm_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(Res.string.settings_scan_confirm_relay, "${link.host}:${link.port}"),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(stringResource(Res.string.settings_scan_confirm_encrypted, if (link.tls) yes else no))
                Text(stringResource(Res.string.settings_scan_confirm_pin, if (link.pin.isEmpty()) none else included))
                Text(stringResource(Res.string.settings_scan_confirm_token, if (link.token.isEmpty()) none else included))
                Text(
                    stringResource(Res.string.settings_scan_confirm_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                fun edit(e: SettingsEdit) = onAction(SettingsAction.EditSettings(e))
                edit(SettingsEdit.Mode(ServerMode.CUSTOM))
                edit(SettingsEdit.Address("${link.host}:${link.port}"))
                edit(SettingsEdit.Tls(link.tls))
                edit(SettingsEdit.Fingerprint(CertificatePin.format(link.pin)))
                edit(SettingsEdit.AccessToken(link.token))
                finish()
            }) { Text(stringResource(Res.string.settings_scan_apply)) }
        },
        dismissButton = {
            TextButton(onClick = finish) { Text(stringResource(Res.string.settings_scan_cancel)) }
        },
    )
}
