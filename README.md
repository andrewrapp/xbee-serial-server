# xbee-serial-server

Connects to an xbee radio on port specified by arg[0] at 9600 baud and listens on port 9000 for connections. This simply relays all TX packets to the radio and forwards RX packets to socket clients. Use with https://github.com/andrewrapp/xbee-socket
