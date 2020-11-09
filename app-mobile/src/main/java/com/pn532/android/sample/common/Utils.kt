package com.pn532.android.sample.common

import android.content.Intent
import android.content.IntentFilter

private const val ACTION_HCE_DATA = "com.pn532.android.sample.common.ACTION_HCE_DATA"
private const val EXTRA_HCE_DATA = "com.pn532.android.sample.common.EXTRA_HCE_DATA"

fun buildHceMsgIntentFilter() = IntentFilter(ACTION_HCE_DATA)

fun resolveHceMsg(intent: Intent?) = if (intent == null || intent.action != ACTION_HCE_DATA) null
else intent.getStringExtra(EXTRA_HCE_DATA)

fun buildHceMsgIntent(msg: String) = Intent(ACTION_HCE_DATA).apply {
    putExtra(EXTRA_HCE_DATA, msg)
}
