/*
 * Tigase XMPP Client Library
 * Copyright (C) 2006-2012 "Bartosz Małkowski" <bartosz.malkowski@tigase.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. Look for COPYING file in the top folder.
 * If not, see http://www.gnu.org/licenses/.
 */
package tigase.jaxmpp.j2se.connectors.socket;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.Socket;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

import javax.net.ssl.HandshakeCompletedEvent;
import javax.net.ssl.HandshakeCompletedListener;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import com.spbsu.commons.io.StreamTools;
import tigase.jaxmpp.core.client.BareJID;
import tigase.jaxmpp.core.client.Connector;
import tigase.jaxmpp.core.client.Connector.ConnectedHandler.ConnectedEvent;
import tigase.jaxmpp.core.client.Connector.EncryptionEstablishedHandler.EncryptionEstablishedEvent;
import tigase.jaxmpp.core.client.Connector.ErrorHandler.ErrorEvent;
import tigase.jaxmpp.core.client.Connector.StanzaReceivedHandler.StanzaReceivedEvent;
import tigase.jaxmpp.core.client.Connector.StanzaSendingHandler.StanzaSendingEvent;
import tigase.jaxmpp.core.client.Connector.StateChangedHandler.StateChangedEvent;
import tigase.jaxmpp.core.client.Context;
import tigase.jaxmpp.core.client.PacketWriter;
import tigase.jaxmpp.core.client.SessionObject;
import tigase.jaxmpp.core.client.SessionObject.Scope;
import tigase.jaxmpp.core.client.XmppModulesManager;
import tigase.jaxmpp.core.client.XmppSessionLogic;
import tigase.jaxmpp.core.client.connector.StreamError;
import tigase.jaxmpp.core.client.eventbus.EventHandler;
import tigase.jaxmpp.core.client.eventbus.JaxmppEvent;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.factory.UniversalFactory;
import tigase.jaxmpp.core.client.xml.Element;
import tigase.jaxmpp.core.client.xml.ElementFactory;
import tigase.jaxmpp.core.client.xml.XMLException;
import tigase.jaxmpp.core.client.xmpp.modules.StreamFeaturesModule;
import tigase.jaxmpp.core.client.xmpp.modules.registration.InBandRegistrationModule;
import tigase.jaxmpp.core.client.xmpp.stanzas.Stanza;
import tigase.jaxmpp.core.client.xmpp.stanzas.StreamPacket;
import tigase.jaxmpp.j2se.DNSResolver;
import tigase.jaxmpp.j2se.Jaxmpp;
import tigase.jaxmpp.j2se.connectors.socket.SocketConnector.HostChangedHandler.HostChangedEvent;

/**
 *
 */
public class SocketConnector implements Connector {

	public static interface DnsResolver {

		List<Entry> resolve(String hostname);
	}

	public final static class Entry {

		private final String hostname;

		private final Integer port;

		public Entry(String host, Integer port) {
			this.hostname = host;
			this.port = port;
		}

		public String getHostname() {
			return hostname;
		}

		public Integer getPort() {
			return port;
		}

		@Override
		public String toString() {
			return hostname + ":" + port;
		}

	}

	/**
	 * see-other-host
	 */
	public interface HostChangedHandler extends EventHandler {

		public static class HostChangedEvent extends JaxmppEvent<HostChangedHandler> {

			public HostChangedEvent(SessionObject sessionObject) {
				super(sessionObject);
			}

			@Override
			protected void dispatch(HostChangedHandler handler) {
				handler.onHostChanged(sessionObject);
			}

		}

		void onHostChanged(SessionObject sessionObject);
	}

	public final static String COMPRESSION_DISABLED_KEY = "COMPRESSION_DISABLED";

	public final static HostnameVerifier DEFAULT_HOSTNAME_VERIFIER = new DefaultHostnameVerifier();

	/**
	 * Default size of buffer used to decode data before parsing
	 */
	public final static int DEFAULT_SOCKET_BUFFER_SIZE = 2048;

	/**
	 * Instance of empty byte array used to force flush of compressed stream
	 */
	private final static byte[] EMPTY_BYTEARRAY = new byte[0];

	public static final String HOSTNAME_VERIFIER_DISABLED_KEY = "HOSTNAME_VERIFIER_DISABLED_KEY";

