package com.goat.kharcha

import android.app.Application
import com.goat.kharcha.data.Repo
import com.goat.kharcha.service.PaymentListener
import com.goat.kharcha.service.Prompts
import com.goat.kharcha.service.RecapWorker

class KharchaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Repo.init(this)
        Prompts.createChannels(this)
        RecapWorker.schedule(this, Repo.settings.value.recap)
        PaymentListener.rebind(this)
    }
}
