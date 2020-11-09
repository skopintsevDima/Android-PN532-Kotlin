package com.pn532.android.core

const val LOREM_IPSUM = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum."
const val MSG_ANSWER_CORRECT = "Data is correct"

const val APDU_MAX_RESPONSE_DATA_LEN = 100

const val BYTE_0 = 0x00.toByte()
const val BYTE_1 = 1.toByte()
const val BYTE_2 = 2.toByte()

const val APDU_GETDATAINS_GETDATA = BYTE_1
const val APDU_GETDATAINS_GETMOREDATA = BYTE_2
const val APDU_SENDDATAINS_SENDMSG = BYTE_1

val APDU_SUCCESS = byteArrayOf(0x90.toByte(), BYTE_0)
val APDU_SUCCESS_MORE_DATA = byteArrayOf(0x91.toByte(), BYTE_0)
val APDU_FAILURE = byteArrayOf(0x6F.toByte())