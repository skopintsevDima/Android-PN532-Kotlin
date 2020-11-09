package com.pn532.android.app_iot.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.things.pio.PeripheralManager
import com.pn532.android.app_iot.R
import com.pn532.android.app_iot.nfc.PN532I2CNfcManager

class HomeActivity : AppCompatActivity() {
    private lateinit var nfcManager: PN532I2CNfcManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initNFC()
    }

    private fun initNFC() {
        nfcManager = PN532I2CNfcManager(PeripheralManager.getInstance())
        nfcManager.launchNfcReader()
    }

    override fun onDestroy() {
        nfcManager.stopNfcReader()
        nfcManager.closeNfc()
        super.onDestroy()
    }
}
