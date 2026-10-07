package cn.alice.timer.monitor

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import cn.alice.timer.core.Observation
import cn.alice.timer.core.ObservationKind
import cn.alice.timer.graph
import cn.alice.timer.notification.NotificationFactory
import kotlinx.coroutines.*

class MonitorService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val screen = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_OFF) scope.launch {
                graph.sync(Observation(ObservationKind.LEFT, System.currentTimeMillis()))
            }
        }
    }
    override fun onCreate() {
        super.onCreate()
        val notification = graph.notifications.monitoring(graph.store.value)
        if (Build.VERSION.SDK_INT >= 34) startForeground(NotificationFactory.MONITOR_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(NotificationFactory.MONITOR_ID, notification)
        graph.serviceRunning.value = true
        ContextCompat.registerReceiver(this, screen, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED)
        scope.launch {
            var lastStatus = ""
            while (isActive) {
                if (!graph.store.value.monitoringEnabled || !graph.store.value.onboardingSeen || !Permissions.read(this@MonitorService).usage) {
                    stopSelf(); break
                }
                runCatching { graph.sync() }.onFailure { graph.monitorError.value = "监测暂不可用：${it.javaClass.simpleName}" }
                val data = graph.store.value
                val status = "${data.cycle.phase}/${data.cycle.gameForeground}/${data.gameLabel}"
                if (status != lastStatus) { graph.notifications.updateMonitoring(data); lastStatus = status }
                delay(2_000)
            }
        }
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() {
        scope.cancel()
        runCatching { unregisterReceiver(screen) }
        graph.serviceRunning.value = false
        super.onDestroy()
    }
    companion object {
        fun tryStart(context: Context): Boolean {
            val graph = context.graph
            if (!graph.store.value.onboardingSeen || !graph.store.value.monitoringEnabled || !Permissions.read(context).usage) return false
            return runCatching {
                ContextCompat.startForegroundService(context, Intent(context, MonitorService::class.java))
                graph.monitorError.value = ""; true
            }.getOrElse {
                graph.monitorError.value = "系统暂未允许启动后台监测，请打开应用恢复"
                false
            }
        }
        fun stop(context: Context) { context.stopService(Intent(context, MonitorService::class.java)) }
    }
}
