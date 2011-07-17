package com.iPlante.DataModel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.iPlante.Driver.Serial.StickPort;

public class Record {

	private static final Logger logger = LoggerFactory.getLogger(StickPort.class);
	private static final Logger _messageLogger = LoggerFactory.getLogger("Record-Logger");

	public int sec;
	public int min;
	public int hour;
	public int day;
	public int month;
	public int year;
	public float tempIn;
	public float tempOut;
	public int lumi;
	public int humi;

	@Override
	public String toString(){
		return "" + day + "/"+ month + "/"+ year + " "+ hour + 
		":" + " "+ min + ":" + " "+ sec + ",T1:" + " "+ tempIn + 
		",T2:" + " "+ tempOut + ",humi:" + humi + ",lumi:" + lumi + ""; 
	}
	
	public Record(int day2, int month2, int year2, int hour2, int minute2,
			int second, int lumi2, int humi2, float tempIn2, float tempExt) {
		sec = second;
		min = minute2;
		hour = hour2;
		day = day2;
		month = month2;
		year = year2;
		tempIn = tempIn2;
		tempOut = tempExt;
		lumi = lumi2;
		humi = humi2;
	}

	//Date:1/1/1970,0:0:47,T1:17.87,T2:17.38,Lumi:158,Humi:158
	public static Record parseRecord(String msg) {
		//remove Date:
		msg=msg.substring(5, msg.length());
		
		int indexOf = msg.indexOf("/");
		String dayStr = msg.substring(0, indexOf);
		int day = Integer.parseInt(dayStr);
		msg = msg.substring(indexOf + 1, msg.length());
		_messageLogger.info("Day " + day);
		
		indexOf = msg.indexOf("/");
		String monthStr = msg.substring(0, indexOf);
		int month = Integer.parseInt(monthStr);
		msg = msg.substring(indexOf + 1, msg.length());
		_messageLogger.info("Month " + month);
		
		indexOf = msg.indexOf(",");
		String yearStr = msg.substring(0, indexOf);
		int year = Integer.parseInt(yearStr);
		msg = msg.substring(indexOf + 1, msg.length());
		_messageLogger.info("year " + year);

		indexOf = msg.indexOf(":");
		String hourStr = msg.substring(0, indexOf);
		int hour = Integer.parseInt(hourStr);
		msg = msg.substring(indexOf + 1, msg.length());
		_messageLogger.info("hour " + hour);

		indexOf = msg.indexOf(":");
		String minuteStr = msg.substring(0, indexOf);
		int minute = Integer.parseInt(minuteStr);
		msg = msg.substring(indexOf + 1, msg.length());
		_messageLogger.info("minute " + minute);

		indexOf = msg.indexOf(",");
		String secondStr = msg.substring(0, indexOf);
		int second = Integer.parseInt(secondStr);
		msg = msg.substring(indexOf + 1, msg.length());
		_messageLogger.info("second " + second);

		//T1:17.87,T2:17.38,Lumi:158,Humi:158
		msg = msg.substring(3, msg.length());
		indexOf = msg.indexOf(",");
		String tempInStr = msg.substring(0, indexOf);
		float tempIn = Float.parseFloat(tempInStr);
		msg = msg.substring(indexOf + 1, msg.length());
		_messageLogger.info("tempIn " + tempIn);

		msg = msg.substring(3, msg.length());
		indexOf = msg.indexOf(",");
		String tempExtStr = msg.substring(0, indexOf);
		float tempExt = Float.parseFloat(tempExtStr);
		msg = msg.substring(indexOf + 1, msg.length());
		_messageLogger.info("tempExt " + tempExt);

		msg = msg.substring(5, msg.length());
		indexOf = msg.indexOf(",");
		String lumiStr = msg.substring(0, indexOf);
		int lumi = Integer.parseInt(lumiStr);
		msg = msg.substring(indexOf + 1, msg.length());
		_messageLogger.info("lumi " + lumi);

		msg = msg.substring(5, msg.length());
		msg = msg.trim();
		
		int humi = 0;
		if (msg.length() > 3) {
			humi = Integer.parseInt(msg.substring(0, 3));
		}
		else {
			humi = Integer.parseInt(msg);
		}
		_messageLogger.info("humi " + humi);

		Record r = new Record(day, month, year, hour, minute, second, lumi,
				humi, tempIn, tempExt);
		
		return r;
	}
}
