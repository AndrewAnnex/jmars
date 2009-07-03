// Copyright 2008, Arizona Board of Regents
// on behalf of Arizona State University
// 
// Prepared by the Mars Space Flight Facility, Arizona State University,
// Tempe, AZ.
// 
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
// 
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
// 
// You should have received a copy of the GNU General Public License
// along with this program.  If not, see <http://www.gnu.org/licenses/>.


package edu.asu.jmars.util;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.UnknownHostException;

import javax.net.SocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;

import org.apache.commons.httpclient.ConnectTimeoutException;
import org.apache.commons.httpclient.HttpClientError;
import org.apache.commons.httpclient.params.HttpConnectionParams;
import org.apache.commons.httpclient.protocol.SecureProtocolSocketFactory;

/**
 * Provides an SSL socket factory for HttpClient that will use the
 * JmarsTrustManager to create SSL connections, allowing connection to https
 * services using self-signed certificates.
 */
public class HttpClientJmarsTrustManagerFactory implements SecureProtocolSocketFactory {
	private SSLContext sslcontext = null;
	private static SSLContext createContext() {
		try {
			SSLContext context = SSLContext.getInstance("SSL");
			context.init(null, new TrustManager[] {new JmarsTrustManager()}, null);
			return context;
		} catch (Exception e) {
			throw new HttpClientError(e.toString());
		}
	}
	private SSLContext getSSLContext() {
		if (this.sslcontext == null) {
			this.sslcontext = createContext();
		}
		return this.sslcontext;
	}
	public Socket createSocket(String host, int port, InetAddress clientHost, int clientPort)
		throws IOException, UnknownHostException
	{
		return getSSLContext().getSocketFactory().createSocket(host,port,clientHost,clientPort);
	}
	public Socket createSocket(String host, int port, InetAddress localAddress, int localPort, HttpConnectionParams params)
		throws IOException, UnknownHostException, ConnectTimeoutException 
	{
		if (params == null) {
			throw new IllegalArgumentException("Parameters may not be null");
		}
		int timeout = params.getConnectionTimeout();
		SocketFactory socketfactory = getSSLContext().getSocketFactory();
		if (timeout == 0) {
			return socketfactory.createSocket(host, port, localAddress, localPort);
		} else {
			Socket socket = socketfactory.createSocket();
			SocketAddress localaddr = new InetSocketAddress(localAddress, localPort);
			SocketAddress remoteaddr = new InetSocketAddress(host, port);
			socket.bind(localaddr);
			socket.connect(remoteaddr, timeout);
			return socket;
		}
	}
	public Socket createSocket(String host, int port)
		throws IOException, UnknownHostException
	{
		return getSSLContext().getSocketFactory().createSocket(host, port);
	}
	public Socket createSocket(Socket socket, String host, int port, boolean autoClose)
		throws IOException, UnknownHostException
	{
		return getSSLContext().getSocketFactory().createSocket(socket, host, port, autoClose);
	}
	public boolean equals(Object obj) {
		return obj != null && getClass().isInstance(obj);
	}
	public int hashCode() {
		return getClass().hashCode();
	}
}
