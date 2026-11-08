package com.github.devapro.pttdroid.features.settings

import androidx.compose.runtime.Composable

/**
 * Whether this platform can scan a relay QR code with a camera. Only Android does; desktop and
 * iOS hide the button rather than show one that cannot do anything.
 */
expect val canScanRelayQr: Boolean

/**
 * A full-screen camera scanner. Calls [onScanned] once with the raw text of the first QR code it
 * reads, or [onDismiss] if the user backs out (or camera access is refused and they close it).
 *
 * It only reads text: what the text means, and whether to trust it, is [RelayLink]'s and the
 * confirmation dialog's business.
 */
@Composable
expect fun RelayQrScanner(onScanned: (String) -> Unit, onDismiss: () -> Unit)
