# Android-PN532-Kotlin
Sample project to setup connection between Android Things Raspberry Pi 3 with PN532 NFC chip and Android phone via NFC

This is not a library, but this code can be used to establish NFC connection between Raspberry Pi 3 on Android Things OS and Android phone.

I have tested it with Raspberry Pi 3 model B.

To make it works, you should implement HostApduService on your mobile side - HCEService.kt inside app-mobile module, and 
create Android Things some kind of user-space driver for PN532 NFC chip on IoT device size (Raspberry Pi 3 in our case) - PN532I2CNfcManager.kt inside app-iot module.

In my case, I bought the following PN532-based NFC chip
![alt text](https://github.com/skopintsevDima/Android-PN532-Kotlin/blob/master/pictures/PN532.jpeg)

This sample demonstrates sending big data pack from MOBILE to Raspberry.

# How to use
1) Connect your PN532 to Raspberry  
Connection scheme  
![alt text](https://github.com/skopintsevDima/Android-PN532-Kotlin/blob/master/pictures/Connection%20scheme.png)
2) Toggle PN532 into I2C mode  
How to toggle into I2C  
![alt text](https://github.com/skopintsevDima/Android-PN532-Kotlin/blob/master/pictures/Toggle%20I2C.png)
3) Setup mobile side:  
  a) Copy PN532HCEService into your project;  
  b) Define PN532HCEService in your AndroidManifest.xml;  
  c) Don't forget to mention required permission, uses-feature and HCE-related metadata.  
  You can read more about Host-Based card emulation on Android [here](https://developer.android.com/guide/topics/connectivity/nfc/hce).
4) Setup Rasppberry side:  
  a) Copy PN532I2CNfcManager into your IoT module;  
  b) Define required <uses-permission android:name="com.google.android.things.permission.USE_PERIPHERAL_IO"/>;  
  c) Launch NFC reader in your main activity onCreate();  
  d) Don't forget to stop and close NFC reader inside onDestroy().  
  You can read more about Android Things apps [here](https://developer.android.com/things/training/first-device).

# Useful links
1) I TOOK MOST PART OF THE CODE FROM THERE: https://github.com/hsilomedus/raspi-pn532
2) https://www.nxp.com/docs/en/user-guide/141520.pdf
3) https://www.smartjac.biz/index.php/support/main-menu?view=kb&kbartid=3
4) https://osoyoo.com/2017/07/20/pn532-nfc-rfid-module-for-raspberry-pi/
5) https://icedev.pl/blog/nfc-frontend-android-hce-apdu/
6) https://web.archive.org/web/20090630004017/http://cheef.ru/docs/HowTo/APDU.info
