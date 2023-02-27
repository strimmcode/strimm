

import socket
import time
import os

print("starting")

target_host = "127.0.0.1"
target_post = 6000
#time.sleep(5.0);

#for f in range(1000):


#szSTRIMMFolder = "C:\\Users\\twrig\\Desktop\\Code\\FullGraph17\\STRIMM.app"
#os.chdir(szSTRIMMFolder)
#szSTRIMMName = "Launch_STRIMM.exe"  #TODO TW change the server name to something more understandable
#start the server
#os.startfile(szSTRIMMFolder + "/" + szSTRIMMName)

#time.sleep(15.0)

print("load")
client = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
client.connect((target_host,target_post))
client.send("1,0003_MMCamera_OpenCV_4_New\n".encode())
received = client.recv(1024)
rcc = received.decode()
print(rcc)
client.close()

time.sleep(5.0)
print("run")
client = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
client.connect((target_host,target_post))
client.send("2\n".encode())
received = client.recv(1024)
rcc = received.decode()
print(rcc)
client.close()

time.sleep(5.0)
print("stop")
client = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
client.connect((target_host,target_post))
client.send("5\n".encode())
received = client.recv(1024)
rcc = received.decode()
print(rcc)
client.close()

time.sleep(5.0)
print("close")
client = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
client.connect((target_host,target_post))
client.send("6\n".encode())
received = client.recv(1024)
rcc = received.decode()
print(rcc)
client.close()




