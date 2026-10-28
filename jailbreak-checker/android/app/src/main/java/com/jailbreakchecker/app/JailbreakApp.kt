package com.jailbreakchecker.app

import android.app.Application
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.svg.SvgDecoder
import com.jailbreakchecker.app.ai.AiWorker

class JailbreakApp : Application(), SingletonImageLoader.Factory {

    override fun onCreate() {
        super.onCreate()
        AiWorker.createChannel(this)
        AiWorker.schedule(this)
    }

    override fun newImageLoader(context: android.content.Context): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(SvgDecoder.Factory()) }
            .build()
}
