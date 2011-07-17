import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.iPlante.DataModel.Record;
import com.iPlante.Driver.Serial.StickPort;
import com.iPlante.Driver.Serial.StickPort.IPlanteDataListener;


public class RecordLog extends ServerResource{
	private static final Logger logger = LoggerFactory
	.getLogger(StickPort.class);
private static final Logger _messageLogger = LoggerFactory
	.getLogger("Record-Logger");

	String dump = null;
	
	boolean acked = false;
	
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
				dump=s;
				acked = true;
			    _messageLogger.info("PROCESSING RECORD");	
			}

			@Override
			public void processRecord(Record r) {
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

		while (acked !=true){
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		    _messageLogger.info("WAITING FOR DATA");	
		}
		
		//port.close();
		
		if (dump != null) {
			
			return dump;
		}
		else return "NO LOG";
		// Print the requested URI path
		/*return "Resource URI  : " + getReference() + '\n' + "Root URI      : "
				+ getRootRef() + '\n' + "Routed part   : "
				+ getReference().getBaseRef() + '\n' + "Remaining part: "
				+ getReference().getRemainingPart();
*/
	}

}
