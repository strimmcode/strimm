


import socket
import time
import os

print("starting")

target_host = "127.0.0.1"
target_post = 5001
#time.sleep(5.0);

for f in range(1000):

    client = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    client.connect((target_host,target_post))
    client.send("data\n".encode())
    received = client.recv(1024)
    rcc = received.decode()
    print(rcc)
    client.close()
    time.sleep(0.5)





