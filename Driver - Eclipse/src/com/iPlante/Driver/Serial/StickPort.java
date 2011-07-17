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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.iPlante.DataModel.Record;

public class StickPort {

	private static final Logger logger = LoggerFactory
			.getLogger(StickPort.class);
	private static final Logger _messageLogger = LoggerFactory
			.getLogger("StickPort-Logger");

	public static final int SERIAL_BUFFER_MAXLENGTH = 1024;
	private static final int BUFFER_MAXLENGTH = 100;

	private final static int DEFAULT_PORT_SPEED = 19200;
	private final static int DEFAULT_PORT_BITS = SerialPort.DATABITS_8;
	private final static int DEFAULT_PORT_STOP_BITS = SerialPort.STOPBITS_1;
	private final static int DEFAULT_PORT_PARITY = SerialPort.PARITY_NONE;
	private static final String PORT_NAME = "/dev/tty.usbserial-A800f7Zr";

	private SerialPort _stickPort = null;
	private List<IPlanteDataListener> _listeners = new ArrayList<IPlanteDataListener>();
	private BlockingQueue<String> _sendingQueue = new ArrayBlockingQueue<String>(
			BUFFER_MAXLENGTH);
	private Thread _writer;
	private final String _name;

	public interface IPlanteDataListener {

		public void processDump(String msg);

		public void processRecord(Record r);

		void processRecord(Record r, String s);

	}

	public StickPort(String name) {
		_name = name;
	}

	public void open() {

		String name = StickPort.PORT_NAME;
		try {
			CommPortIdentifier portIdentifier = CommPortIdentifier
					.getPortIdentifier(name);
			if (portIdentifier.isCurrentlyOwned()) {
				logger.error("Error: Port " + name + " is currently in use"); //$NON-NLS-1$ //$NON-NLS-2$
			} else {
				CommPort commPort = portIdentifier.open(this.getClass()
						.getName(), 2000);
				commPort.enableReceiveTimeout(1000); // A priori pour gerer
														// proprement les
														// ruptures du Comm
				commPort.enableReceiveThreshold(0);

				if (commPort instanceof SerialPort) {
					_stickPort = (SerialPort) commPort;
					_stickPort.setSerialPortParams(
							StickPort.DEFAULT_PORT_SPEED,
							StickPort.DEFAULT_PORT_BITS,
							StickPort.DEFAULT_PORT_STOP_BITS,
							StickPort.DEFAULT_PORT_PARITY);

					try {
						// TODO 2nde radio ?
						_messageLogger.info("RF-Open " + _stickPort.getName());
						SerialReader sReader = new SerialReader(
								_stickPort.getInputStream());
						_stickPort.addEventListener(sReader);
						_stickPort.notifyOnDataAvailable(true);
						_writer = new Thread(new SerialWriter(
								_stickPort.getOutputStream()), "Stick " + _name
								+ " Serial Port Writer");
						_writer.start();
						_messageLogger.info("Started writer thread");
						send("douetounet");

					} catch (IOException e) {
						logger.error("open(String)", e); //$NON-NLS-1$
					} catch (TooManyListenersException e) {
						logger.error("open(String)", e); //$NON-NLS-1$
					}
				} else {
					logger.error("Error: " + _name + " Not a serial port"); //$NON-NLS-1$
				}
			}
		} catch (UnsupportedCommOperationException e) {
			logger.error("open(" + name + ")", e); //$NON-NLS-1$
		} catch (PortInUseException e) {
			logger.error("open(" + name + ")", e); //$NON-NLS-1$
		} catch (NoSuchPortException e) {
			logger.error("open(" + name + ")", e); //$NON-NLS-1$
		}
	}

