package cn.alice.timer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import cn.alice.timer.monitor.MonitorService
import cn.alice.timer.ui.AliceApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        graph.initialize()
        setContent { AliceApp(graph) }
    }
    override fun onResume() {
        super.onResume()
        graph.refreshPermissions()
        MonitorService.tryStart(this)
        graph.initialize()
    }
}
