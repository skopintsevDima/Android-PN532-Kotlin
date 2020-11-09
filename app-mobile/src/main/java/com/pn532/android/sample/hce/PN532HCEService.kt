package com.pn532.android.sample.hce

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import android.util.Log
import com.pn532.android.core.*
import com.pn532.android.sample.common.buildHceMsgIntent

class PN532HCEService: HostApduService() {
    private var isAppSelected = false
    private val data: ByteArray = LOREM_IPSUM.toByteArray()
    private var remainingData: ByteArray? = null

    override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray {
        if (commandApdu == null) return byteArrayOf()
        Log.d(TAG, "Received APDU: [${bytesToHex(commandApdu)}]")
        return when {
            isSelectAidAPDU(commandApdu) -> {
                isAppSelected = true
                Log.d(TAG, "Application selected!")
                APDU_SUCCESS
            }
            isAppSelected && isGetDataAPDU(commandApdu) -> {
                val responseAPDU = data.let {
                    if (it.size <= APDU_MAX_RESPONSE_DATA_LEN) it + APDU_SUCCESS
                    else {
                        remainingData = it.takeLast(it.size - APDU_MAX_RESPONSE_DATA_LEN).toByteArray()
                        it.take(APDU_MAX_RESPONSE_DATA_LEN).toByteArray() + APDU_SUCCESS_MORE_DATA
                    }
                }
                Log.d(TAG, "GET DATA response (HEX) = ${bytesToHex(responseAPDU)}")
                responseAPDU
            }
            isAppSelected && isGetMoreDataAPDU(commandApdu) -> {
                val responseAPDU = if (remainingData != null) with (remainingData!!) {
                    if (size <= APDU_MAX_RESPONSE_DATA_LEN) {
                        (this + APDU_SUCCESS).also { remainingData = null }
                    } else {
                        remainingData = takeLast(size - APDU_MAX_RESPONSE_DATA_LEN).toByteArray()
                        take(APDU_MAX_RESPONSE_DATA_LEN).toByteArray() + APDU_SUCCESS_MORE_DATA
                    }
                } else APDU_SUCCESS
                Log.d(TAG, "GET MORE DATA response (HEX) = ${bytesToHex(responseAPDU)}")
                responseAPDU
            }
            isAppSelected && isSendMsgAPDU(commandApdu) -> {
                val msg = String(commandApdu.takeLast(commandApdu.size - 4).toByteArray())
                Log.d(TAG, "Received message: $msg")
                onCommunicationSequenceEnd(msg)
                APDU_SUCCESS
            }
            else -> APDU_FAILURE
        }
    }

    private fun onCommunicationSequenceEnd(messageFromIotDevice: String) {
        sendBroadcast(buildHceMsgIntent(messageFromIotDevice))
    }

    override fun onDeactivated(reason: Int) {
        Log.d(TAG, "Deactivated, reason = $reason")
    }

    private fun isSelectAidAPDU(commandAPDU: ByteArray) = commandAPDU.size > 2
            && commandAPDU[0] == BYTE_0
            && commandAPDU[1] == 0xA4.toByte()

    private fun isGetDataAPDU(commandAPDU: ByteArray) = commandAPDU.size >= 4
            && commandAPDU[0] == BYTE_0
            && commandAPDU[1] == 0xCA.toByte()
            && commandAPDU[2] == BYTE_0
            && commandAPDU[3] == APDU_GETDATAINS_GETDATA

    private fun isGetMoreDataAPDU(commandAPDU: ByteArray) = commandAPDU.size >= 4
            && commandAPDU[0] == BYTE_0
            && commandAPDU[1] == 0xCA.toByte()
            && commandAPDU[2] == BYTE_0
            && commandAPDU[3] == APDU_GETDATAINS_GETMOREDATA

    private fun isSendMsgAPDU(commandAPDU: ByteArray) = commandAPDU.size > 4
            && commandAPDU[0] == BYTE_0
            && commandAPDU[1] == 0xDA.toByte()
            && commandAPDU[2] == BYTE_0
            && commandAPDU[3] == APDU_SENDDATAINS_SENDMSG

    companion object {
        private const val TAG = "HCE service"
    }
}