package com.myapp.autoscroll

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val prefs = getSharedPreferences("prefs", Context.MODE_PRIVATE)
        status = findViewById(R.id.status)

        bind(prefs, R.id.seekMin, R.id.lblMin, "minSec", 8, 1) { "Kam se kam wait: $it sec" }
        bind(prefs, R.id.seekMax, R.id.lblMax, "maxSec", 15, 1) { "Zyada se zyada wait: $it sec" }
        bind(prefs, R.id.seekTimer, R.id.lblTimer, "timerMin", 0, 5) {
            if (it == 0) "Auto stop timer: Off" else "Auto stop timer: $it min"
        }

        val sw = findViewById<Switch>(R.id.swTikTok)
        sw.isChecked = prefs.getBoolean("tiktokOnly", true)
        sw.setOnCheckedChangeListener { _, c -> prefs.edit().putBoolean("tiktokOnly", c).apply() }

        findViewById<Button>(R.id.btnAccess).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        findViewById<Button>(R.id.btnBubble).setOnClickListener {
            val s = ScrollService.instance
            if (s == null) Toast.makeText(this, "Pehle Accessibility me service ON karo", Toast.LENGTH_LONG).show()
            else s.toggleBubble()
        }
    }

    override fun onResume() {
        super.onResume()
        status.text = if (ScrollService.instance != null)
            "✅ Service ON hai. TikTok kholo aur floating ▶ dabao."
        else
            "❌ Service OFF hai. Neeche button dabao, 'TikTok AutoScroller' dhundho aur ON karo."
    }

    private fun bind(p: SharedPreferences, seekId: Int, labelId: Int, key: String, def: Int, step: Int, fmt: (Int) -> String) {
        val sb = findViewById<SeekBar>(seekId)
        val tv = findViewById<TextView>(labelId)
        sb.progress = p.getInt(key, def) / step
        tv.text = fmt(sb.progress * step)
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar, progress: Int, fromUser: Boolean) {
                val v = progress * step
                p.edit().putInt(key, v).apply()
                tv.text = fmt(v)
            }
            override fun onStartTrackingTouch(s: SeekBar) {}
            override fun onStopTrackingTouch(s: SeekBar) {}
        })
    }
}
