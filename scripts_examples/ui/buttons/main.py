# Author: @josejuansanchez
#
# Description:
# This example loads a layout from an external file
# and detects when the buttons are clicked.

import os
import android

def eventloop():
  while True:
    event = droid.eventWait().result
    
    print event
    droid.fullSetProperty("textview1", "text", "event: " + str(event))

    if event["name"] == "click":
      id = event["data"]["id"]
      if id == "button1":
        droid.makeToast("Button1 clicked!")
      elif id == "button2":
        droid.makeToast("Button2 clicked!")

    # The script is stopped when the back key is pressed
    if event["name"] == "key":
        key = event["data"]["key"]
        if key == "4":
            return

droid = android.Android()

# Read the layout from an external file stored in the sd card
cwd = os.getcwd() 
layout = open(cwd + "/scripts/ui/buttons/layout.xml", "r")
layout = layout.read()

# Load the layout and wait for the events
print droid.fullShow(layout)
eventloop()
