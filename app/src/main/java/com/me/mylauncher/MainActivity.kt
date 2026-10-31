package com.me.mylauncher

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.me.mylauncher.navigation.LauncherHome
import com.me.mylauncher.ui.theme.MyLauncherTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        // Prevent the keyboard from resizing or panning the Activity.
        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        )

        setContent {
            MyLauncherTheme {
                LauncherHome()
            }
        }
    }
}