	public void close() {
		try {
			if (_stickPort != null) {
				_messageLogger.info("SitckPort" + _name + "-Close "
						+ _stickPort.getName());
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
			if (_writer != null) {
				_writer.interrupt();
				_writer.join();
				_writer = null;
			}
			if (_sendingQueue != null)
				_sendingQueue.clear();
		} catch (InterruptedException e) {
			logger.error("close()", e); //$NON-NLS-1$
		}
	}

	public void send(String msg) {
		if (_stickPort != null) {
			try {
				_sendingQueue.put(msg);
			} catch (InterruptedException e) {
				logger.error("send " + _name + " (String)", e); //$NON-NLS-1$
			}
		}
	}

	public boolean addListener(IPlanteDataListener e_p) {
		return _listeners.add(e_p);
	}

	public boolean removeListener(Object o_p) {
		return _listeners.remove(o_p);
	}

	/**
	 * Handles the input coming from the serial port. A new line character is
	 * treated as the end of a block in this example.
	 */
	class SerialReader implements SerialPortEventListener {
		private InputStream in;
		int len = 0;
		String sReadBuff = "";
		
		public SerialReader(InputStream in) {
			this.in = in;
		}

		public void serialEvent(SerialPortEvent arg0) {
			_messageLogger.info("Serial Event type " + arg0.getEventType());
			byte[] readBuffer = new byte[1024];
			int numBytes;
			int numBytesTotal = 0;

			try {
				while (in.available() > 0) {
					numBytes = in.read(readBuffer);
					numBytesTotal += numBytes;
					String tmpR = new String(readBuffer);
					sReadBuff += tmpR.substring(0, numBytes);
				}
				if (sReadBuff.indexOf("\n") > -1){
					_messageLogger.info("msg has \\n, treat it");
					trtMsg(sReadBuff);
					sReadBuff = "";
				}if (sReadBuff.indexOf("\n\r") > -1){
					_messageLogger.info("msg has \\n\\r");
				}
				
			} catch (IOException e) {
				logger.error("Error Reading from serial port");
			}

		}
	}

	protected void trtMsg(String msg) {
		_messageLogger.info("Received msg " + msg);

		if (msg.equalsIgnoreCase("p")) {
			_messageLogger.info("Received Ping");
			send("dump");
			return;
		} else {
			String cmd = msg.substring(0, 4);

			for (IPlanteDataListener lst : _listeners) {
				if (cmd.startsWith("RA")) {
					_messageLogger.info("DUMP");
					lst.processDump(msg);
				} 
				//R111:Date:1/1/19700:0:47 T1:17.87 T2:17.38 Lumi:158 Humi:158
				else if (cmd.startsWith("R")){
					String back = msg;
					_messageLogger.info("RECORD");
					//split till :
					int indexOf = msg.indexOf(":");
					String nbRec = msg.substring(1,indexOf);
					int nb = Integer.parseInt(nbRec);
					_messageLogger.info("nbRec " + nb);
					
					msg = msg.substring(indexOf + 1, msg.length());
					
					indexOf = msg.indexOf(":");
					msg = msg.substring(indexOf + 1, msg.length());
					
					indexOf = msg.indexOf("/");
					String dayStr = msg.substring(0,indexOf);
					int day = Integer.parseInt(dayStr);
					msg = msg.substring(indexOf + 1, msg.length());
					
					indexOf = msg.indexOf("/");
					String monthStr = msg.substring(0,indexOf);
					int month = Integer.parseInt(monthStr);
					msg = msg.substring(indexOf + 1, msg.length());
					
					indexOf = msg.indexOf(",");
					String yearStr = msg.substring(0,indexOf);
					int year = Integer.parseInt(yearStr);
					msg = msg.substring(indexOf + 1, msg.length());
					_messageLogger.info("year " + year);
					
					indexOf = msg.indexOf(":");
					String hourStr = msg.substring(0,indexOf);
					int hour = Integer.parseInt(hourStr);
					msg = msg.substring(indexOf + 1, msg.length());
					_messageLogger.info("hour " + hour);
					
					indexOf = msg.indexOf(":");
					String minuteStr = msg.substring(0,indexOf);
					int minute = Integer.parseInt(minuteStr);
					msg = msg.substring(indexOf + 1, msg.length());
					_messageLogger.info("minute " + minute);
					
					indexOf = msg.indexOf(",");
					String secondStr = msg.substring(0,indexOf);
					int second = Integer.parseInt(secondStr);
					msg = msg.substring(indexOf + 1, msg.length());
					
					_messageLogger.info("Received Record " + nb + " : " + msg);
					_messageLogger.info(day+"/"+month+"/"+year+" "+hour+":" + minute + ":"+ second);
					
					float tempIn;
					float tempOut;
					int lumi;
					int humi;
					
					Record r = null;
					lst.processRecord(r,back);
				}
				else
					logger.error("Error : Unknown RF " + _name + " msg reception ..." + msg + "...");
			}
		}
	}

	public void askRecordNumbers() {
		send("RN");
	}

	/**
	 * Handles the input coming from the serial port. A new line character is
	 * treated as the end of a block in this example.
	 */
	class SerialWriter implements Runnable {
		private OutputStream _out;
		int len = 0;

		public SerialWriter(OutputStream out) {
			_out = out;
		}

		@Override
		public void run() {
			String msg = null;

			while (true) {
				try {
					msg = _sendingQueue.take();
					if (msg != null && _stickPort != null) {
						_messageLogger.info("Sending stuff");
						_out.write(msg.getBytes(), 0, msg.length());
						_out.write('\r');
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
