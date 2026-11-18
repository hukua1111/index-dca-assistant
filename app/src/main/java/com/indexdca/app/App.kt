package com.indexdca.app

import android.app.Application

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        AppStore.init(this)
    }
}
