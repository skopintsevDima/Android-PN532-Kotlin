package com.pn532.android.core

fun bytesToHex(bytes: ByteArray): String {
    val stringBuffer = StringBuffer()
    bytes.forEach { b -> stringBuffer.append(String.format("%02X", b)).append(" ") }
    return stringBuffer.toString().trim()
}