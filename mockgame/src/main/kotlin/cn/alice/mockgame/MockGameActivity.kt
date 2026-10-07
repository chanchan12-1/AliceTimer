package cn.alice.mockgame

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MockGameActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 90, 40, 40) }
        layout.addView(TextView(this).apply {
            text = "这是测试空壳，不是蔚蓝档案\n\n$packageName\n\n只用于测试前后台切换与计时通知。\n没有游戏内容，不联网。"
            textSize = 22f
        })
        layout.addView(Button(this).apply {
            text = "同一应用内切换页面（不应开始计时）"
            setOnClickListener { startActivity(Intent(this@MockGameActivity, SecondActivity::class.java)) }
        })
        setContentView(layout)
    }
}
class SecondActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply { text = "空壳 App 的第二个页面\n按返回键回到第一页"; textSize = 24f; setPadding(40, 90, 40, 40) })
    }
}
