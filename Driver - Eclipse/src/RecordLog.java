import java.util.List;

import org.json.JSONException;
import org.json.JSONObject;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.iPlante.DataModel.Record;
import com.iPlante.Driver.Serial.StickPort;
import com.iPlante.Driver.Serial.StickPort.IPlanteDataListener;

public class RecordLog extends ServerResource {
	private static final Logger logger = LoggerFactory
			.getLogger(StickPort.class);
	private static final Logger _messageLogger = LoggerFactory
			.getLogger("Record-Logger");

	String dump = null;

	boolean acked = false;

	Record record;

	@Get
	public String toString() {
		StickPort port = new StickPort("StickPort");

		port.addListener(new IPlanteDataListener() {

			@Override
			public void processDump(String msg) {
				// TODO Auto-generated method stub

			}

			@Override
			public void processRecord(Record r, String s) {
				// TODO Auto-generated method stub
				dump = s;
				acked = true;
				_messageLogger.info("PROCESSING RECORD");
				_messageLogger.info(r.toString());
				record = r;
			}

			@Override
			public void processRecord(Record r) {
				// TODO Auto-generated method stub

			}

			@Override
			public void processDump(List<Record> records) {
				// TODO Auto-generated method stub

			}

		});

		port.open();

		try {
			Thread.sleep(2000);
			port.send("R001");
			Thread.sleep(100);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		while (acked != true) {
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			_messageLogger.info("WAITING FOR DATA");
		}

		// port.close();

		if (dump != null) {

			JSONObject jsonRep = new JSONObject();
			try {
				jsonRep.put("date", record.day + "/" + record.month + "/"
						+ record.year + " " + record.hour + ":" + record.min
						+ ":" + record.sec);
				jsonRep.put("humi", record.humi);
				jsonRep.put("lumi", record.lumi);
				jsonRep.put("tempIn", record.tempIn);
				jsonRep.put("tempOut", record.tempOut);
				
			} catch (JSONException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			return jsonRep.toString();
		} else
			return "NO LOG";
		// Print the requested URI path
		/*
		 * return "Resource URI  : " + getReference() + '\n' +
		 * "Root URI      : " + getRootRef() + '\n' + "Routed part   : " +
		 * getReference().getBaseRef() + '\n' + "Remaining part: " +
		 * getReference().getRemainingPart();
		 */
	}

}
