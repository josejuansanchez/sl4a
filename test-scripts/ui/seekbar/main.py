# Author: @josejuansanchez
#
# Description:
# This example loads a layout from an external file
# and uses the seekbar values to change the background color.

import os
import android

global r
global g
global b

r = "0"
g = "0"
b = "0"

def eventloop():
  global r
  global g
  global b

  while True:
    event = droid.eventWait().result
    
    #droid.fullSetProperty("textview", "text", "event: " + str(event))

    if event["name"] == "itemclick":
      id = event["data"]["id"]
      if id == "seekbar_r":
        r = str(hex(int(event["data"]["progress"])).replace('0x', ''))
      elif id == "seekbar_g":
        g = str(hex(int(event["data"]["progress"])).replace('0x', ''))
      elif id == "seekbar_b":
        b = str(hex(int(event["data"]["progress"])).replace('0x', ''))

    changeBackgroundColor(r, g, b)

    # The script is stopped when the back key is pressed
    if event["name"] == "key":
        key = event["data"]["key"]
        if key == "4":
            return

def changeBackgroundColor(r, g, b):
    r = "0" + r if len(r) == 1 else r
    g = "0" + g if len(g) == 1 else g
    b = "0" + b if len(b) == 1 else b

    backgroundColor = "0xff" + r + g + b
    droid.fullSetProperty("textview", "text", "Color: " + backgroundColor)
    droid.fullSetProperty("background", "backgroundColor", backgroundColor)

#---

droid = android.Android()

# Read the layout from an external file stored in the sd card
cwd = os.getcwd() 
layout = open(cwd + "/scripts/ui/seekbar/layout.xml", "r")
layout = layout.read()

# Load the layout and wait for the events
droid.fullShow(layout)
eventloop()
