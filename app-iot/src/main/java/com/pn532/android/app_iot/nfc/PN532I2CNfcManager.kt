package com.pn532.android.app_iot.nfc

import android.util.Log
import com.google.android.things.pio.I2cDevice
import com.google.android.things.pio.PeripheralManager
import com.pn532.android.core.*
import java.io.IOException
import kotlin.experimental.and
import kotlin.experimental.inv

class PN532I2CNfcManager(
    peripheralManager: PeripheralManager
) {
    private var nfcReader: I2cDevice? = null
    private var isNfcScannerEnabled = false
    private var command: Byte? = null

    init {
        try {
            val deviceList: List<String> = peripheralManager.i2cBusList
            when {
                deviceList.isEmpty() -> {
                    Log.d(TAG, "No I2C bus available on this device.")
                }
                deviceList.count() > 1 -> {
                    Log.d(TAG, "There are a few devices connected via I2C - it's impossible to recognize PN532 reader. Device list = $deviceList")
                }
                else -> {
                    val deviceName = deviceList[0]
                    nfcReader = peripheralManager.openI2cDevice(deviceName, NFC_READER_RPI3B_I2C_ADDRESS)
                    Log.d(TAG, "NFC PN532 reader opened via I2C")
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Unable to access I2C device: ${e.message}")
        }
    }

    fun launchNfcReader() {
        isNfcScannerEnabled = true
        Thread {
            val isSAMConfigCorrect = sendSAMConfigurationCmd()
            if (isSAMConfigCorrect) {
                Log.d(TAG, "SAMConfiguration SUCCEED")
                while (isNfcScannerEnabled) {
                    var buffer = ByteArray(PN532_DETECT_TARGET_BUFFER_SIZE)
                    var readLength = detectPassiveTarget(buffer)
                    if (readLength > 0) {
                        val targetLogicalNumber = buffer[0]
                        Log.d(TAG, "Found an ISO14443A card: target logical number = ${bytesToHex(ByteArray(1) { targetLogicalNumber })}")
                        val result = selectAID(targetLogicalNumber, buildSelectAidAPDU(AID))
                        if (result == 0) {
                            Log.d(TAG, "APP AID SELECTED. Getting data...")
                            buffer = ByteArray(PN532_GET_DATA_BUFFER_SIZE)
                            readLength = getData(targetLogicalNumber, buildGetDataAPDU(APDU_GETDATAINS_GETDATA), buffer)
                            if (readLength > 0) {
                                val userIntentEncryptedRaw = buffer.take(readLength).toByteArray()
                                Log.d(TAG, "Data received")
                                handleData(targetLogicalNumber, userIntentEncryptedRaw)
                            }
                        }
                    }
                    Thread.sleep(PN532_SCANNER_WAIT_TIME_MS)
                }
            }
        }.start()
    }

    private fun handleData(targetLogicalNumber: Byte, data: ByteArray) {
        Log.d(TAG, "handleData: data = (${data.size})${bytesToHex(data)}")
        if (String(data) == LOREM_IPSUM) {
            val msg = MSG_ANSWER_CORRECT
            if (sendData(targetLogicalNumber, buildSendDataAPDU(APDU_SENDDATAINS_SENDMSG, msg.toByteArray())) == 0) {
                Log.d(TAG, "Message via NFC sent: '$msg'")
            }
        }
    }

    fun stopNfcReader() {
        isNfcScannerEnabled = false
    }

    private fun sendSAMConfigurationCmd(): Boolean {
        Log.d(TAG, "Sending SAMConfiguration")
        val command = ByteArray(4)
        command[0] = PN532_CMD_SAMCONFIGURATION
        command[1] = 0x01 // Normal mode
        command[2] = 0x14 // Timeout: 50ms * 20 = 1 second
        command[3] = 0x01 // Use IRQ pin!
        return if (writeCommand(command) != CommandStatus.OK) false
        else {
            val packetBuffer = ByteArray(PN532_PACKET_BUFFER_SIZE) { BYTE_0 }
            readResponse(packetBuffer, PN532_SAM_CONFIG_EXPECTED_LENGTH) == 0
        }
    }

    private fun detectPassiveTarget(buffer: ByteArray): Int {
        Log.d(TAG, "Sending InListPassiveTarget")
        val command = ByteArray(3)
        command[0] = PN532_CMD_INLISTPASSIVETARGET
        command[1] = 1 // Max 1 target/card could be detected at once
        command[2] = PN532_ISO14443A // Card type
        if (writeCommand(command, ByteArray(0)) != CommandStatus.OK) {
            return -1 // Command failed
        }

        // Read data packet
        val packetBuffer = ByteArray(PN532_PACKET_BUFFER_SIZE) { BYTE_0 }
        if (readResponse(packetBuffer, PN532_READ_PASSIVE_TARGET_EXPECTED_LENGTH) < 0) {
            return -1
        }

        // ISO14443A correct response format:
        // b0 Tags Found
        // b1 Tag Number (only one used in this case)
        // b2..3 SENS_RES
        // b4 SEL_RES
        // b5 NFCIDLen
        // b6..NFCIDLen NFCID
        if (packetBuffer[0] != BYTE_1) {
            return -1
        }

        buffer[0] = packetBuffer[0]
        return 1
    }

    private fun selectAID(
        targetLogicalNumber: Byte,
        selectAidAPDU: ByteArray
    ): Int {
        Log.d(TAG, "Sending InDataExchange (SELECT AID APDU = ${bytesToHex(selectAidAPDU)})")
        val command = ByteArray(2 + selectAidAPDU.size)
        command[0] = PN532_CMD_INDATAEXCHANGE
        command[1] = targetLogicalNumber
        for (index in selectAidAPDU.indices) {
            command[2 + index] = selectAidAPDU[index]
        }
        if (writeCommand(command, ByteArray(0)) != CommandStatus.OK) {
            return -1 // Command failed
        }

        // Read data packet
        val packetBuffer = ByteArray(PN532_PACKET_BUFFER_SIZE) { BYTE_0 }
        val readLength = readResponse(packetBuffer, PN532_SELECT_AID_EXPECTED_LENGTH)
        if (readLength < 0) {
            Log.d(TAG, "selectAID: Reading response FAILED.")
            return -1
        }
        Log.d(TAG, "selectAID: Response = ${bytesToHex(packetBuffer.take(readLength).toByteArray())}")

        if (!checkStatus(packetBuffer[0])) {
            Log.d(TAG, "selectAID: Status is FAILURE.")
            return -1
        }

        if (packetBuffer[1] != APDU_SUCCESS[0] || packetBuffer[2] != APDU_SUCCESS[1]) {
            Log.d(TAG, "selectAID: Response != SUCCESS")
            return -1
        }

        return 0
    }

    private fun getData(
        targetLogicalNumber: Byte,
        getDataAPDU: ByteArray,
        buffer: ByteArray
    ): Int {
        Log.d(TAG, "Sending InDataExchange (GET DATA APDU = ${bytesToHex(getDataAPDU)})")
        val command = ByteArray(2 + getDataAPDU.size)
        command[0] = PN532_CMD_INDATAEXCHANGE
        command[1] = targetLogicalNumber
        for (index in getDataAPDU.indices) {
            command[2 + index] = getDataAPDU[index]
        }
        if (writeCommand(command, ByteArray(0)) != CommandStatus.OK) {
            return -1 // Command failed
        }

        // Read data packet
        val packetBuffer = ByteArray(PN532_PACKET_BUFFER_SIZE) { BYTE_0 }
        val readLen = readResponse(packetBuffer, PN532_GET_DATA_EXPECTED_LENGTH)
        if (readLen < 0) {
            Log.d(TAG, "getData: Reading response FAILED.")
            return -1
        }
        Log.d(TAG, "getData: Response = ${bytesToHex(packetBuffer.take(readLen).toByteArray())}")

        if (!checkStatus(packetBuffer[0])){
            Log.d(TAG, "getData: Status is FAILURE.")
            return -1
        }

        if (!(packetBuffer[readLen - 2] == APDU_SUCCESS[0] || packetBuffer[readLen - 2] == APDU_SUCCESS_MORE_DATA[0])) {
            Log.d(TAG, "getData: Response != SUCCESS")
            return -1
        }

        val responseDataLen = readLen - 3
        packetBuffer.copyInto(buffer, 0, 1, readLen - 2)

        if (packetBuffer[readLen - 2] != APDU_SUCCESS_MORE_DATA[0]) return responseDataLen
        else {
            val bufferMoreData = ByteArray(PN532_GET_DATA_BUFFER_SIZE) { BYTE_0 }
            val readMoreLen = getData(
                targetLogicalNumber,
                buildGetDataAPDU(APDU_GETDATAINS_GETMOREDATA),
                bufferMoreData
            )
            Log.d(TAG, "getData: Get more data result = ($readMoreLen)[${bytesToHex(bufferMoreData)}]")
            return if (readMoreLen > 0) {
                bufferMoreData.copyInto(buffer, responseDataLen, 0, readMoreLen)
                responseDataLen + readMoreLen
            } else {
                Log.d(TAG, "getData: GET MORE DATA APDU FAILED")
                -1
            }
        }
    }

    private fun sendData(
        targetLogicalNumber: Byte,
        sendDataAPDU: ByteArray
    ): Int {
        Log.d(TAG, "Sending InDataExchange (SEND DATA APDU = ${bytesToHex(sendDataAPDU)})")
        val command = ByteArray(2 + sendDataAPDU.size)
        command[0] = PN532_CMD_INDATAEXCHANGE
        command[1] = targetLogicalNumber
        for (index in sendDataAPDU.indices) {
            command[2 + index] = sendDataAPDU[index]
        }
        if (writeCommand(command, ByteArray(0)) != CommandStatus.OK) {
            return -1 // Command failed
        }

        // Read data packet
        val packetBuffer = ByteArray(PN532_PACKET_BUFFER_SIZE) { BYTE_0 }
        val readLength = readResponse(packetBuffer, PN532_SEND_DATA_EXPECTED_LENGTH)
        if (readLength < 0) {
            Log.d(TAG, "sendData: Reading response FAILED.")
            return -1
        }
        Log.d(TAG, "sendData: Response = ${bytesToHex(packetBuffer.take(readLength).toByteArray())}")

        if (!checkStatus(packetBuffer[0])) {
            Log.d(TAG, "sendData: Status is FAILURE.")
            return -1
        }

        if (packetBuffer[1] != APDU_SUCCESS[0] || packetBuffer[2] != APDU_SUCCESS[1]) {
            Log.d(TAG, "sendData: Response != SUCCESS")
            return -1
        }

        return 0
    }

    private fun checkStatus(statusByte: Byte): Boolean {
        val errorCode = (statusByte and (1 shl 7).inv().toByte()) and (1 shl 6).inv().toByte() // Set 6th bit of statusByte as 0, turns statusByte
        Log.d(TAG, "checkStatus: Error code = ${bytesToHex(ByteArray(1) { errorCode })}")
        return errorCode == BYTE_0
    }

    fun closeNfc() {
        isNfcScannerEnabled = false
        nfcReader?.close()
    }

    private fun writeCommand(header: ByteArray, body: ByteArray = ByteArray(0)): CommandStatus {
        Log.d(TAG, "writeCommand: Header = [${bytesToHex(header)}], body = [${bytesToHex(body)}]")
        val toSend = ArrayList<Byte>()
        command = header[0]
        try {
            toSend.apply {
                add(PN532_PREAMBLE)
                add(PN532_STARTCODE1)
                add(PN532_STARTCODE2)
            }

            val cmdLen = (header.size + body.size + 1).toByte()
            val cmdLenCheck = (cmdLen.inv() + 1).toByte()
            toSend.apply {
                add(cmdLen)
                add(cmdLenCheck)
                add(PN532_HOSTTOPN532)
            }

            var sum: Byte = PN532_HOSTTOPN532
            (header + body).forEach {
                toSend.add(it)
                sum = (sum.plus(it)).toByte()
            }
            val checksum = (sum.inv() + 1).toByte()
            toSend.add(checksum)
            toSend.add(PN532_POSTAMBLE)

            val bytesToSend = toSend.toByteArray()

            Log.d(TAG, "writeCommand: Sending [${bytesToHex(bytesToSend)}]")
            nfcReader?.write(bytesToSend, bytesToSend.size)
        } catch (e: IOException) {
            Log.d(TAG, "writeCommand: Exception occurred = ${e.message}")
            return CommandStatus.INVALID_ACK
        }
        Log.d(TAG, "writeCommand: Transferring to waitForAck()")
        return waitForAck()
    }

    private fun waitForAck(timeout: Int = PN532_ACK_TIMEOUT_MS): CommandStatus {
        Log.d(TAG, "waitForAck")
        val ackBuffer = ByteArray(7) { BYTE_0 }
        var timer = 0
        var message = ""
        while (true) {
            try {
                nfcReader?.read(ackBuffer, ackBuffer.size)
                if (ackBuffer.any { it != BYTE_0 }) {
                    Log.d(TAG, "waitForAck: Read ${ackBuffer.size} bytes.")
                }
            } catch (e: IOException) {
                message = e.message ?: "IOException(NO MESSAGE)"
            }

            if (ackBuffer[0] and 1 > 0) {
                break
            }

            if (timeout != 0) {
                timer += PN532_WAIT_TIME_MS
                if (timer > timeout) {
                    Log.d(TAG, "waitForAck: Timeout occurred, message = $message")
                    return CommandStatus.TIMEOUT
                }
            }
            Thread.sleep(PN532_WAIT_TIME_MS.toLong())
        }
        for (i in 1 until ackBuffer.size) {
            if (ackBuffer[i] != PN532_ACK[i - 1]) {
                Log.d(TAG, "waitForAck: Invalid Ack.")
                return CommandStatus.INVALID_ACK
            }
        }
        Log.d(TAG, "waitForAck: OK")
        return CommandStatus.OK
    }

    private fun readResponse(buffer: ByteArray, expectedLength: Int, timeout: Int = PN532_READ_TIMEOUT_MS): Int {
        Log.d(TAG, "readResponse")
        val actualLength = expectedLength + 2
        val response = ByteArray(actualLength) { BYTE_0 }
        var timer = 0
        while (true) {
            try {
                nfcReader?.read(response, actualLength)
            } catch (e: IOException) {
                // Nothing to do, timeout will occur if an error has happened.
            }

            if (response[0] and BYTE_1 > 0) {
                break
            }

            if (timeout != 0) {
                timer += PN532_WAIT_TIME_MS
                if (timer > timeout) {
                    Log.d(TAG, "readResponse: Timeout occurred.")
                    return -1
                }
            }
            Thread.sleep(PN532_WAIT_TIME_MS.toLong())
        }
        Log.d(TAG, "readResponse: Response = [${bytesToHex(response)}]")

        var index = 1
        if (PN532_PREAMBLE != response[index++] || PN532_STARTCODE1 != response[index++] || PN532_STARTCODE2 != response[index++]) {
            Log.d(TAG, "readResponse: Bad starting bytes found.")
            return -1
        }

        var length = response[index++]
        val lengthCheck = length.plus(response[index++]).toByte()
        if (lengthCheck.toInt() != 0) {
            Log.d(TAG, "readResponse: Bad length checksum.")
            return -1
        }

        if (command == null) {
            Log.d(TAG, "readResponse: Command is null.")
            return -1
        }
        val cmd = 1.plus(command!!).toByte()

        if (PN532_PN532TOHOST != response[index++] || cmd != response[index++]) {
            Log.d(TAG, "readResponse: Bad command check.")
            return -1
        }

        length = length.minus(2).toByte()
        if (length > expectedLength) {
            Log.d(TAG, "readResponse: Not enough space.")
            return -1
        }

        var sum = PN532_PN532TOHOST.plus(cmd).toByte()

        for (i in 0 until length) {
            buffer[i] = response[index++]
            sum = sum.plus(buffer[i]).toByte()
        }

        val checksum = response[index].plus(sum).toByte()
        if (checksum.toInt() != 0) {
            Log.d(TAG, "readResponse: Bad checksum.")
            return -1
        }
        return length.toInt()
    }

    companion object {
        private const val TAG = "NfcManager"
        private const val NFC_READER_RPI3B_I2C_ADDRESS = 0x24

        private const val PN532_PREAMBLE = BYTE_0
        private const val PN532_STARTCODE1 = BYTE_0
        private const val PN532_STARTCODE2 = 0xFF.toByte()
        private const val PN532_POSTAMBLE = BYTE_0
        private const val PN532_HOSTTOPN532 = 0xD4.toByte()
        private const val PN532_PN532TOHOST = 0xD5.toByte()
        private const val PN532_ISO14443A = BYTE_0

        private const val PN532_CMD_SAMCONFIGURATION: Byte = 0x14
        private const val PN532_CMD_INLISTPASSIVETARGET: Byte = 0x4A
        private const val PN532_CMD_INDATAEXCHANGE: Byte = 0x40

        private const val PN532_WAIT_TIME_MS = 10
        private const val PN532_SCANNER_WAIT_TIME_MS = 2000L
        private const val PN532_ACK_TIMEOUT_MS = 5000
        private const val PN532_READ_TIMEOUT_MS = 1000

        private const val PN532_PACKET_BUFFER_SIZE = 10 * APDU_MAX_RESPONSE_DATA_LEN

        private const val PN532_SAM_CONFIG_EXPECTED_LENGTH = 8

        private const val PN532_DETECT_TARGET_BUFFER_SIZE = 8
        private const val PN532_READ_PASSIVE_TARGET_EXPECTED_LENGTH = 64

        private const val PN532_SELECT_AID_EXPECTED_LENGTH = 16

        private const val PN532_GET_DATA_BUFFER_SIZE = PN532_PACKET_BUFFER_SIZE
        private const val PN532_GET_DATA_EXPECTED_LENGTH = PN532_PACKET_BUFFER_SIZE

        private const val PN532_SEND_DATA_EXPECTED_LENGTH = 16

        private val PN532_ACK = byteArrayOf(0, 0, 0xFF.toByte(), 0, 0xFF.toByte(), 0)
        private val AID = byteArrayOf(0xF0.toByte(), 0x01, 0x02, 0x03, 0x04, 0x05, 0x09)

        @Suppress("SameParameterValue")
        private fun buildSelectAidAPDU(aid: ByteArray) = ByteArray(aid.size + 6).apply {
            this[0] = BYTE_0 // CLA
            this[1] = 0xA4.toByte() // INS
            this[2] = 0x04 // P1
            this[3] = BYTE_0 // P2
            this[4] = aid.size.toByte() // LC
            for (index in aid.indices) { // AID
                this[5 + index] = aid[index]
            }
            this[6 + aid.size - 1] = BYTE_0 // LE
        }

        private fun buildGetDataAPDU(instruction: Byte) = ByteArray(6).apply {
            this[0] = BYTE_0 // CLA
            this[1] = 0xCA.toByte() // INS
            this[2] = BYTE_0 // P1
            this[3] = instruction // P2
            this[4] = BYTE_0 // Lc
            this[5] = APDU_MAX_RESPONSE_DATA_LEN.toByte() // Le
        }

        @Suppress("SameParameterValue")
        private fun buildSendDataAPDU(
            instruction: Byte,
            data: ByteArray
        ) = ByteArray(data.size + 5).apply {
            this[0] = BYTE_0 // CLA
            this[1] = 0xDA.toByte() // INS
            this[2] = BYTE_0 // P1
            this[3] = instruction // P2
            this[4] = data.size.toByte() // LC
            for (index in data.indices) { // DATA
                this[5 + index] = data[index]
            }
        }
    }
}