package com.aadil.tclremote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Input
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aadil.tclremote.Phase
import com.aadil.tclremote.RemoteViewModel
import com.aadil.tclremote.UiState
import com.aadil.tclremote.net.Keys

private val Body = Color(0xFF1A1C20)
private val KeyDark = Color(0xFF2B2E34)
private val KeyLight = Color(0xFFD5D7DC)
private val KeyText = Color(0xFFECECEF)
private val Ink = Color(0xFF1A1C20)
private val RingGray = Color(0xFFA4A7AD)
private val PillBg = Color(0xFFF2F2F4)
private val Muted = Color(0xFFA0A3A9)
private val Danger = Color(0xFFFF8A80)
private val PowerRed = Color(0xFFFF6B6B)
private val Blue = Color(0xFF7CC4FF)
private val Coral = Color(0xFFFF7A6B)

@Composable
fun RemoteApp(vm: RemoteViewModel = viewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.start() }
    when (ui.phase) {
        Phase.Pick -> PickScreen(ui, vm)
        Phase.Connecting -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                CircularProgressIndicator()
                Text("Connecting…", color = Muted)
            }
        }
        Phase.NeedPin -> PinScreen(ui, vm)
        Phase.Remote -> RemoteScreen(ui, vm)
    }
}

// ---------------------------------------------------------------- Remote

@Composable
private fun RemoteScreen(ui: UiState, vm: RemoteViewModel) {
    val haptic = LocalHapticFeedback.current
    val press: (Int) -> Unit = { code ->
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        vm.press(code)
    }
    val launch: (String) -> Unit = { url ->
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        vm.openLink(url)
    }

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TopBar(ui.name ?: ui.host ?: "TV", onSwitch = vm::disconnect)

        Column(
            Modifier
                .widthIn(max = 380.dp)
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .clip(RoundedCornerShape(38.dp))
                .background(Body)
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ThreeColumns(
                left = { KeyButton("Mute", icon = Icons.AutoMirrored.Filled.VolumeOff) { press(Keys.VOLUME_MUTE) } },
                middle = { Box(Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF0C0D10))) },
                right = { KeyButton("Power", icon = Icons.Filled.PowerSettingsNew, tint = PowerRed) { press(Keys.POWER) } }
            )
            ThreeColumns(
                left = { KeyButton("Favorites", icon = Icons.Filled.BookmarkBorder) { press(Keys.BOOKMARK) } },
                middle = { KeyButton("Assistant", icon = Icons.Filled.Mic, container = KeyLight, tint = Ink) { press(Keys.ASSIST) } },
                right = { KeyButton("Settings", icon = Icons.Filled.Settings) { press(Keys.SETTINGS) } }
            )

            DPad(press)

            ThreeColumns(
                left = { KeyButton("Back", icon = Icons.AutoMirrored.Filled.ArrowBack) { press(Keys.BACK) } },
                middle = { KeyButton("Home", icon = Icons.Filled.Home, container = KeyLight, tint = Ink) { press(Keys.HOME) } },
                right = { KeyButton("Live TV", icon = Icons.Filled.Tv) { press(Keys.TV) } }
            )

            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    KeyButton("Input source", icon = Icons.AutoMirrored.Filled.Input) { press(Keys.TV_INPUT) }
                    KeyButton("Menu", icon = Icons.Filled.Menu) { press(Keys.MENU) }
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    VolumePill(onUp = { press(Keys.VOLUME_UP) }, onDown = { press(Keys.VOLUME_DOWN) })
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    KeyButton("Channel up", text = "P+") { press(Keys.CHANNEL_UP) }
                    KeyButton("Channel down", text = "P−") { press(Keys.CHANNEL_DOWN) }
                }
            }

            ThreeColumns(
                left = { KeyButton("Recent apps", icon = Icons.Filled.Layers, tint = Blue) { press(Keys.APP_SWITCH) } },
                middle = { KeyButton("Info", icon = Icons.Filled.Info) { press(Keys.INFO) } },
                right = { KeyButton("All apps", icon = Icons.Filled.Apps, tint = Coral) { press(Keys.ALL_APPS) } }
            )

            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PillRow(
                    "Netflix", { launch("https://www.netflix.com/browse") },
                    "Prime Video", { launch("https://app.primevideo.com") }
                )
                PillRow(
                    "YouTube", { launch("https://www.youtube.com") },
                    "Live TV", { press(Keys.TV) }
                )
                MediaPill(
                    onRewind = { press(Keys.MEDIA_REWIND) },
                    onPlayPause = { press(Keys.MEDIA_PLAY_PAUSE) },
                    onForward = { press(Keys.MEDIA_FAST_FORWARD) }
                )
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun TopBar(name: String, onSwitch: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .widthIn(max = 380.dp)
            .height(64.dp)
            .padding(start = 22.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(9.dp).clip(CircleShape).background(Color(0xFF3DDC84)))
            Column {
                Text(name, color = KeyText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text("Connected", color = Muted, fontSize = 12.sp)
            }
        }
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color(0xFF1D1F24))
                .tap("Switch TV", onSwitch),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.SwapHoriz, contentDescription = null, tint = KeyText)
        }
    }
}

@Composable
private fun ThreeColumns(
    left: @Composable () -> Unit,
    middle: @Composable () -> Unit,
    right: @Composable () -> Unit
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { left() }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { middle() }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { right() }
    }
}

