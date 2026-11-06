package com.me.mylauncher

import android.app.role.RoleManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.me.mylauncher.navigation.LauncherHome
import com.me.mylauncher.ui.theme.MyLauncherTheme

class MainActivity : ComponentActivity() {

    private val homeRoleLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            // Android will now use the selected default Home app.
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        )

        setContent {
            MyLauncherTheme {
                LauncherHome()
            }
        }

        requestHomeRole()
    }

    private fun requestHomeRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

            val roleManager = getSystemService(RoleManager::class.java)

            if (
                roleManager.isRoleAvailable(RoleManager.ROLE_HOME) &&
                !roleManager.isRoleHeld(RoleManager.ROLE_HOME)
            ) {
                homeRoleLauncher.launch(
                    roleManager.createRequestRoleIntent(
                        RoleManager.ROLE_HOME
                    )
                )
            }
        }
    }
}