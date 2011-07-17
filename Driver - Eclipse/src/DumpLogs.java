import java.util.List;

import org.json.JSONException;
import org.json.JSONObject;
import org.restlet.Component;
import org.restlet.Server;
import org.restlet.data.Protocol;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.iPlante.DataModel.Record;
import com.iPlante.Driver.Serial.StickPort;
import com.iPlante.Driver.Serial.StickPort.IPlanteDataListener;

public class DumpLogs extends ServerResource {

	private static final Logger logger = LoggerFactory
			.getLogger(StickPort.class);

	private static final Logger _messageLogger = LoggerFactory
			.getLogger("DumpLogs-Logger");

	String dump = null;

	List<Record> recordsList;

	boolean acked = false;

	@Get
	public String toString() {
		StickPort port = new StickPort("StickPort");

		port.addListener(new IPlanteDataListener() {

			@Override
			public void processDump(String msg) {
				// TODO Auto-generated method stub
				dump = msg;
				acked = true;
				_messageLogger.info("PROCESSING DUMP");
			}

			@Override
			public void processRecord(Record r, String s) {
				// TODO Auto-generated method stub
			}

			@Override
			public void processRecord(Record r) {
				// TODO Auto-generated method stub

			}

			@Override
			public void processDump(List<Record> records) {
				// TODO Auto-generated method stub
				acked = true;
				recordsList = records;
			}

		});

		port.open();

		try {
			Thread.sleep(2000);
			port.send("RAXX");
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

		if (recordsList != null) {
			JSONObject jsonRep = new JSONObject();
			int i = 0;
			for (Record record : recordsList) {
				JSONObject object = new JSONObject();
				try {
					object.put("date", record.day + "/" + record.month + "/"
							+ record.year + " " + record.hour + ":"
							+ record.min + ":" + record.sec);
					object.put("humi", record.humi);
					object.put("lumi", record.lumi);
					object.put("tempIn", record.tempIn);
					object.put("tempOut", record.tempOut);
					jsonRep.put("" + i, object);
					i++;
				} catch (JSONException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			return jsonRep.toString();
		} else
			return "NO DUMP";
	}

}