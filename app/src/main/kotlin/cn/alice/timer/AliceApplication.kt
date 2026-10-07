package cn.alice.timer

import android.app.Application

class AliceApplication : Application() {
    lateinit var graph: AppGraph
        private set
    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
    }
}
