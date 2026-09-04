package com.rork.cipher

import android.app.Application
import com.rork.cipher.data.CipherRepository
import com.rork.cipher.data.Push

class CipherApplication : Application() {

    val repository: CipherRepository by lazy { CipherRepository(this) }

    override fun onCreate() {
        super.onCreate()
        // Firebase is brought up from environment values rather than a config
        // file in the project, so push works without checking credentials in.
        Push.start(this)
    }
}
