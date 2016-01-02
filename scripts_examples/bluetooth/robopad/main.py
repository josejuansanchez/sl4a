# Author: @josejuansanchez
#
# Description:
# Robot controller using the device's Bluetooth.
#
# The layout and the images have been taken from this source:
# https://github.com/bq/robopad

import os
import android

def eventloop():
  while True:
    event = droid.eventWait().result
    
    if event["name"] == "click":
      id = event["data"]["id"]
      if id == "up_button":
        droid.bluetoothWrite("U")
      elif id == "down_button":
        droid.bluetoothWrite("D")
      elif id == "left_button":
        droid.bluetoothWrite("L")
      elif id == "right_button":
        droid.bluetoothWrite("R")

    # The script is stopped when the back key is pressed
    if event["name"] == "key":
        key = event["data"]["key"]
        if key == "4":
            return

#---

droid = android.Android()

# Read the layout from an external file stored in the sd card
cwd = os.getcwd() 
layout = open(cwd + "/scripts/bluetooth/robopad/layout.xml", "r")
layout = layout.read()

# Load the layout and wait for the events
droid.fullShow(layout)

# Set the images
droid.fullSetProperty("robot_bg","src","file:///sdcard/sl4a/scripts/bluetooth/robopad/images/pollywog_bg_off.png")
droid.fullSetProperty("up_button","src","file:///sdcard/sl4a/scripts/bluetooth/robopad/images/ic_up_button.png")
droid.fullSetProperty("down_button","src","file:///sdcard/sl4a/scripts/bluetooth/robopad/images/ic_down_button.png")
droid.fullSetProperty("left_button","src","file:///sdcard/sl4a/scripts/bluetooth/robopad/images/ic_left_button.png")
droid.fullSetProperty("right_button","src","file:///sdcard/sl4a/scripts/bluetooth/robopad/images/ic_right_button.png")

# Set the bluetooth
droid.toggleBluetoothState(True)
droid.bluetoothConnect()

eventloop()
droid.bluetoothStop()