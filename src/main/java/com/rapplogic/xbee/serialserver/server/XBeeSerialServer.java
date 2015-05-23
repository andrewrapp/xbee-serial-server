/**
 * Copyright (c) 2009 Andrew Rapp. All rights reserved.
 *  
 * This file is part of XBee-XMPP
 *  
 * XBee-XMPP is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *  
 * XBee-XMPP is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *  
 * You should have received a copy of the GNU General Public License
 * along with XBee-XMPP.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.rapplogic.xbee.serialserver.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

import com.rapplogic.xbee.api.XBee;
import com.rapplogic.xbee.api.XBeeConfiguration;
import com.rapplogic.xbee.api.XBeePacket;
import com.rapplogic.xbee.api.XBeeResponse;
import com.rapplogic.xbee.util.ByteUtils;

// TODO rewrite in Python, RXTX is a pain

public class XBeeSerialServer {
	 
	private final static Logger log = Logger.getLogger(XBeeSerialServer.class);
	
	private XBee xbee;
	private List<Integer> packet;
	private ServerSocket socketServer;
	private BlockingQueue<int[]> packetQueue = new LinkedBlockingDeque<int[]>();
	private List<Socket> clients = new CopyOnWriteArrayList<Socket>();
	
	private final int MAX_PACKET_SIZE = 120;
	
	public static void main(String[] args) throws Exception {
		// init log4j
		PropertyConfigurator.configure("log4j.properties");
		
		if (args.length < 1) {
			System.err.println("Serial port or xbee device must be specified as the argument, e.g. /dev/tty.usbserial-A6005uRz");
			System.exit(1);
		}
		
		new XBeeSerialServer(args[0]);
	}
	
	public void shutdown() throws IOException {
		socketServer.close();
		
		if (xbee != null && xbee.isConnected()) {
			xbee.close();		
		}
	}
	
	public XBeeSerialServer(String port) throws Exception {
		xbee = new XBee(new XBeeConfiguration().withStartupChecks(false));
		
		log.info("Connecting to serial port " + port);
		// replace with the com port of your XBee coordinator
		xbee.open(port, 9600);
		
		socketServer = new ServerSocket(10010);
		log.debug("Socket server listening on port 10010");
		
		final ExecutorService socketReaderExecutor = Executors.newCachedThreadPool();
		
		ExecutorService xbeeExecutor = Executors.newFixedThreadPool(1);
		
		xbeeExecutor.submit(new Runnable() {
			public void run() {
				while (true) {
					try {
						log.debug("Waiting for RX packet from serial");
						XBeeResponse response = xbee.getResponse();
						log.debug("Received RX packet from serial " + response);
						
						if (response.isError()) {
							log.error("Response is error: " + response.toString());
							if (response.getRawPacketBytes() == null) {
								// TODO proper error handling
								// set bogus packet with zero length and zero api id. this will error out on client
								response.setRawPacketBytes(new int[] {0, 0, 0});
							}
						}
						
						packetQueue.add(response.getRawPacketBytes());						
					} catch (Throwable t) {
						log.error("Error processing RX packet from radio", t);
					}
				}
			}
		});
		
		ExecutorService socketServerExecutor = Executors.newFixedThreadPool(1);
		
		socketServerExecutor.submit(new Callable<Void>() {

			public Void call() throws Exception {
	            while (true) {
	            	try {
	            		log.debug("Waiting for socket connecton from client");
		                final Socket socket = socketServer.accept();
		                log.info("Client connected from " + socket.getRemoteSocketAddress());
		                
		                clients.add(socket);	
		                
		                log.debug("There are " + clients.size() + " active clients");
		                
		                //nio would be better here
		                socketReaderExecutor.submit(new Callable<Void>() {
							public Void call() throws Exception {
								try {
									// blocks until socket disconnect
									readSocket(socket.getInputStream());									
								} catch (Throwable t) {
									// TODO do we need to close?
									log.warn("Socket read error", t);
								}
								
								log.info("Socket closed " + socket.getReuseAddress() + ". removing from clients");
								
								// socket disconnect
								clients.remove(socket);
								
								log.debug("There are " + clients.size() + " active clients");
								
								return null;
							}
						});
			        } catch (Throwable t) {
			        	log.error("Error in socket server", t);
			        }
	            }
			}
		});
		
		
		ExecutorService packetRelayExecutor = Executors.newFixedThreadPool(1);
		
		packetRelayExecutor.submit(new Callable<Void>() {
			public Void call() throws Exception {
				try {
					while (true) {
						log.debug("Waiting for packet from queue to broadcast to clients");
					
						int[] packet = packetQueue.take();
						
						log.debug("Found packet on queue, size " + packet.length);
						
						for (Socket socket : clients) {
							if (socket.isConnected() && !socket.isClosed() && !socket.isInputShutdown() && !socket.isOutputShutdown()) {
								try {
									log.debug("Writing packet to socket " + socket.getRemoteSocketAddress());
									// add start byte
									socket.getOutputStream().write(XBeePacket.SpecialByte.START_BYTE.getValue());	
									
									for (int b : packet) {
										socket.getOutputStream().write(b);										
									}
								} catch (Exception e) {
									log.error("Unable to write packet to socket " + socket.getRemoteSocketAddress(), e);
								}
							} else {
								log.debug("Socket " + socket.getRemoteSocketAddress() + " is not longer connected. closing");
								socket.close();
								// read block with throw exception and remove from list
							}
						}
						
						if (Thread.currentThread().isInterrupted()) {
							// really closing the socket will suffice to exit but for good measure
							break;
						}
					}					
				} catch (Throwable t) {
					log.error("Error reading from queue", t);
				}
				
				return null;
			}
		});
		
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				try {
					shutdown();
				} catch (Throwable t) {
					log.error("Shutdown failed", t);
				}
			}
		});
	}
	
	private int[] toIntArray(Integer[] arr) {
		int[] intArr = new int[arr.length];
		for (int i = 0; i < arr.length; i++) {
			intArr[i] = arr[i];
		}
		
		return intArr;
	}
	
	protected void writePacketToSocket(int[] packet, OutputStream out) throws IOException {		
		for (int i : packet) {
			out.write(i);	
		}
	}
	
	// read from socket, send bytes to xbee via serial
	public void readSocket(InputStream inputStream) throws IOException {
				
		log.debug("Reading from socket");
		
		int b;
		
		while ((b = inputStream.read()) != -1) {
//			log.debug("Read byte from socket " + ByteUtils.toBase16(b));
			
			try {
				if (b == XBeePacket.SpecialByte.START_BYTE.getValue()) {
					if (packet != null) {
						log.error("Received packet start while already parsing a packet.. discarding " + packet);
					}
					
					log.debug("Received start byte");
					
					// start accumulating
					packet = new ArrayList<Integer>();
				}
				
				if (packet != null) {
					packet.add(b);
					
					// ugly convert
					int[] packetInt = toIntArray((Integer[])packet.toArray(new Integer[packet.size()]));
									
					if (XBeePacket.verify(packetInt)) {
						// complete
						log.debug("Received valid TX packet from socket, size " + packetInt.length + ".. sending to radio " + ByteUtils.toBase16(packetInt));
						
						// gotta sync to prevent interleaved packets going out radio which would fail miserably
						// could add to queue and avoid synchronizing
						synchronized (this) {
							xbee.sendPacket(packetInt);						
						}

						packet = null;
					} else {
						if (packet.size() > MAX_PACKET_SIZE) {
							// error did not verify
							log.error("Read too many bytes without successful packet validation.. discarding packet of length " + packet.size());
							packet = null;
						}
					}				
				}				
			} catch (RuntimeException e) {
				// don't throw ex which would close socket
				log.error("Unexpected error parsing packet " + packet, e);
			}
		}
	}
}