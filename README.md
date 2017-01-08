# xbee-serial-server

Release [0.9.1](https://search.maven.org/#search%7Cga%7C1%7Ca%3A%22xbee-serial-server%22) is in Maven Central

Connects to an xbee radio on port specified by arg[0] at 9600 baud and listens on port 10010 for connections. This simply relays all TX packets to the radio and forwards RX packets to socket clients. Use with https://github.com/andrewrapp/xbee-api

```java  
XBee xbee = new XBee(new XBeeConfiguration().withStartupChecks(false));
// connect to xbee-serial-server on host "pi" with port 9000
xbee.initProviderConnection((XBeeConnection)new SocketXBeeConnection("pi", 9000));
AtCommandResponse response = (AtCommandResponse) xbee.sendSynchronous(new AtCommand("AI"));
System.out.println("Received AI response " + response);
xbee.close();
```