	public static final String HOSTNAME_VERIFIER_KEY = "HOSTNAME_VERIFIER_KEY";

	public static final String KEY_MANAGERS_KEY = "KEY_MANAGERS_KEY";

	public final static String RECONNECTING_KEY = "s:reconnecting";

	public static final String SASL_EXTERNAL_ENABLED_KEY = "SASL_EXTERNAL_ENABLED_KEY";

	public static final String SERVER_HOST = "socket#ServerHost";

	public static final String SERVER_PORT = "socket#ServerPort";

	/**
	 * Socket timeout.
	 */
	public static final int SOCKET_TIMEOUT = 1000 * 60 * 3;

	public static final String SSL_SOCKET_FACTORY_KEY = "socket#SSLSocketFactory";

	public static final String TLS_DISABLED_KEY = "TLS_DISABLED";

	public static boolean isTLSAvailable(SessionObject sessionObject) throws XMLException {
		final Element sf = StreamFeaturesModule.getStreamFeatures(sessionObject);
		if (sf == null)
			return false;
		Element m = sf.getChildrenNS("starttls", "urn:ietf:params:xml:ns:xmpp-tls");
		return m != null;
	}

	/**
	 * Returns true if server send stream features in which it advertises
	 * support for stream compression using ZLIB
	 *
	 * @param sessionObject
	 * @return
	 * @throws XMLException
	 */
	public static boolean isZLibAvailable(SessionObject sessionObject) throws XMLException {
		final Element sf = StreamFeaturesModule.getStreamFeatures(sessionObject);
		if (sf == null)
			return false;
		Element m = sf.getChildrenNS("compression", "http://jabber.org/features/compress");
		if (m == null)
			return false;

		for (Element method : m.getChildren("method")) {
			if ("zlib".equals(method.getValue()))
				return true;
		}

		return false;
	}

	private Context context;

	private final TrustManager dummyTrustManager = new X509TrustManager() {

		@Override
		public void checkClientTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {
		}

		@Override
		public void checkServerTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {
		}

		@Override
		public X509Certificate[] getAcceptedIssuers() {
			return null;
		}
	};

	private final Object ioMutex = new Object();

	private final Logger log;

	private TimerTask pingTask;

	private volatile Reader reader;

	private Socket socket;

	private Timer timer;

	private Worker worker;

	private OutputStream writer;

	public SocketConnector(Context context) {
		this.log = Logger.getLogger(this.getClass().getName());
		this.context = context;
	}

	@Override
	public XmppSessionLogic createSessionLogic(XmppModulesManager modulesManager, PacketWriter writer) {
		if (context.getSessionObject().getProperty(InBandRegistrationModule.IN_BAND_REGISTRATION_MODE_KEY) == Boolean.TRUE) {
			log.info("Using XEP-0077 mode!!!!");
			return new SocketInBandRegistrationXmppSessionLogic(this, modulesManager, context);
		} else
			return new SocketXmppSessionLogic(this, modulesManager, context);
	}

	protected void fireOnConnected(SessionObject sessionObject) throws JaxmppException {
		if (getState() == State.disconnected)
			return;

		context.getEventBus().fire(new ConnectedEvent(sessionObject));
	}

	protected void fireOnError(Element response, Throwable caught, SessionObject sessionObject) throws JaxmppException {
		StreamError streamError = null;
		if (response != null) {
			List<Element> es = response.getChildrenNS("urn:ietf:params:xml:ns:xmpp-streams");
			if (es != null)
				for (Element element : es) {
					String n = element.getName();
					streamError = StreamError.getByElementName(n);
					break;
				}
		}

		context.getEventBus().fire(new ErrorEvent(sessionObject, streamError, caught));
	}

	protected void fireOnStanzaReceived(StreamPacket response, SessionObject sessionObject) throws JaxmppException {
		context.getEventBus().fire(new StanzaReceivedEvent(sessionObject, response));
	}

	protected void fireOnTerminate(SessionObject sessionObject) throws JaxmppException {
		context.getEventBus().fire(new StreamTerminatedHandler.StreamTerminatedEvent(sessionObject));
	}

