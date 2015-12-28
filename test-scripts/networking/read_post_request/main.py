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

PORT = 8000

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

        self.wfile.write(data)
        return

httpd = SocketServer.TCPServer(("", PORT), CustomHandler)
print("Serving at: {0}:{1}".format(getIpAdress(), PORT))
httpd.serve_forever()