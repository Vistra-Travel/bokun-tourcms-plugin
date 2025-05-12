package io.bokun.inventory.plugin.tourcms;

import com.google.inject.*;
import com.google.inject.name.*;
import io.bokun.inventory.plugin.tourcms.service.RestService;
import io.bokun.inventory.plugin.tourcms.util.AppLogger;
import io.undertow.*;
import io.undertow.server.*;
import io.undertow.server.handlers.*;
import io.undertow.util.Headers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static com.google.inject.Scopes.*;

public class Main {

    private static final String TAG = Main.class.getSimpleName();

    private static final String ENVIRONMENT_PREFIX = "TOURCMS_";
    private static final String DEFAULT_HOST = "0.0.0.0";
    private static final int DEFAULT_PORT = 9090;

    private final int port;
    private final RestService restService;

    @Inject
    public Main(@Named(ENVIRONMENT_PREFIX + "PLUGIN_PORT") Integer port, RestService restService) {
        this.port = (port != null) ? port : DEFAULT_PORT;
        this.restService = restService;
    }

    public static void main(String[] args) {
        AppLogger.info(TAG, "Starting server...");

        String portValue = System.getenv("TOURCMS_PLUGIN_PORT") != null ? System.getenv("TOURCMS_PLUGIN_PORT") : String.valueOf(DEFAULT_PORT);
        int port = Integer.parseInt(portValue);

        AppLogger.info(TAG, String.format(" - Host: %s", DEFAULT_HOST));
        AppLogger.info(TAG, String.format(" - Port: %s", port));

        Injector injector = Guice.createInjector(new GuiceInitializer(port));
        Main server = injector.getInstance(Main.class);

        Undertow.builder()
                .addHttpListener(server.port, DEFAULT_HOST)
                .setHandler(
                        new RoutingHandler()
                                .get("/", exchange -> {
                                    try {
                                        String htmlContent = new String(Files.readAllBytes(Paths.get("index.html")));
                                        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/html");
                                        exchange.getResponseSender().send(htmlContent);
                                    } catch (IOException e) {
                                        exchange.setStatusCode(500);
                                        exchange.getResponseSender().send("Error loading HTML file");
                                    }
                                })
                                .get("/plugin/definition", server.restService::getDefinition)
                                .post("/product/search", new BlockingHandler(server.restService::searchProducts))
                                .post("/product/getById", new BlockingHandler(server.restService::getProductById))
                                .post("/product/getAvailable", new BlockingHandler(server.restService::getAvailableProducts))
                                .post("/product/getAvailability", new BlockingHandler(server.restService::getProductAvailability))
                                .post("/booking/reserve", new BlockingHandler(server.restService::createReservation))
                                .post("/booking/cancelReserve", new BlockingHandler(server.restService::cancelReservation))
                                .post("/booking/confirm", new BlockingHandler(server.restService::confirmBooking))
                                .post("/booking/createAndConfirm", new BlockingHandler(server.restService::createAndConfirmBooking))
                                .post("/booking/cancel", new BlockingHandler(server.restService::cancelBooking))
                                .post("/booking/amend", new BlockingHandler(server.restService::amendBooking))
                )
                .build()
                .start();

        AppLogger.info(TAG, String.format("Started REST service on port %s", server.port));
    }

    private static class GuiceInitializer extends AbstractModule {

        private final int port;

        public GuiceInitializer(int port) {
            this.port = port;
        }

        @Override
        protected void configure() {
            Binder binder = binder();
            binder.bind(Integer.class)
                    .annotatedWith(Names.named("TOURCMS_PLUGIN_PORT"))
                    .toInstance(port);

            binder.bind(RestService.class).in(SINGLETON);
            binder.bind(Main.class).in(SINGLETON);
        }
    }
}