	private Entry getHostFromSessionObject() {
		String serverHost = (String) context.getSessionObject().getProperty(SERVER_HOST);
		Integer port = (Integer) context.getSessionObject().getProperty(SERVER_PORT);
		if (serverHost == null)
			return null;
		return new Entry(serverHost, port == null ? 5222 : port);

	}

	protected KeyManager[] getKeyManagers() throws NoSuchAlgorithmException {
		KeyManager[] result = context.getSessionObject().getProperty(KEY_MANAGERS_KEY);
		return result == null ? new KeyManager[0] : result;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public State getState() {
		State st = this.context.getSessionObject().getProperty(CONNECTOR_STAGE_KEY);
		return st == null ? State.disconnected : st;
	}

	/**
	 * Returns true when stream is compressed
	 *
	 * @return
	 */
	@Override
	public boolean isCompressed() {
		return ((Boolean) context.getSessionObject().getProperty(COMPRESSED_KEY)) == Boolean.TRUE;
	}

	@Override
	public boolean isSecure() {
		return ((Boolean) context.getSessionObject().getProperty(ENCRYPTED_KEY)) == Boolean.TRUE;
	}

	@Override
	public void keepalive() throws JaxmppException {
		if (context.getSessionObject().getProperty(DISABLE_KEEPALIVE_KEY) == Boolean.TRUE)
			return;
		if (getState() == State.connected)
			send(new byte[] { 32 });
	}

	protected void onError(Element response, Throwable caught) throws JaxmppException {
		if (response != null) {
			Element seeOtherHost = response.getChildrenNS("see-other-host", "urn:ietf:params:xml:ns:xmpp-streams");
			if (seeOtherHost != null) {
				if (log.isLoggable(Level.FINE))
					log.fine("Received see-other-host=" + seeOtherHost.getValue());
				reconnect(seeOtherHost.getValue());
				return;
			}
		}
		terminateAllWorkers();
		fireOnError(response, caught, context.getSessionObject());
	}

	protected void onErrorInThread(Exception e) throws JaxmppException {
		if (getState() == State.disconnected)
			return;
		terminateAllWorkers();
		fireOnError(null, e, context.getSessionObject());
	}

	protected void onResponse(final Element response) throws JaxmppException {
		synchronized (ioMutex) {
			if ("error".equals(response.getName()) && response.getXMLNS() != null
					&& response.getXMLNS().equals("http://etherx.jabber.org/streams")) {
				onError(response, null);
			} else {
				StreamPacket p;
				if (Stanza.canBeConverted(response)) {
					p = Stanza.create(response);
				} else {
					p = new StreamPacket(response) {
					};
				}
				p.setXmppStream(context.getStreamsManager().getDefaultStream());
				fireOnStanzaReceived(p, context.getSessionObject());
			}
		}
	}

	protected void onStreamStart(Map<String, String> attribs) {
		// TODO Auto-generated method stub
	}

	protected void onStreamTerminate() throws JaxmppException {
		if (getState() == State.disconnected)
			return;
		setStage(State.disconnected);

		if (log.isLoggable(Level.FINE))
			log.fine("Stream terminated");

		terminateAllWorkers();
		fireOnTerminate(context.getSessionObject());

	}

	public void onTLSStanza(Element elem) throws JaxmppException {
		if (elem.getName().equals("proceed")) {
			proceedTLS();
		} else if (elem.getName().equals("failure")) {
			log.info("TLS Failure");
		}
	}

	/**
	 * Handles result of requesting stream compression
	 *
	 * @param elem
	 * @throws JaxmppException
	 */
	public void onZLibStanza(Element elem) throws JaxmppException {
		if (elem.getName().equals("compressed") && "http://jabber.org/protocol/compress".equals(elem.getXMLNS())) {
			proceedZLib();
		} else if (elem.getName().equals("failure")) {
			log.info("ZLIB Failure");
		}
	}

	protected void proceedTLS() throws JaxmppException {
		log.fine("Proceeding TLS");
		try {
			context.getSessionObject().setProperty(Scope.stream, DISABLE_KEEPALIVE_KEY, Boolean.TRUE);
			TrustManager[] trustManagers = context.getSessionObject().getProperty(TRUST_MANAGERS_KEY);
			final SSLSocketFactory factory;
			if (trustManagers == null) {
				if (context.getSessionObject().getProperty(SSL_SOCKET_FACTORY_KEY) != null) {
					factory = context.getSessionObject().getProperty(SSL_SOCKET_FACTORY_KEY);
				} else {
					factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
				}
			} else {
				SSLContext ctx = SSLContext.getInstance("TLS");
				ctx.init(getKeyManagers(), trustManagers, new SecureRandom());
				factory = ctx.getSocketFactory();
			}

			SSLSocket s1 = (SSLSocket) factory.createSocket(socket, socket.getInetAddress().getHostAddress(), socket.getPort(),
					true);

			// if
			// (context.getSessionObject().getProperty(DISABLE_SOCKET_TIMEOUT_KEY)
			// == null
			// || !((Boolean)
			// context.getSessionObject().getProperty(DISABLE_SOCKET_TIMEOUT_KEY)).booleanValue())
			// {
			// s1.setSoTimeout(SOCKET_TIMEOUT);
			// }
			s1.setSoTimeout(0);
			s1.setKeepAlive(false);
			s1.setTcpNoDelay(true);
			s1.setUseClientMode(true);
			s1.addHandshakeCompletedListener(new HandshakeCompletedListener() {

				@Override
				public void handshakeCompleted(HandshakeCompletedEvent arg0) {
					log.info("TLS completed " + arg0);
					context.getSessionObject().setProperty(Scope.stream, ENCRYPTED_KEY, Boolean.TRUE);
					context.getEventBus().fire(new EncryptionEstablishedEvent(context.getSessionObject()));
				}
			});
			writer = null;
			reader = null;
			log.fine("Start handshake");

			final String hostname;
			if (context.getSessionObject().getProperty(SessionObject.USER_BARE_JID) != null) {
				hostname = ((BareJID) context.getSessionObject().getProperty(SessionObject.USER_BARE_JID)).getDomain();
			} else if (context.getSessionObject().getProperty(SessionObject.DOMAIN_NAME) != null) {
				hostname = context.getSessionObject().getProperty(SessionObject.DOMAIN_NAME);
			} else {
				hostname = null;
			}

			s1.startHandshake();

			final HostnameVerifier hnv = context.getSessionObject().getProperty(HOSTNAME_VERIFIER_KEY);
			if (hnv != null && !hnv.verify(hostname, s1.getSession())) {
				throw new javax.net.ssl.SSLHandshakeException(
						"Cerificate hostname doesn't match domain name you want to connect.");
			}

			socket = s1;
			writer = socket.getOutputStream();
			reader = new TextStreamReader(socket.getInputStream());
			restartStream();
		} catch (javax.net.ssl.SSLHandshakeException e) {
			log.log(Level.SEVERE, "Can't establish encrypted connection", e);
			onError(null, e);
		} catch (Exception e) {
			log.log(Level.SEVERE, "Can't establish encrypted connection", e);
			onError(null, e);
		} finally {
			context.getSessionObject().setProperty(Scope.stream, DISABLE_KEEPALIVE_KEY, Boolean.FALSE);
		}
	}

	/**
	 * Method activates stream compression by replacing reader and writer fields
	 * values and restarting XMPP stream
	 *
	 * @throws JaxmppException
	 */
	protected void proceedZLib() throws JaxmppException {
		log.fine("Proceeding ZLIB");
		try {
			context.getSessionObject().setProperty(Scope.stream, DISABLE_KEEPALIVE_KEY, Boolean.TRUE);

			writer = null;
			reader = null;
			log.fine("Start ZLIB compression");

			Deflater compressor = new Deflater(Deflater.BEST_COMPRESSION, false);
			try {
				// on Android platform Deflater has field named flushParm which
				// can force flushing data to socket for us
				Field f = compressor.getClass().getDeclaredField("flushParm");
				if (f != null) {
					f.setAccessible(true);
					f.setInt(compressor, 2); // Z_SYNC_FLUSH
					writer = new DeflaterOutputStream(socket.getOutputStream(), compressor);
				}
			} catch (NoSuchFieldException ex) {

				// if we do not have field we are on standard Java VM
				try {
					// try to create flushable DeflaterOutputStream but it
					// exists
					// only on Java 7 so we access it using reflection for
					// compatibility
					Constructor<DeflaterOutputStream> flushable = DeflaterOutputStream.class.getConstructor(OutputStream.class,
							Deflater.class, boolean.class);

					// we need wrap DeflaterOutputStream to flush it every time
					// we are
					// writing to it
					writer = new OutputStreamFlushWrap(flushable.newInstance(socket.getOutputStream(), compressor, true));

				} catch (NoSuchMethodException ex1) {
					// if we do not find constructor from Java 7 we use flushing
					// algorithm which was working fine on Java 6
					writer = new DeflaterOutputStream(socket.getOutputStream(), compressor) {
						@Override
						public void write(byte[] data) throws IOException {
							super.write(data);
							super.write(EMPTY_BYTEARRAY);
							super.def.setLevel(Deflater.NO_COMPRESSION);
							super.deflate();
							super.def.setLevel(Deflater.BEST_COMPRESSION);
							super.deflate();
						}
					};
				}
			}

			Inflater decompressor = new Inflater(false);
			final InflaterInputStream is = new InflaterInputStream(socket.getInputStream(), decompressor);
			reader = new TextStreamReader(is);

			context.getSessionObject().setProperty(Scope.stream, Connector.COMPRESSED_KEY, true);
			log.info("ZLIB compression started");

			restartStream();
		} catch (Exception e) {
			log.log(Level.SEVERE, "Can't establish compressed connection", e);
			onError(null, e);
		} finally {
			context.getSessionObject().setProperty(Scope.stream, DISABLE_KEEPALIVE_KEY, Boolean.FALSE);
		}
	}

	public void processElement(Element elem) throws JaxmppException {
		if (log.isLoggable(Level.FINEST))
			log.finest("RECV: " + elem.getAsString());
		if (elem != null && elem.getXMLNS() != null && elem.getXMLNS().equals("urn:ietf:params:xml:ns:xmpp-tls")) {
			onTLSStanza(elem);
		} else if (elem != null && elem.getXMLNS() != null && "http://jabber.org/protocol/compress".equals(elem.getXMLNS())) {
			onZLibStanza(elem);
		} else {
			onResponse(elem);
		}
	}

	private void reconnect(final String newHost) {
		log.info("See other host: " + newHost);
		try {
			terminateAllWorkers();

			Object x1 = this.context.getSessionObject().getProperty(Jaxmpp.SYNCHRONIZED_MODE);

			this.context.getSessionObject().clear(SessionObject.Scope.stream);
			this.context.getSessionObject().setProperty(SERVER_HOST, newHost);
			worker = null;
			reader = null;
			writer = null;

			this.context.getSessionObject().setProperty(RECONNECTING_KEY, Boolean.TRUE);
			this.context.getSessionObject().setProperty(Jaxmpp.SYNCHRONIZED_MODE, x1);

			log.finest("Waiting for workers termination");

			// start();
		} catch (JaxmppException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void restartStream() throws XMLException, JaxmppException {
		StringBuilder sb = new StringBuilder();
		sb.append("<stream:stream ");

		final BareJID from = context.getSessionObject().getProperty(SessionObject.USER_BARE_JID);
		String to;
		Boolean seeOtherHost = context.getSessionObject().getProperty(SEE_OTHER_HOST_KEY);
		if (from != null && (seeOtherHost == null || seeOtherHost)) {
			to = from.getDomain();
			sb.append("from='").append(from.toString()).append("' ");
		} else {
			to = context.getSessionObject().getProperty(SessionObject.DOMAIN_NAME);
		}

		if (to != null) {
			sb.append("to='").append(to).append("' ");
		}

		sb.append("xmlns='jabber:client' ");
		sb.append("xmlns:stream='http://etherx.jabber.org/streams' ");
		sb.append("version='1.0'>");

		if (log.isLoggable(Level.FINEST))
			log.finest("Restarting XMPP Stream");
		send(sb.toString().getBytes(StreamTools.UTF));
	}

	public void send(byte[] buffer) throws JaxmppException {
		synchronized (ioMutex) {
			if (writer != null)
				try {
					if (log.isLoggable(Level.FINEST))
						log.finest("Send: " + new String(buffer));
					writer.write(buffer);
					writer.flush();
				} catch (IOException e) {
					throw new JaxmppException(e);

				}
		}
	}

	@Override
	public void send(Element stanza) throws XMLException, JaxmppException {
		synchronized (ioMutex) {
			if (writer != null)
				try {
					String t = stanza.getAsString();
					if (log.isLoggable(Level.FINEST))
						log.finest("Send: " + t);

					try {
						context.getEventBus().fire(new StanzaSendingEvent(context.getSessionObject(), stanza));
					} catch (Exception e) {
					}
					writer.write(t.getBytes(StreamTools.UTF));
				} catch (IOException e) {
					terminateAllWorkers();
					throw new JaxmppException(e);
				}
		}
		try {
			Thread.sleep((2));
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	protected void setStage(State state) throws JaxmppException {
		State s = this.context.getSessionObject().getProperty(CONNECTOR_STAGE_KEY);
		this.context.getSessionObject().setProperty(Scope.stream, CONNECTOR_STAGE_KEY, state);
		if (s != state) {
			log.fine("Connector state changed: " + s + "->" + state);
			context.getEventBus().fire(new StateChangedEvent(context.getSessionObject(), s, state));
			if (state == State.disconnected) {
				fireOnTerminate(context.getSessionObject());
			}
		}
	}

	@Override
	public void start() throws XMLException, JaxmppException {
		log.fine("Start connector.");
		if (timer != null) {
			try {
				timer.cancel();
			} catch (Exception e) {
			}
		}
		timer = new Timer(true);

		if (context.getSessionObject().getProperty(TRUST_MANAGERS_KEY) == null)
			context.getSessionObject().setProperty(TRUST_MANAGERS_KEY, new TrustManager[] { dummyTrustManager });

		if (context.getSessionObject().getProperty(HOSTNAME_VERIFIER_DISABLED_KEY) == Boolean.TRUE) {
			context.getSessionObject().setProperty(HOSTNAME_VERIFIER_KEY, null);
		} else if (context.getSessionObject().getProperty(HOSTNAME_VERIFIER_KEY) == null) {
			context.getSessionObject().setProperty(HOSTNAME_VERIFIER_KEY, DEFAULT_HOSTNAME_VERIFIER);
		}

		setStage(State.connecting);

		try {
			Entry serverHost = getHostFromSessionObject();
			if (serverHost == null) {
				String x = context.getSessionObject().getProperty(SessionObject.DOMAIN_NAME);
				log.info("Resolving SRV recrd of domain '" + x + "'");
				List<Entry> xx;
				DnsResolver dnsResolver = UniversalFactory.createInstance(DnsResolver.class.getName());
				if (dnsResolver != null) {
					xx = dnsResolver.resolve(x);
				} else {
					xx = DNSResolver.resolve(x);
				}

				if (xx.size() > 0) {
					serverHost = xx.get(0);
				}
			}

			context.getSessionObject().setProperty(Scope.stream, DISABLE_KEEPALIVE_KEY, Boolean.FALSE);

			if (log.isLoggable(Level.FINER))
				log.finer("Preparing connection to " + serverHost);

			InetAddress x = InetAddress.getByName(serverHost.getHostname());
			log.info("Opening connection to " + x + ":" + serverHost.getPort());
			socket = new Socket(x, serverHost.getPort());
			// if
			// (context.getSessionObject().getProperty(DISABLE_SOCKET_TIMEOUT_KEY)
			// == null
			// || ((Boolean)
			// context.getSessionObject().getProperty(DISABLE_SOCKET_TIMEOUT_KEY)).booleanValue())
			// {
			// socket.setSoTimeout(SOCKET_TIMEOUT);
			// }
			socket.setSoTimeout(SOCKET_TIMEOUT);
			socket.setKeepAlive(false);
			socket.setTcpNoDelay(true);
			// writer = new BufferedOutputStream(socket.getOutputStream());
			writer = socket.getOutputStream();
			reader = new TextStreamReader(socket.getInputStream());
			worker = new Worker(this) {

				@Override
				protected Reader getReader() {
					return SocketConnector.this.reader;
				}

				@Override
				protected void onErrorInThread(Exception e) throws JaxmppException {
					SocketConnector.this.onErrorInThread(e);
				}

				@Override
				protected void onStreamStart(Map<String, String> attribs) {
					SocketConnector.this.onStreamStart(attribs);
				}

				@Override
				protected void onStreamTerminate() throws JaxmppException {
					SocketConnector.this.onStreamTerminate();
				}

				@Override
				protected void processElement(Element elem) throws JaxmppException {
					SocketConnector.this.processElement(elem);
				}

				@Override
				protected void workerTerminated() {
					SocketConnector.this.workerTerminated(this);
				}

			};
			log.finest("Starting worker...");
			worker.start();

			restartStream();

			setStage(State.connected);

			this.pingTask = new TimerTask() {

				@Override
				public void run() {
					new Thread() {
						@Override
						public void run() {
							try {
								keepalive();
							} catch (JaxmppException e) {
								log.log(Level.SEVERE, "Can't ping!", e);
							}
						}
					}.start();
				}
			};
			long delay = SOCKET_TIMEOUT - 1000 * 5;

			if (log.isLoggable(Level.CONFIG))
				log.config("Whitespace ping period is setted to " + delay + "ms");

			if (context.getSessionObject().getProperty(EXTERNAL_KEEPALIVE_KEY) == null
					|| ((Boolean) context.getSessionObject().getProperty(EXTERNAL_KEEPALIVE_KEY) == false)) {
				timer.schedule(pingTask, delay, delay);
			}

			fireOnConnected(context.getSessionObject());
		} catch (Exception e) {
			terminateAllWorkers();
			onError(null, e);
			throw new JaxmppException(e);
		}
	}

	public void startTLS() throws JaxmppException {
		if (writer != null)
			try {
				log.fine("Start TLS");
				Element e = ElementFactory.create("starttls", null, "urn:ietf:params:xml:ns:xmpp-tls");
				send(e.getAsString().getBytes(StreamTools.UTF));
			} catch (Exception e) {
				throw new JaxmppException(e);
			}
	}

	/**
	 * Sends <compress/> stanza to start stream compression using ZLIB
	 *
	 * @throws JaxmppException
	 */
	public void startZLib() throws JaxmppException {
		if (writer != null)
			try {
				log.fine("Start ZLIB");
				Element e = ElementFactory.create("compress", null, "http://jabber.org/protocol/compress");
				e.addChild(ElementFactory.create("method", "zlib", null));
				send(e.getAsString().getBytes(StreamTools.UTF));
			} catch (Exception e) {
				throw new JaxmppException(e);
			}
	}

	@Override
	public void stop() throws JaxmppException {
		if (getState() == State.disconnected)
			return;
		terminateStream();
		setStage(State.disconnecting);
		terminateAllWorkers();
	}

	@Override
	@Deprecated
	public void stop(boolean terminate) throws JaxmppException {

	}

	private void terminateAllWorkers() throws JaxmppException {
		log.finest("Terminating all workers");
		if (this.pingTask != null) {
			this.pingTask.cancel();
			this.pingTask = null;
		}
		setStage(State.disconnected);
		try {
			if (socket != null)
				socket.close();
		} catch (IOException e) {
			log.log(Level.FINEST, "Problem with closing socket", e);
		}
		try {
			if (worker != null)
				worker.interrupt();
		} catch (Exception e) {
			log.log(Level.FINEST, "Problem with interrupting w2", e);
		}
		try {
			if (timer != null)
				timer.cancel();
		} catch (Exception e) {
			log.log(Level.FINEST, "Problem with canceling timer", e);
		} finally {
			timer = null;
		}
	}

	private void terminateStream() throws JaxmppException {
		final State state = getState();
		if (state == State.connected || state == State.connecting || state == State.disconnecting) {
			String x = "</stream:stream>";
			log.fine("Terminating XMPP Stream");
			send(x.getBytes(StreamTools.UTF));
			System.out.println(x);
		} else
			log.fine("Stream terminate not sent, because of connection state==" + state);
	}

	private void workerTerminated(final Worker worker) {
		try {
			setStage(State.disconnected);
		} catch (JaxmppException e) {
		}
		log.finest("Worker terminated");
		try {
			if (this.context.getSessionObject().getProperty(RECONNECTING_KEY) == Boolean.TRUE) {
				this.context.getSessionObject().setProperty(RECONNECTING_KEY, null);
				context.getEventBus().fire(new HostChangedEvent(context.getSessionObject()));
				log.finest("Restarting...");
				start();
			} else {
				context.getEventBus().fire(new DisconnectedHandler.DisconnectedEvent(context.getSessionObject()));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