@Composable
private fun KeyButton(
    label: String,
    icon: ImageVector? = null,
    text: String? = null,
    container: Color = KeyDark,
    tint: Color = KeyText,
    size: Dp = 52.dp,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(container)
            .tap(label, onClick),
        contentAlignment = Alignment.Center
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        } else if (text != null) {
            Text(text, color = tint, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun DPad(press: (Int) -> Unit) {
    Box(
        Modifier
            .size(190.dp)
            .clip(CircleShape)
            .background(RingGray)
    ) {
        Arrow(Icons.Filled.KeyboardArrowUp, "Up", Alignment.TopCenter) { press(Keys.DPAD_UP) }
        Arrow(Icons.Filled.KeyboardArrowDown, "Down", Alignment.BottomCenter) { press(Keys.DPAD_DOWN) }
        Arrow(Icons.Filled.KeyboardArrowLeft, "Left", Alignment.CenterStart) { press(Keys.DPAD_LEFT) }
        Arrow(Icons.Filled.KeyboardArrowRight, "Right", Alignment.CenterEnd) { press(Keys.DPAD_RIGHT) }
        Box(
            Modifier
                .align(Alignment.Center)
                .size(76.dp)
                .clip(CircleShape)
                .background(Color(0xFF14161A))
                .tap("OK") { press(Keys.DPAD_CENTER) },
            contentAlignment = Alignment.Center
        ) {
            Text("OK", color = KeyText, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun BoxScope.Arrow(icon: ImageVector, label: String, align: Alignment, onClick: () -> Unit) {
    Box(
        Modifier
            .align(align)
            .padding(8.dp)
            .size(56.dp)
            .clip(CircleShape)
            .tap(label, onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = Ink, modifier = Modifier.size(30.dp))
    }
}

@Composable
private fun VolumePill(onUp: () -> Unit, onDown: () -> Unit) {
    Column(
        Modifier
            .width(52.dp)
            .height(116.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(KeyDark)
    ) {
        Box(Modifier.weight(1f).fillMaxWidth().tap("Volume up", onUp), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = KeyText)
        }
        Box(Modifier.weight(1f).fillMaxWidth().tap("Volume down", onDown), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Remove, contentDescription = null, tint = KeyText)
        }
    }
}

@Composable
private fun PillRow(left: String, onLeft: () -> Unit, right: String, onRight: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(PillBg)
    ) {
        Box(Modifier.weight(1f).fillMaxSize().tap(left, onLeft), contentAlignment = Alignment.Center) {
            Text(left, color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Box(
            Modifier
                .width(1.dp)
                .fillMaxHeight()
                .padding(vertical = 10.dp)
                .background(Color(0xFFC8CAD0))
        )
        Box(Modifier.weight(1f).fillMaxSize().tap(right, onRight), contentAlignment = Alignment.Center) {
            Text(right, color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun MediaPill(onRewind: () -> Unit, onPlayPause: () -> Unit, onForward: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(PillBg)
    ) {
        Box(Modifier.weight(1f).fillMaxSize().tap("Rewind", onRewind), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.FastRewind, contentDescription = null, tint = Ink)
        }
        Box(Modifier.weight(1f).fillMaxSize().tap("Play or pause", onPlayPause), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Ink)
        }
        Box(Modifier.weight(1f).fillMaxSize().tap("Fast forward", onForward), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.FastForward, contentDescription = null, tint = Ink)
        }
    }
}

private fun Modifier.tap(label: String, onClick: () -> Unit): Modifier =
    this
        .semantics(mergeDescendants = true) { contentDescription = label }
        .clickable(role = Role.Button, onClick = onClick)

// ---------------------------------------------------------------- Picker

@Composable
private fun PickScreen(ui: UiState, vm: RemoteViewModel) {
    var manual by remember { mutableStateOf(ui.host.orEmpty()) }

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Choose your TV", style = MaterialTheme.typography.headlineSmall, color = KeyText)
        Text("Your phone and TV need to be on the same Wi-Fi network.", color = Muted)

        if (ui.devices.isEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Text("Looking for TVs…", color = Muted)
            }
        }
        ui.devices.forEach { d ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Body)
                    .clickable { vm.connect(d.host, d.name) }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Icon(Icons.Filled.Tv, contentDescription = null, tint = KeyText)
                Column {
                    Text(d.name, color = KeyText, fontWeight = FontWeight.SemiBold)
                    Text(d.host, color = Muted, fontSize = 13.sp)
                }
            }
        }

        HorizontalDivider(color = KeyDark)

        Text("Or enter the TV's IP address", color = KeyText)
        OutlinedTextField(
            value = manual,
            onValueChange = { manual = it },
            label = { Text("IP address, e.g. 192.168.1.20") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth()
        )
        Button(onClick = { vm.connect(manual) }, enabled = manual.isNotBlank()) { Text("Connect") }
        if (manual.isNotBlank()) {
            TextButton(onClick = { vm.repair(manual) }) { Text("Pair again") }
        }
        ui.message?.let { Text(it, color = Danger) }
    }
}

// ---------------------------------------------------------------- Pairing

@Composable
private fun PinScreen(ui: UiState, vm: RemoteViewModel) {
    var code by remember { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Pair with your TV", style = MaterialTheme.typography.headlineSmall, color = KeyText)
        Text(
            "A 6-character code is showing on your TV screen. Type it here. " +
                "You only need to do this once.",
            color = Muted
        )
        OutlinedTextField(
            value = code,
            onValueChange = { v -> code = v.uppercase().filter { it in "0123456789ABCDEF" }.take(6) },
            label = { Text("Code on TV") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Button(onClick = { vm.submitCode(code) }, enabled = code.length == 6) { Text("Pair") }
        TextButton(onClick = vm::cancelPairing) { Text("Cancel") }
        ui.message?.let { Text(it, color = Danger) }
    }
}
