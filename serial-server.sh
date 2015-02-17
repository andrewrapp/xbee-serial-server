#!/bin/bash

/opt/java/ejre1.7.0_10/bin/java -Xms16m -Xmx64m -Djava.library.path=/usr/lib/jni/ -classpath "*:/usr/share/java/RXTXcomm.jar" com.rapplogic.xbee.serialserver.server.XBeeSerialServer 2>> serialserver.err 1> /dev/null