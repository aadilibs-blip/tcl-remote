package com.aadil.tclremote

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.aadil.tclremote.net.Keys
import com.aadil.tclremote.ui.RemoteApp

class MainActivity : ComponentActivity() {
    private val vm: RemoteViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF0C0D10)) {
                    RemoteApp(vm)
                }
            }
        }
    }

    // The phone's own volume buttons control the TV while the remote is showing.
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (vm.isRemote()) {
            when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> { vm.press(Keys.VOLUME_UP); return true }
                KeyEvent.KEYCODE_VOLUME_DOWN -> { vm.press(Keys.VOLUME_DOWN); return true }
            }
        }
        return super.onKeyDown(keyCode, event)
    }
}
