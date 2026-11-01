package com.utkusuer.hatirlatici

import android.app.Application
import com.utkusuer.hatirlatici.di.AppContainer
import com.utkusuer.hatirlatici.notification.NotificationChannels

class HatirlaticiApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        NotificationChannels.register(this)
        container.reminderSync.schedulePeriodic()
        container.reminderSync.refreshNow()
    }
}
