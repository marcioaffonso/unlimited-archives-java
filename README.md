# OpenTok Unlimited Archives

This is a simple demo app that shows how you can use the OpenTok Java SDK to create unlimited archives using (once archives have a limit of 90 min) using a Callback URL.

## Running the App

First, add your own API Key and API Secret to the system properties. For your convenience, the
`sample/HelloWorld/build.gradle` file is set up for you to place your values into it.

```
run.systemProperty 'API_KEY', '000000'
run.systemProperty 'API_SECRET', 'abcdef1234567890abcdef01234567890abcdef'
```

Next, start the server using Gradle (which handles dependencies and setting up the environment).

```
$ gradle :sample/HelloWorld:run
```

Or if you are using the Gradle Wrapper that is distributed with the project, from the root project
directory:

```
$ ./gradlew :sample/HelloWorld:run
```

Visit <http://localhost:4567> in your browser. Open it again in a second window. Smile! You've just
set up a group chat with automatic archive

## Configuring the Callback URL

You must register a callback URL for notifications when an archive's status changes. Use the OpenTok
dashboard to specify a callback URL. When an archive's status changes, the server sends HTTP POST
requests to the URL you supply. The Content-Type for the request is application/json.

Set your Callback URL to <http://yourapp.com/archiveChangeHandler>

## Walkthrough

This demo application uses the exactly same architecture of the [Opentok Java SDK Samples](https://github.com/opentok/Opentok-Java-SDK). It only aggregates a callback method to the HelloWorldServer.java to reinitilialize
stopped archives. It also sets the session to start archives automatically.

### Main Application (sample/HelloWorld/src/main/java/com/example/HelloWorldServer.java)


The app calls the `OpenTok` instance's `createSession()` method, setting the ArchiveMode
property to ALWAYS in order to start archives automatically and pulls out the
`String sessionId` using the `getSessionId()` method on the resulting `Session` instance. This is
stored in another class variable. Alternatively, `sessionId`s are commonly stored in databses for
applications that have many of them.

```java
public class HelloWorldServer {

  // ...
  private static String sessionId;

  public static void main(String[] args) throws OpenTokException {
    // ...

    sessionId = opentok.createSession(
      new SessionProperties.Builder()
        .mediaMode(MediaMode.ROUTED)
        .archiveMode(ArchiveMode.ALWAYS)
        .build()
    ).getSessionId();
  }
}
```

A root method is used to serve a page with apiKey, sessionId and token. To do so, we put together a
Map of values that our template system (freemarker) will use to render an HTML page. This is done by
returning an instance of `ModelAndView` that groups this map with the name of a view.

```java
    get(new FreeMarkerTemplateView("/") {
      @Override
      public ModelAndView handle(Request request, Response response) {
        // ...

        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put("apiKey", apiKey);
        attributes.put("sessionId", sessionId);
        attributes.put("token", token);

        return new ModelAndView(attributes, "index.ftl");
      }
    });
```

### Callback Method

In order to reinitiliaze archives automatically, we need to create a callback method, which will
be invocated every time there is an update on the archive status. When an archive's status changes,
the server sends HTTP POST requests to the URL you supply. In this case, to the method below.

The method below verify if the status of the archive is "stopped" and if the reason for it was stopped
was due to the 90 min limit. If so, the startArchive method is invocated to reinitiliaze the archive.

```java
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
```

### Main Template (sample/HelloWorld/src/main/resources/com/example/freemarker/index.ftl)

This file simply sets up the HTML page for the JavaScript application to run, imports the OpenTok.js
JavaScript library, and passes the values created by the server into the JavaScript application
inside `public/js/helloworld.js`
