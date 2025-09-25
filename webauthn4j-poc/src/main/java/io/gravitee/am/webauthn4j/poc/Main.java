package io.gravitee.am.webauthn4j.poc;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;
import io.vertx.ext.web.handler.SessionHandler;
import io.vertx.ext.web.sstore.LocalSessionStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main class for WebAuthn4J PoC application demonstrating certificate rotation
 * and MDS integration capabilities.
 */
public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        // Parse command line arguments - simplified
        boolean useMdsOnly = false; // Default to hardcoded certs
        int port = 8080;
        
        for (String arg : args) {
            switch (arg) {
                case "--mds-only":
                    useMdsOnly = true;
                    break;
                case "--help":
                    System.out.println("WebAuthn4J PoC - Certificate Rotation Demo");
                    System.out.println("Usage: java -jar webauthn4j-poc.jar [options]");
                    System.out.println("Options:");
                    System.out.println("  --mds-only          Use MDS for validation (default: hardcoded certs)");
                    System.out.println("  --port=PORT         Set server port (default: 8080)");
                    System.out.println("  --help              Show this help message");
                    System.exit(0);
                default:
                    if (arg.startsWith("--port=")) {
                        port = Integer.parseInt(arg.substring(7));
                    }
                    break;
            }
        }
        
        // Print configuration
        System.out.println("🚀 WebAuthn4J PoC - Certificate Rotation Demo");
        System.out.println("=============================================");
        System.out.println("Configuration:");
        System.out.println("  - Mode: " + (useMdsOnly ? "MDS-only" : "Hardcoded certificates"));
        System.out.println("  - Port: " + port);
        System.out.println();
        
        Vertx vertx = Vertx.vertx();
        
        // Create HTTP server
        HttpServer server = vertx.createHttpServer();
        
        // Create router
        Router router = Router.router(vertx);
        
        // Configure CORS
        router.route().handler(CorsHandler.create()
            .addOrigin("http://localhost:3000")
            .addOrigin("http://localhost:8080")
            .addOrigin("https://webauthn.io")
            .allowedMethods(java.util.Set.of(io.vertx.core.http.HttpMethod.GET, io.vertx.core.http.HttpMethod.POST, io.vertx.core.http.HttpMethod.PUT, io.vertx.core.http.HttpMethod.DELETE, io.vertx.core.http.HttpMethod.OPTIONS))
            .allowedHeaders(java.util.Set.of("Content-Type", "Authorization", "X-Requested-With"))
            .allowCredentials(true));
        
        // Add body handler
        router.route().handler(BodyHandler.create());
        
        // Add session handler
        router.route().handler(SessionHandler.create(LocalSessionStore.create(vertx)));
        
        // Initialize WebAuthn service with configuration
        WebAuthnService webAuthnService = new WebAuthnService(vertx, useMdsOnly);
        webAuthnService.initialize();
        
        // Register routes
        WebAuthnRoutes webAuthnRoutes = new WebAuthnRoutes(webAuthnService);
        webAuthnRoutes.registerRoutes(router);
        
        // Health check endpoint
        router.get("/health").handler(ctx -> {
            ctx.response()
                .putHeader("Content-Type", "application/json")
                .end("{\"status\":\"UP\",\"service\":\"webauthn4j-poc\"}");
        });
        
        // Start server
        final int finalPort = port;
        server.requestHandler(router).listen(finalPort)
            .onSuccess(s -> {
                logger.info("WebAuthn4J PoC server started on port {}", finalPort);
                logger.info("Health check: http://localhost:{}/health", finalPort);
                logger.info("WebAuthn endpoints available at /webauthn/*");
            })
            .onFailure(throwable -> {
                logger.error("Failed to start server", throwable);
                System.exit(1);
            });
    }
}
