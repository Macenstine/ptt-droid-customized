package com.github.devapro.pttdroid.features.settings

import androidx.compose.runtime.Composable

actual val canScanRelayQr: Boolean = false

@Composable
actual fun RelayQrScanner(onScanned: (String) -> Unit, onDismiss: () -> Unit) {
    // Never shown: the Scan button is hidden while canScanRelayQr is false.
}
