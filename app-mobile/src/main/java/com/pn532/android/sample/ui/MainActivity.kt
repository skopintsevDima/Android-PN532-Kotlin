package com.pn532.android.sample.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.pn532.android.sample.R
import com.pn532.android.sample.common.buildHceMsgIntentFilter
import com.pn532.android.sample.common.resolveHceMsg
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {
    private val hceMsgReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val hceMsg = resolveHceMsg(intent)
            hceMsgView.text = hceMsg
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        registerReceiver(hceMsgReceiver, buildHceMsgIntentFilter())
    }

    override fun onDestroy() {
        unregisterReceiver(hceMsgReceiver)
        super.onDestroy()
    }
}