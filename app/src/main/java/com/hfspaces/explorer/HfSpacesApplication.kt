package com.hfspaces.explorer

import android.app.Application
import kotlinx.coroutines.launch

open class HfSpacesApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = createContainer()
        registerActivityLifecycleCallbacks(container.visibility)
        container.startSystemIntegrations()
        container.applicationScope.launch { container.uploadStaging.prune() }
    }

    protected open fun createContainer(): AppContainer = AppContainer(this)
}
