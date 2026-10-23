package com.example

import android.app.Application
import com.example.data.database.AppDatabase
import com.example.data.database.AppRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class SmartClickerApp : Application() {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database by lazy { AppDatabase.getDatabase(this, applicationScope) }

    val repository by lazy {
        AppRepository(
            pointDao = database.pointDao(),
            roiDao = database.roiDao(),
            macroDao = database.macroDao(),
            scriptDao = database.scriptDao()
        )
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        com.example.engine.SmartClipboardManager.init(this)
        com.example.engine.ExecutionManager.init(this, repository)
    }

    companion object {
        var instance: SmartClickerApp? = null
            private set
    }
}
