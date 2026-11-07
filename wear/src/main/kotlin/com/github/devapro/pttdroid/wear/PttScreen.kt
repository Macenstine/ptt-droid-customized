package com.github.devapro.pttdroid.wear

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.github.devapro.pttdroid.wear.protocol.WearState
import com.github.devapro.pttdroid.wear.protocol.WearStatus

private const val MIN_CHANNEL = 1
private const val MAX_CHANNEL = 99

private val PILL_WIDTH = 176.dp
private val PILL_HEIGHT = 92.dp
private val STEP_BUTTON_SIZE = 40.dp

/** Extra room between the channel stepper and the talk button, beyond the column's own spacing. */
private val STEPPER_GAP = 10.dp

/** The dark tone the phone's talk button uses for text and rim (`PTTButton`'s `OnSignal`). */
private val ON_SIGNAL = Color(0xFF08111A)

/** How far toward [ON_SIGNAL] the border moves from the status colour. */
private const val BORDER_DARKNESS = 0.55f

/**
 * The whole watch UI: the channel stepper, one big hold-to-talk pill beneath it, and who is
 * talking below that.
 *
 * The word and colour come from [WearStatus], which mirrors the phone's `PttUiStatus`, so
 * "HOLD / WAIT / ON AIR / BUSY" mean here exactly what they mean on the phone.
 */
@Composable
fun PttScreen(
    state: WearState?,
    phoneConnected: Boolean,
    onTalkDown: () -> Unit,
    onTalkUp: () -> Unit,
    onChannel: (Int) -> Unit,
) {
    val status = if (phoneConnected) state?.status ?: WearStatus.OFFLINE else WearStatus.OFFLINE
    val word = when {
        !phoneConnected -> stringResource(R.string.no_phone)
        else -> status.word
    }
    val channel = state?.channel ?: MIN_CHANNEL
    val live = phoneConnected && status.isControlLive

    MaterialTheme {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ChannelStepper(
                    channel = channel,
                    enabled = phoneConnected && state != null,
                    onChannel = onChannel,
                )
                Spacer(Modifier.height(STEPPER_GAP))
                TalkButton(
                    status = status,
                    word = word,
                    live = live,
                    onTalkDown = onTalkDown,
                    onTalkUp = onTalkUp,
                )
                // Who is talking; a blank line keeps the layout from jumping when it appears.
                Text(
                    text = state?.floorHolderName?.takeIf { phoneConnected } ?: " ",
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun TalkButton(
    status: WearStatus,
    word: String,
    live: Boolean,
    onTalkDown: () -> Unit,
    onTalkUp: () -> Unit,
) {
    // Read through rememberUpdatedState so the gesture detector is never re-keyed mid-press:
    // tearing it down while the finger is still on the button would drop the release and strand
    // the phone's floor with its microphone open.
    val liveNow by rememberUpdatedState(live)
    val down by rememberUpdatedState(onTalkDown)
    val up by rememberUpdatedState(onTalkUp)

    val container = Color(status.argb)
    val content = if (status == WearStatus.TRANSMITTING || status == WearStatus.OFFLINE) Color.White else Color.Black

    // A pill: a wide, fully rounded rectangle. Its border is the status colour pulled toward the
    // phone button's dark "on signal" tone, so it reads as a deeper shade of whatever state the
    // button is in, as the rim on the phone does.
    val shape = RoundedCornerShape(percent = 50)
    val border = lerp(container, ON_SIGNAL, BORDER_DARKNESS)

    Box(
        modifier = Modifier
            .size(width = PILL_WIDTH, height = PILL_HEIGHT)
            .clip(shape)
            .background(container)
            .border(width = 3.dp, color = border, shape = shape)
            .semantics { contentDescription = word }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        if (liveNow) {
                            down()
                            try {
                                tryAwaitRelease()
                            } finally {
                                up()
                            }
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = word,
            color = content,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun ChannelStepper(channel: Int, enabled: Boolean, onChannel: (Int) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        StepButton(
            label = "−",
            description = stringResource(R.string.channel_down),
            enabled = enabled && channel > MIN_CHANNEL,
        ) { onChannel(channel - 1) }
        Text(text = stringResource(R.string.channel_label, channel), fontSize = 16.sp)
        StepButton(
            label = "+",
            description = stringResource(R.string.channel_up),
            enabled = enabled && channel < MAX_CHANNEL,
        ) { onChannel(channel + 1) }
    }
}

@Composable
private fun StepButton(label: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(STEP_BUTTON_SIZE)
            .semantics { contentDescription = description },
        colors = ButtonDefaults.secondaryButtonColors(),
    ) {
        Text(text = label, fontSize = 20.sp)
    }
}
