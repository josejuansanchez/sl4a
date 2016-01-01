# Author: @josejuansanchez
#
# Description:
# This script allows to communicate an Android device with an Arduino 
# via usb serial connection and sends the commands 'on'/'off' in order 
# to turn on/off a LED attached to the Arduino board.
#
# Reference:
# https://code.google.com/p/android-jp-kobe/source/browse/trunk/pyAndyUI/andy6th.py
#
# Note:
# Arduino Uno R3 has the next VID and PID: 
# Vendor ID: 0x2341 / Product ID: 0x0043

import android
import time
import json

def connect():
    devices = []
    for key, value in droid.usbserialGetDeviceList().result.items():
        # value: is '["device-name","VID","PID","hashCode"]'
        print(key, value)
        devices.append(value)

    response = showDialogDeviceList(devices)
    selectedDevice = devices[response["item"]]
    hashCode = json.loads(selectedDevice)[-1]

    ret = droid.usbserialConnect(hashCode)
    if not "OK" in ret.result:
        print("Can't connect to device: ", ret.result)
        return False

    uuid = json.loads(ret.result)[-1]
    print("Connected with ", hashCode, uuid)

    # It is necessary to wait a few seconds before
    # to send anything via usb serial port
    n = 2
    while n > 0 and not droid.usbserialReadReady(uuid).result:
        n -= 1
        print("Waiting for connect...", n)
        time.sleep(1)

    return uuid

def showDialogDeviceList(items):
    droid.dialogCreateAlert("Connected USB devices")
    droid.dialogSetItems(items)
    droid.dialogShow()
    response = droid.dialogGetResponse().result
    return response

#---

droid = android.Android()
connect()
droid.usbserialWrite("on")
time.sleep(2)
droid.usbserialWrite("off")
