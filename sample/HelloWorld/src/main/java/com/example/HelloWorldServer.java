package com.example;

import static spark.Spark.*;
import spark.*;
import org.codehaus.jackson.map.*;

import java.util.Map;
import java.util.HashMap;
import java.io.IOException;

import com.opentok.*;
import com.opentok.exception.OpenTokException;

public class HelloWorldServer {

    private static final String apiKey = System.getProperty("API_KEY");
    private static final String apiSecret = System.getProperty("API_SECRET");
    private static OpenTok opentok;
    private static String sessionId;

    public static void main(String[] args) throws OpenTokException {

        if (apiKey == null || apiKey.isEmpty() || apiSecret == null || apiSecret.isEmpty()) {
            System.out.println("You must define API_KEY and API_SECRET system properties in the build.gradle file.");
            System.exit(-1);
        }

        opentok = new OpenTok(Integer.parseInt(apiKey), apiSecret);

        sessionId = opentok.createSession(
            new SessionProperties.Builder()
              .mediaMode(MediaMode.ROUTED)
              .archiveMode(ArchiveMode.ALWAYS)
              .build()
        ).getSessionId();

        externalStaticFileLocation("./public");

        //Default HelloWorld Method
        get(new FreeMarkerTemplateView("/") {
            @Override
            public ModelAndView handle(Request request, Response response) {

                System.out.println("Request recieved");

                String token = null;
                try {
                    token = opentok.generateToken(sessionId);
                } catch (OpenTokException e) {
                    e.printStackTrace();
                }

                Map<String, Object> attributes = new HashMap<String, Object>();
                attributes.put("apiKey", apiKey);
                attributes.put("sessionId", sessionId);
                attributes.put("token", token);

                return new ModelAndView(attributes, "index.ftl");
            }
        });

        // Callback method to reinitialize stopped archives due to 90 min limit
        post(new Route("/archiveChangeHandler") {
            @Override
            public Object handle(Request request, Response response) {
                response.header("Access-Control-Allow-Origin", "*");

                ObjectMapper mapper = new ObjectMapper();
                mapper.configure(DeserializationConfig.Feature.FAIL_ON_UNKNOWN_PROPERTIES, false);
                mapper.enable(DeserializationConfig.Feature.READ_ENUMS_USING_TO_STRING);

                Archive archive = null;
                try {
                    archive = mapper.readValue(request.body(), Archive.class);
                } catch (IOException e) {
                    e.printStackTrace();
                    return null;
                }

                // Checks if archive was stopped due to 90 min limit
                if(archive.getStatus().toString().equals("stopped") && archive.getReason().toString().equals("90 mins exceeded")) {
                    try {
                        archive = opentok.startArchive(archive.getSessionId());
                    } catch (OpenTokException e) {
                        e.printStackTrace();
                        return null;
                    }
                    return archive.toString();
                }
                response.status(200);
                return "No action required";
            }
        });
    }
}