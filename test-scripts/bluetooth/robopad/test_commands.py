import android

droid = android.Android()
droid.toggleBluetoothState(True)
droid.bluetoothConnect()
#droid.bluetoothConnect("457807c0-4897-11df-9879-0800200c9a66", "20:14:12:22:05:85")
droid.bluetoothWrite("U")
droid.bluetoothWrite("D")
droid.bluetoothWrite("L")
droid.bluetoothWrite("R")
droid.bluetoothWrite("S")
droid.checkBluetoothState()
droid.bluetoothStop()