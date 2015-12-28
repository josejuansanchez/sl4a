# Author: @josejuansanchez
#
# Description:
# A simple http server that is able to read a POST request body, 
# parses it as JSON and reponses with the data.
#
# You can test the script with curl. Example:
# curl http://192.168.1.10:8000 -H "Content-Type: application/json" -X POST -d '{"r":128,"g":"36","b":37}' 
#
# References:
# - https://gist.github.com/trungly/5889154

from BaseHTTPServer import BaseHTTPRequestHandler
import SocketServer
import socket
import json
import os
import android

PORT = 8000

# Networking issues (http server)

def getIpAdress():
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    s.connect(("gmail.com",80))
    ip = s.getsockname()[0]
    s.close()
    return ip

class CustomHandler(BaseHTTPRequestHandler):

    def do_POST(self):
        content_len = int(self.headers.getheader('content-length'))
        post_body = self.rfile.read(content_len)
        self.send_response(200)
        self.end_headers()

        data = json.loads(post_body)
        print data

        changeBackgroundColor(data)

        self.wfile.write(data)
        return

# UI issues

def changeBackgroundColor(data):
    r = str(hex(int(data["r"])).replace('0x', ''))
    g = str(hex(int(data["g"])).replace('0x', ''))
    b = str(hex(int(data["b"])).replace('0x', ''))

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
layout = open(cwd + "/scripts/simple-projects/change_background_color/layout_server.xml", "r")
layout = layout.read()

# Load the layout
droid.fullShow(layout)

# Create and start the http server
httpd = SocketServer.TCPServer(("", PORT), CustomHandler)
initial_text = "Serving at: {0}:{1}".format(getIpAdress(), PORT) 
print(initial_text)
droid.fullSetProperty("textview", "text", initial_text)
httpd.serve_forever()