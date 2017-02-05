# xbee-serial-server

Release [0.9.1](https://search.maven.org/#search%7Cga%7C1%7Ca%3A%22xbee-serial-server%22) is in Maven Central

Connects to an xbee radio on port specified by arg[0] at 9600 baud and listens on port 10010 for connections. This simply relays all TX packets to the radio and forwards RX packets to socket clients. Use with https://github.com/andrewrapp/xbee-api

Example:

Start the server on machine that has attached XBee radio. In this case the XBee is exposed on serial port /dev/ttyUSB0. Note: RXTX must be added to classpath and java.library.path.

```
java -Xms16m -Xmx64m -Djava.library.path=/usr/lib/jni/ -classpath "*:/usr/share/java/RXTXcomm.jar" com.rapplogic.xbee.serialserver.server.XBeeSerialServer /dev/ttyUSB0 
```

Then, somewhere else on the network, start the client and specify the host and port of the server. All XBee commands will now be sent over the wire.

```java  
XBee xbee = new XBee(new XBeeConfiguration().withStartupChecks(false));
// connect to xbee-serial-server on host "pi" with port 10010
xbee.initProviderConnection((XBeeConnection)new SocketXBeeConnection("pi", 10010));
AtCommandResponse response = (AtCommandResponse) xbee.sendSynchronous(new AtCommand("AI"));
System.out.println("Received AI response " + response);
xbee.close();
```
