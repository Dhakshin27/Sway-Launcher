package com.me.mylauncher.data

import android.app.Application

class LauncherApp : Application() {

    lateinit var appRepository: AppRepository
        private set

    override fun onCreate() {
        super.onCreate()

        appRepository = AppRepository.get(this)

        appRepository.start()
    }
}