package com.iPlante.Driver.Serial;

import gnu.io.CommPort;
import gnu.io.CommPortIdentifier;
import gnu.io.NoSuchPortException;
import gnu.io.PortInUseException;
import gnu.io.SerialPort;
import gnu.io.SerialPortEvent;
import gnu.io.SerialPortEventListener;
import gnu.io.UnsupportedCommOperationException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.TooManyListenersException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StickPort {

	private static final Logger logger = LoggerFactory.getLogger(StickPort.class);
	private static final Logger _messageLogger = LoggerFactory.getLogger("StickPort-Logger");

	public static final int SERIAL_BUFFER_MAXLENGTH = 1024;
	private static final int BUFFER_MAXLENGTH = 100;
	private static final Pattern MAC_ADRESS_PATTERN = Pattern.compile( "^([0-9a-fA-F]{8})\\b" );

	private final static int DEFAULT_PORT_SPEED = 115200;
	private final static int DEFAULT_PORT_BITS = SerialPort.DATABITS_8;
	private final static int DEFAULT_PORT_STOP_BITS = SerialPort.STOPBITS_1;	
	private final static int DEFAULT_PORT_PARITY = SerialPort.PARITY_NONE;
	
	private SerialPort _stickPort = null;
	//	Integer _rfPort = Integer.valueOf(1); 
	private List<RFListener> _listeners = new ArrayList<RFListener>();
	private String _pendingAckMessage = null;
	private BlockingQueue<String> _sendingQueue = new ArrayBlockingQueue<String>(BUFFER_MAXLENGTH);
	private Thread _writer;
	private final String _name;

	public enum RF_CMD {
		TX_SEND_NO_ACK	(0x00),  // Commands sent from server to client
		TX_SEND_ACK		(0x01),
		TX_LINK			(0x02),
		TX_UNLINK         (0x03),
		TX_START_LISTEN   (0x04),
		TX_STOP_LISTEN    (0x05),
		TX_PING           (0x06),
		TX_DEBUG_ON       (0x07),
		TX_DEBUG_OFF      (0x08),
		TX_GET_CLIENT_LIST(0x09),
		RX_RECEIVE        (0x0A), // Commands sent from client to server
		RX_LINKED         (0x0B),
		RX_UNLINKED       (0x0C),
		RX_ALIVE          (0x0D),
		RX_DEBUG          (0x0E),
		TX_SET_ADDR       (0x0F),
		TX_SEND_DELAYED   (0x10),
		RX_SEND_DELAYED   (0x11),
		UNKNOWN			(0xFF);

		private int _cmd;

		public static RF_CMD getCommand( String msg ) {
			for( RF_CMD cmd : RF_CMD.values() ) {
				if ( cmd.getCodeHex().equalsIgnoreCase(msg) )
					return cmd;
			}
			return UNKNOWN;
		}

		private RF_CMD( int cmd ) {
			_cmd = cmd;
		}

		public String getCodeHex() {
			return String.format("%02X", _cmd);
		}
	}

	public enum RF_STATUS {
		SMPL_SUCCESS			(0x00),
		SMPL_TIMEOUT			(0x01),
		SMPL_BAD_PARAM			(0x02),
		SMPL_NOMEM				(0x03),
		SMPL_NO_FRAME			(0x04),
		SMPL_NO_LINK			(0x05),
		SMPL_NO_JOIN			(0x06),
		SMPL_NO_CHANNEL			(0x07),
		SMPL_NO_PEER_UNLINK		(0x08),
		SMPL_TX_CCA_FAIL		(0x09),
		SMPL_NO_PAYLOAD			(0x0A),
		SMPL_NO_AP_ADDRESS		(0x0B),
		SMPL_NO_ACK				(0x0C),
		UNKNOWN					(0xFF);

		private int _code;

		public static RF_STATUS getStatus( String msg ) {
			for( RF_STATUS ack : RF_STATUS.values() ) {
				if ( ack.getCodeHex().equalsIgnoreCase(msg) )
					return ack;
			}
			return UNKNOWN;
		}

		RF_STATUS( int code ) {
			_code = code;
		}

		String getCodeHex() {
			return String.format("%02X", _code);
		}
	}

	public static Integer extractMacAddress(String msg) {
		Matcher matcher_l = MAC_ADRESS_PATTERN.matcher(msg);
		if ( matcher_l.find() ) {
			return (int) Long.parseLong( matcher_l.group(1), 16 );
		}

		return null;
	};

	public interface RFListener {

		public void processEvent( RF_STATUS status_p, Integer rssi_p, String msg_p );
		public void processLink(RF_STATUS status_p, Integer rssi_p, String msg );
		public void processUnlink(RF_STATUS status_p, Integer rssi_p, String msg );
		public void processAck(RF_CMD cmd_p, RF_STATUS status_p, String substring_p);
		public void processAlive(RF_STATUS status_p, Integer rssi_p, String msg );
	}

	public StickPort(String name) {
		_name = name;
	}

	public void open() {

		String name = "/dev/ttyUSB0";
		try {
			CommPortIdentifier portIdentifier = CommPortIdentifier.getPortIdentifier( name );
			if ( portIdentifier.isCurrentlyOwned() )
			{
				logger.error("Error: Port " + name + " is currently in use"); //$NON-NLS-1$ //$NON-NLS-2$
			}
			else
			{
				CommPort commPort = portIdentifier.open(this.getClass().getName(),2000);
				commPort.enableReceiveTimeout(1000);  // A priori pour gerer proprement les ruptures du Comm
				commPort.enableReceiveThreshold(0);

				if ( commPort instanceof SerialPort )
				{
					_stickPort = (SerialPort) commPort;
					_stickPort.setSerialPortParams(StickPort.DEFAULT_PORT_SPEED,	StickPort.DEFAULT_PORT_BITS,
							StickPort.DEFAULT_PORT_STOP_BITS, StickPort.DEFAULT_PORT_PARITY);

					try {
						// TODO 2nde radio ?
						_messageLogger.info("RF-Open " + _stickPort.getName() );
						SerialReader sReader = new SerialReader( _stickPort.getInputStream() );
						_stickPort.addEventListener( sReader );
						_stickPort.notifyOnDataAvailable(true);
						_writer = new Thread( new SerialWriter( _stickPort.getOutputStream() ), "RF " + _name + " Serial Port Writer");
						_writer.start();
					} catch (IOException e) {
						logger.error("open(String)", e); //$NON-NLS-1$
					} catch (TooManyListenersException e) {
						logger.error("open(String)", e); //$NON-NLS-1$
					}
				}
				else
				{
					logger.error("Error: " + _name + " Not a serial port"); //$NON-NLS-1$
				}
			}     
		} catch (UnsupportedCommOperationException e) {
			logger.error("open("+name+")", e); //$NON-NLS-1$
		} catch (PortInUseException e) {
			logger.error("open("+name+")", e); //$NON-NLS-1$
		} catch (NoSuchPortException e) {
			logger.error("open("+name+")", e); //$NON-NLS-1$
		}                
	}

	public void close() {
		try {
			if ( _stickPort != null ) {
				_messageLogger.info("SitckPort" + _name + "-Close " + _stickPort.getName() );
				_stickPort.removeEventListener();
				try {
					_stickPort.getOutputStream().close();
				} catch (IOException e) {
					e.printStackTrace();
				}
				try {
					_stickPort.getInputStream().close();
				} catch (IOException e) {
					e.printStackTrace();
				}
				_stickPort.close();					
				_stickPort = null;
			}				
			if ( _writer != null ) {
				_writer.interrupt();
				_writer.join();
				_writer = null;
			}
			if ( _sendingQueue != null )
				_sendingQueue.clear();
		}
		catch( InterruptedException e) {
			logger.error("close()", e); //$NON-NLS-1$
		}
	}

	public void send( String msg ) {
		if ( _stickPort != null ) {
			try {
				_sendingQueue.put( msg );
			} catch (InterruptedException e) {
				logger.error("send " + _name + " (String)", e); //$NON-NLS-1$
			}
		}
		else {
			logger.info("Ok FAKE emission RF " + _name + " ..." + msg + "..."); //$NON-NLS-1$ //$NON-NLS-2$
			_messageLogger.info("RF-TX FAKE " + msg);
		}
	}

//	public void send(PrintStream ps, String msg ) {
//		if ( _rfPort != null ) {
//			try {
//				_sendingQueue.put( msg );
//			} catch (InterruptedException e) {
//				if(ps == null)
//					logger.error(e.getMessage(), e); //$NON-NLS-1$
//				else
//					e.printStackTrace(ps);
//			}
//		} else {
//			logger.info("Ok FAKE emission RF ..." + msg + "..."); //$NON-NLS-1$ //$NON-NLS-2$
//			_messageLogger.info("RF-TX FAKE " + msg);
//		}
//	}

	public boolean addListener(RFListener e_p) {
		return _listeners.add(e_p);
	}

	public boolean removeListener(Object o_p) {
		return _listeners.remove(o_p);
	}

	/**
	 * Handles the input coming from the serial port. A new line character
	 * is treated as the end of a block in this example. 
	 */
	class SerialReader implements SerialPortEventListener {
		private InputStream in;
		private byte[] buffer = new byte[StickPort.SERIAL_BUFFER_MAXLENGTH];
		int len = 0;

		public SerialReader ( InputStream in )
		{
			this.in = in;
		}

		public void serialEvent(SerialPortEvent arg0) {
			int data;

			try
			{
				while ( (data = in.read()) > -1 )
				{
					//                	len += in.read( buffer, len, SERIAL_BUFFER_MAXLENGTH -len);
					if ( data == '\n' || data == '\r' || data == '\0' ) {
						if ( len != 0 ) {
							try {
								String msg = new String(buffer,0,len);
								_messageLogger.info("RF-RX " + _name + " " + _stickPort.getName() + " " + msg);
								trtMsg( msg );
							}
							catch( Exception e ) {
								logger.error("Error : Exception catch in RF " + _name + " msg processing", e); //$NON-NLS-1$
							}
							len = 0;
						}
					}
					else if ( len < StickPort.SERIAL_BUFFER_MAXLENGTH )
						buffer[len++] = (byte) data;
					else {
						logger.error("Buffer RF " + _name + " overflow --> RESET"); //$NON-NLS-1$
						len  = 0;
					}
				}
			}
			catch ( IOException e )
			{
				logger.error(_name + " serialEvent(SerialPortEvent)", e); //$NON-NLS-1$
			}             
		}
	}

	protected void trtMsg( String msg ) {
		//		System.out.println("Packet RF reçu ..." + msg + "...");

		boolean ack = false;
		RF_CMD cmd = null;
		RF_STATUS status = null;
		Integer rssi = null;

		if ( msg.length() >= 8 ) {
			cmd = RF_CMD.getCommand( msg.substring( 0, 2 ) );
			status = RF_STATUS.getStatus( msg.substring( 3, 5 ) );
			try {
				rssi = (int) Long.parseLong(msg.substring( 6, 8 ), 16);
			}
			catch ( NumberFormatException e ) {
			}
		}

		if ( cmd == RF_CMD.UNKNOWN || rssi == null || status == RF_STATUS.UNKNOWN ) {
			logger.error("Error : RF " + _name + " RX, bad frame received : ..." + msg + "..."); //$NON-NLS-1$
			return;
		}

		if( _stickPort != null ) { // Si le port n'est pas ouvert, on fait qd meme -- Mode Simulation
			synchronized (_stickPort ) {

				if ( _pendingAckMessage != null ) {
					if ( _pendingAckMessage.length() + 6 == msg.length() &&
							_pendingAckMessage.startsWith( msg.substring( 0, 3 )) &&
							_pendingAckMessage.endsWith( msg.substring( 9, msg.length() )) ) {
						logger.trace("ACK RF " + _name + " reçu ..." + msg + "..."); //$NON-NLS-1$
						ack = true;
						_pendingAckMessage = null;
						_stickPort.notify();
					}
				}
			}
		}

		for(RFListener lst : _listeners ) {
			if ( ack ) {
				lst.processAck( cmd, status, msg.substring(9) );					
			}
			else switch ( cmd ) {
			case RX_RECEIVE:
				lst.processEvent( status, rssi, msg.substring(9) );
				break;
			case RX_LINKED:
				lst.processLink( status, rssi, msg.substring(9) );
				break;
			case RX_UNLINKED:
				lst.processUnlink( status, rssi, msg.substring(9) );
				break;
			case RX_ALIVE:
				lst.processAlive( status, rssi, msg.substring(9) );
				break;
			case RX_DEBUG:
				logger.info("RF " + _name + " Debug : ..." + msg + "..."); //$NON-NLS-1$ //$NON-NLS-2$
				break;
			case RX_SEND_DELAYED:
				lst.processAck( cmd, status, msg.substring(9) );					
				break;
			case TX_GET_CLIENT_LIST:
				if( _stickPort != null ) { // Si le port n'est pas ouvert, on fait qd meme -- Mode Simulation
					synchronized (_stickPort ) { // Code pourri car le TX est une sorte d'acquittement
						_pendingAckMessage = null;
						_stickPort.notify();
					}
				}
				lst.processAck( cmd, status, msg.substring(9) );					
				break;
			case TX_SEND_NO_ACK:
				logger.info(_name + "TX SEND NO ACK: ..." + msg + "..."); //$NON-NLS-1$ //$NON-NLS-2$
			

			default:
				logger.error("Error : Unknown RF " + _name + " msg reception ..." + msg + "..."); //$NON-NLS-1$ //$NON-NLS-2$
				break;
			}
		}
	}

	public void simulationReceptionMsg( String msg ) {
		trtMsg( msg );
	}

	public void simulationACKEmissionRF( String msg ) {
		if( _stickPort != null ) { // Si le port n'est pas ouvert, on fait qd meme -- Mode Simulation
			synchronized (_stickPort ) {
				if ( _pendingAckMessage != null && _pendingAckMessage.equals( msg )) {
					logger.info("Simul ACK RF " + _name + " reçu ..." + msg + "..."); //$NON-NLS-1$ //$NON-NLS-2$
					_pendingAckMessage = null;
					_stickPort.notify();
					return;
				}
			}
		}
		else
			logger.info("Simul ACK RF " + _name + " reçu IGNORE ..." + msg + "..."); //$NON-NLS-1$ //$NON-NLS-2$
	}

	/**
	 * Handles the input coming from the serial port. A new line character
	 * is treated as the end of a block in this example. 
	 */
	class SerialWriter implements Runnable {
		private OutputStream _out;
		int len = 0;

		public SerialWriter ( OutputStream out ) {
			_out = out;
		}        

		@Override
		public void run() {
			String msg = null;

			while( true ) {
				try {
					msg = _sendingQueue.take();				
					if ( msg != null && _stickPort != null ) {
						synchronized ( _stickPort ) {
							while ( _pendingAckMessage != null ) {
								logger.trace("Attente emission RF"); //$NON-NLS-1$
								_stickPort.wait();
							}
							logger.info("Ok emission RF " + _name + " ..." + msg + "..."); //$NON-NLS-1$ //$NON-NLS-2$
							_pendingAckMessage = msg;
						}
						_messageLogger.info("RF" + _name + "-TX " + _stickPort.getName() + " " + msg);
						_out.write(msg.getBytes(), 0, msg.length());
						_out.write('\r');
					}
					else if ( msg != null ){
						logger.info("Ok FAKE emission RF " + _name + " ..." + msg + "..."); //$NON-NLS-1$ //$NON-NLS-2$
						_messageLogger.info("RF" + _name + "-TX FAKE " + msg);						
					}
				} catch (InterruptedException e) {
					logger.trace("Thread " + _name + " writer interrompu --> Fin"); //$NON-NLS-1$
					break;
				} catch (IOException e) {
					logger.error("run()", e); //$NON-NLS-1$
				} catch (NullPointerException e) {
					logger.error("run()", e); //$NON-NLS-1$
				}
			}
		}
	}

}
