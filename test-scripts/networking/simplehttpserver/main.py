# Author: @josejuansanchez
#
# Description:
# Very basic web server serving files relative to the current directory.
#
# References:
# - https://docs.python.org/2/library/simplehttpserver.html
# - https://stackoverflow.com/questions/166506/finding-local-ip-addresses-using-pythons-stdlib/166589#166589

import SimpleHTTPServer
import SocketServer
import socket

PORT = 8000

def getIpAdress():
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    s.connect(("gmail.com",80))
    ip = s.getsockname()[0]
    s.close()
    return ip

Handler = SimpleHTTPServer.SimpleHTTPRequestHandler
httpd = SocketServer.TCPServer(("", PORT), Handler)
print("Serving at: {0}:{1}".format(getIpAdress(), PORT))
httpd.serve_forever()