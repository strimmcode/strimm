
import socket
import sys

# Create a TCP/IP socket
sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)

# Bind the socket to the port
server_address = ('localhost', 5000)
sock.bind(server_address)

# Listen for incoming connections
sock.listen(5)
while True:
    connection, client_address = sock.accept()
    print("accepted connection")
    try:
        # Receive the data in small chunks and retransmit it
        while True:
            data = connection.recv(10000)
            print("got data")
            if data[0] == 1:
                print("close connection")
                break
    finally:
        # Clean up the connection
        print("closed connection")
        connection.close()	