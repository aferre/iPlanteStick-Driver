import org.restlet.Application;
import org.restlet.Component;
import org.restlet.Context;
import org.restlet.Request;
import org.restlet.Response;
import org.restlet.Restlet;
import org.restlet.data.MediaType;
import org.restlet.data.Protocol;
import org.restlet.representation.StringRepresentation;
import org.restlet.routing.Router;

import com.iPlante.Driver.Serial.StickPort;
import com.iPlante.Driver.Serial.StickPort.IPlanteDataListener;

public class RestletServer extends Application {

	static StickPort port = null;

	public RestletServer() {
		super();
	}

	public RestletServer(Context context) {
		super(context);
	
	}

	@Override
	public Restlet createRoot() {

		Router router = new Router(getContext());
		router.attach("/dump", DumpLogs.class);
		router.attach("/record", RecordLog.class);
		// router.attach("/users/{id}", UserResource.class);

		Restlet mainpage = new Restlet() {
			@Override
			public void handle(Request request, Response response) {
				StringBuilder stringBuilder = new StringBuilder();

				stringBuilder.append("<html>");
				stringBuilder
						.append("<head><title>Sample Application Servlet Page</title></head>");
				stringBuilder.append("<body bgcolor=white>");

				stringBuilder.append("<table border=\"0\">");
				stringBuilder.append("<tr>");
				stringBuilder.append("<td>");
				stringBuilder.append("<h1>2048Bits.com example - REST</h1>");
				stringBuilder.append("</td>");
				stringBuilder.append("</tr>");
				stringBuilder.append("</table>");
				stringBuilder.append("</body>");
				stringBuilder.append("</html>");

				response.setEntity(new StringRepresentation(stringBuilder
						.toString(), MediaType.TEXT_HTML));

			}
		};
		router.attach("/main", mainpage);
		return router;
	}

	public static void main(String[] args) throws Exception {
		/*
		 * // Create a new Restlet component and add a HTTP server connector to
		 * it Component component = new Component();
		 * component.getServers().add(Protocol.HTTP, 8182);
		 * 
		 * // Then attach it to the local host
		 * component.getDefaultHost().attach("/dump", DumpLogs.class);
		 * component.getDefaultHost(). // Now, let's start the component! //
		 * Note that the HTTP server connector is also automatically started.
		 * component.start();
		 */
		// Create a component
		Component component = new Component();
		component.getServers().add(Protocol.HTTP, 8100);

		RestletServer application = new RestletServer(component.getContext()
				.createChildContext());

		// Attach the application to the component and start it
		component.getDefaultHost().attach(application);
		component.start();
	}

}
