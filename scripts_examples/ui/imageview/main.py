# Author: @josejuansanchez
#
# Description:
# This example shows how to load images into an imageview

def eventloop():
  while True:
    event=droid.eventWait().result
    print event
    #droid.fullSetProperty("textView1","text","event: " + str(event))

    # The script is stopped when the back key is pressed
    if event["name"] == "key":
        key = event["data"]["key"]
        if key == "4":
            return

layout="""<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/background"
    android:orientation="vertical" 
    android:layout_width="match_parent"
    android:layout_height="match_parent" 
    android:background="#ff000000">

    <ImageView 
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:id="@+id/imageViewUrl" />

    <ImageView 
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:id="@+id/imageViewFile" />

</LinearLayout>
"""

import android
droid=android.Android()
droid.fullShow(layout)
droid.fullSetProperty("imageViewUrl","src","http://hacklabalmeria.net/recursos/logo-225x225.png")
droid.fullSetProperty("imageViewFile","src","file:///sdcard/sl4a/scripts/ui/imageview/images/hacklabalmeria.png")
eventloop()