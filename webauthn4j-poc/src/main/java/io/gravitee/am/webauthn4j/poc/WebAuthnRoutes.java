package io.gravitee.am.webauthn4j.poc;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * WebAuthn routes for registration and authentication
 */
public class WebAuthnRoutes {
    private static final Logger logger = LoggerFactory.getLogger(WebAuthnRoutes.class);
    
    private final WebAuthnService webAuthnService;

    public WebAuthnRoutes(WebAuthnService webAuthnService) {
        this.webAuthnService = webAuthnService;
    }

    public void registerRoutes(Router router) {
        // Registration endpoints
        router.post("/webauthn/register/begin").handler(this::beginRegistration);
        router.post("/webauthn/register/complete").handler(this::completeRegistration);
        
        // Authentication endpoints
        router.post("/webauthn/authenticate/begin").handler(this::beginAuthentication);
        router.post("/webauthn/authenticate/complete").handler(this::completeAuthentication);
        
        // Configuration endpoints
        router.get("/webauthn/config").handler(this::getConfiguration);
        router.post("/webauthn/config").handler(this::updateConfiguration);
        
        // Certificate management endpoints
        router.get("/webauthn/certificates").handler(this::getCertificates);
        router.post("/webauthn/certificates").handler(this::addCertificate);
        router.delete("/webauthn/certificates/:id").handler(this::removeCertificate);
        router.get("/webauthn/certificates/rotation").handler(this::getCertificateRotationStatus);
        
        // MDS management endpoints
        router.get("/webauthn/mds/config").handler(this::getMdsConfiguration);
        router.post("/webauthn/mds/config").handler(this::updateMdsConfiguration);
        router.get("/webauthn/mds/cache").handler(this::getMdsCache);
        router.post("/webauthn/mds/cache/refresh").handler(this::refreshMdsCache);
        router.delete("/webauthn/mds/cache").handler(this::clearMdsCache);
        
        // MDS-only mode endpoint
        router.get("/webauthn/mds-only").handler(this::getMdsOnlyMode);
        
        
        // Debug endpoints
        router.get("/webauthn/debug/trust-anchors").handler(this::getTrustAnchors);
        router.get("/webauthn/debug/credentials").handler(this::getCredentials);
        router.get("/webauthn/debug/statistics").handler(this::getStatistics);
    }

    private void beginRegistration(RoutingContext ctx) {
        try {
            JsonObject body = ctx.body().asJsonObject();
            String username = body.getString("username");
            
            if (username == null || username.trim().isEmpty()) {
                ctx.response()
                    .setStatusCode(400)
                    .putHeader("Content-Type", "application/json")
                    .end("{\"error\":\"Username is required\"}");
                return;
            }

            JsonObject options = webAuthnService.createRegistrationOptions(username);
            
            ctx.response()
                .putHeader("Content-Type", "application/json")
                .end(options.encode());
                
            logger.info("Registration options created for user: {}", username);
            
        } catch (Exception e) {
            logger.error("Error creating registration options", e);
            ctx.response()
                .setStatusCode(500)
                .putHeader("Content-Type", "application/json")
                .end("{\"error\":\"Internal server error\"}");
        }
    }

    private void completeRegistration(RoutingContext ctx) {
        try {
            JsonObject body = ctx.body().asJsonObject();
            String username = body.getString("username");
            
            if (username == null || username.trim().isEmpty()) {
                ctx.response()
                    .setStatusCode(400)
                    .putHeader("Content-Type", "application/json")
                    .end("{\"error\":\"Username is required\"}");
                return;
            }

            // Parse the credential from the client (simplified)
            Object credential = body.getJsonObject("credential");
            
            webAuthnService.validateRegistration(credential, username);
            
            ctx.response()
                .putHeader("Content-Type", "application/json")
                .end("{\"status\":\"success\",\"message\":\"Registration completed successfully\"}");
                
            logger.info("Registration completed successfully for user: {}", username);
            
        } catch (Exception e) {
            logger.error("Error completing registration", e);
            ctx.response()
                .setStatusCode(400)
                .putHeader("Content-Type", "application/json")
                .end("{\"error\":\"Registration failed: " + e.getMessage() + "\"}");
        }
    }

    private void beginAuthentication(RoutingContext ctx) {
        try {
            JsonObject body = ctx.body().asJsonObject();
            String username = body.getString("username");
            
            if (username == null || username.trim().isEmpty()) {
                ctx.response()
                    .setStatusCode(400)
                    .putHeader("Content-Type", "application/json")
                    .end("{\"error\":\"Username is required\"}");
                return;
            }

            JsonObject options = webAuthnService.createAuthenticationOptions(username);
            
            ctx.response()
                .putHeader("Content-Type", "application/json")
                .end(options.encode());
                
            logger.info("Authentication options created for user: {}", username);
            
        } catch (Exception e) {
            logger.error("Error creating authentication options", e);
            ctx.response()
                .setStatusCode(500)
                .putHeader("Content-Type", "application/json")
                .end("{\"error\":\"Internal server error\"}");
        }
    }

    private void completeAuthentication(RoutingContext ctx) {
        try {
            JsonObject body = ctx.body().asJsonObject();
            String username = body.getString("username");
            
            if (username == null || username.trim().isEmpty()) {
                ctx.response()
                    .setStatusCode(400)
                    .putHeader("Content-Type", "application/json")
                    .end("{\"error\":\"Username is required\"}");
                return;
            }

            // Parse the credential from the client (simplified)
            Object credential = body.getJsonObject("credential");
            
            webAuthnService.validateAuthentication(credential, username);
            
            ctx.response()
                .putHeader("Content-Type", "application/json")
                .end("{\"status\":\"success\",\"message\":\"Authentication completed successfully\"}");
                
            logger.info("Authentication completed successfully for user: {}", username);
            
        } catch (Exception e) {
            logger.error("Error completing authentication", e);
            ctx.response()
                .setStatusCode(400)
                .putHeader("Content-Type", "application/json")
                .end("{\"error\":\"Authentication failed: " + e.getMessage() + "\"}");
        }
    }

    private void getConfiguration(RoutingContext ctx) {
        JsonObject config = new JsonObject()
            .put("useMdsOnly", webAuthnService.isUseMdsOnly())
            .put("mode", webAuthnService.isUseMdsOnly() ? "MDS-only" : "Hardcoded certificates")
            .put("certificateCount", webAuthnService.getCertificateService().getCertificateCount())
            .put("relyingPartyId", webAuthnService.getRelyingPartyId())
            .put("relyingPartyName", webAuthnService.getRelyingPartyName());
            
        ctx.response()
            .putHeader("Content-Type", "application/json")
            .end(config.encode());
    }

    private void updateConfiguration(RoutingContext ctx) {
        try {
            JsonObject body = ctx.body().asJsonObject();
            JsonObject updated = webAuthnService.updateConfiguration(body);
            ctx.response()
                .putHeader("Content-Type", "application/json")
                .end(updated.encode());
        } catch (Exception e) {
            logger.error("Error updating configuration", e);
            ctx.response()
                .setStatusCode(400)
                .putHeader("Content-Type", "application/json")
                .end("{\"error\":\"Failed to update configuration: " + e.getMessage() + "\"}");
        }
    }

    private void getTrustAnchors(RoutingContext ctx) {
        // This would return information about loaded trust anchors
        JsonObject response = new JsonObject()
            .put("count", 0)
            .put("message", "Trust anchors not available in simplified implementation");
            
        ctx.response()
            .putHeader("Content-Type", "application/json")
            .end(response.encode());
    }

    private void getCredentials(RoutingContext ctx) {
        try {
            Map<String, JsonObject> credentials = webAuthnService.getCredentials();
            JsonObject response = new JsonObject()
                .put("count", credentials.size())
                .put("credentials", credentials);
                
            ctx.response()
                .putHeader("Content-Type", "application/json")
                .end(response.encode());
        } catch (Exception e) {
            logger.error("Error getting credentials", e);
            ctx.response()
                .setStatusCode(500)
                .putHeader("Content-Type", "application/json")
                .end("{\"error\":\"Failed to get credentials: " + e.getMessage() + "\"}");
        }
    }

    private void getCertificates(RoutingContext ctx) {
        try {
            Map<String, Map<String, Object>> certInfo = webAuthnService.getCertificateService().getCertificateInfo();
            JsonObject response = new JsonObject()
                .put("count", certInfo.size())
                .put("certificates", certInfo);
                
            ctx.response()
                .putHeader("Content-Type", "application/json")
                .end(response.encode());
        } catch (Exception e) {
            logger.error("Error getting certificates", e);
            ctx.response()
                .setStatusCode(500)
                .putHeader("Content-Type", "application/json")
                .end("{\"error\":\"Failed to get certificates: " + e.getMessage() + "\"}");
        }
    }

    private void addCertificate(RoutingContext ctx) {
        try {
            JsonObject body = ctx.body().asJsonObject();
            String id = body.getString("id");
            String base64Certificate = body.getString("certificate");
            String description = body.getString("description", "Custom certificate");
            
            if (id == null || base64Certificate == null) {
                ctx.response()
                    .setStatusCode(400)
                    .putHeader("Content-Type", "application/json")
                    .end("{\"error\":\"ID and certificate are required\"}");
                return;
            }
            
            webAuthnService.getCertificateService().loadCertificate(id, base64Certificate, description);
            
            ctx.response()
                .putHeader("Content-Type", "application/json")
                .end("{\"status\":\"success\",\"message\":\"Certificate added successfully\"}");
                
        } catch (Exception e) {
            logger.error("Error adding certificate", e);
            ctx.response()
                .setStatusCode(400)
                .putHeader("Content-Type", "application/json")
                .end("{\"error\":\"Failed to add certificate: " + e.getMessage() + "\"}");
        }
    }

    private void removeCertificate(RoutingContext ctx) {
        try {
            String id = ctx.pathParam("id");
            if (id == null) {
                ctx.response()
                    .setStatusCode(400)
                    .putHeader("Content-Type", "application/json")
                    .end("{\"error\":\"Certificate ID is required\"}");
                return;
            }
            
            boolean removed = webAuthnService.getCertificateService().removeCertificate(id);
            
            if (removed) {
                ctx.response()
                    .putHeader("Content-Type", "application/json")
                    .end("{\"status\":\"success\",\"message\":\"Certificate removed successfully\"}");
            } else {
                ctx.response()
                    .setStatusCode(404)
                    .putHeader("Content-Type", "application/json")
                    .end("{\"error\":\"Certificate not found\"}");
            }
                
        } catch (Exception e) {
            logger.error("Error removing certificate", e);
            ctx.response()
                .setStatusCode(500)
                .putHeader("Content-Type", "application/json")
                .end("{\"error\":\"Failed to remove certificate: " + e.getMessage() + "\"}");
        }
    }

    private void getCertificateRotationStatus(RoutingContext ctx) {
        try {
            CertificateService certService = webAuthnService.getCertificateService();
            JsonObject response = new JsonObject()
                .put("supported", certService.isCertificateRotationSupported())
                .put("certificateCount", certService.getCertificateCount())
                .put("enabled", true);
                
            ctx.response()
                .putHeader("Content-Type", "application/json")
                .end(response.encode());
        } catch (Exception e) {
            logger.error("Error getting certificate rotation status", e);
            ctx.response()
                .setStatusCode(500)
                .putHeader("Content-Type", "application/json")
                .end("{\"error\":\"Failed to get certificate rotation status: " + e.getMessage() + "\"}");
        }
    }

    private void getMdsConfiguration(RoutingContext ctx) {
        try {
            JsonObject config = webAuthnService.getMdsService().getConfiguration();
            ctx.response()
                .putHeader("Content-Type", "application/json")
                .end(config.encode());
        } catch (Exception e) {
            logger.error("Error getting MDS configuration", e);
            ctx.response()
                .setStatusCode(500)
                .putHeader("Content-Type", "application/json")
                .end("{\"error\":\"Failed to get MDS configuration: " + e.getMessage() + "\"}");
        }
    }

    private void updateMdsConfiguration(RoutingContext ctx) {
        try {
            JsonObject body = ctx.body().asJsonObject();
            webAuthnService.getMdsService().updateConfiguration(body);
            
            ctx.response()
                .putHeader("Content-Type", "application/json")
                .end("{\"status\":\"success\",\"message\":\"MDS configuration updated\"}");
                
        } catch (Exception e) {
            logger.error("Error updating MDS configuration", e);
            ctx.response()
                .setStatusCode(400)
                .putHeader("Content-Type", "application/json")
                .end("{\"error\":\"Failed to update MDS configuration: " + e.getMessage() + "\"}");
        }
    }

    private void getMdsCache(RoutingContext ctx) {
        try {
            JsonObject cacheInfo = webAuthnService.getMdsService().getCacheStatistics();
            ctx.response()
                .putHeader("Content-Type", "application/json")
                .end(cacheInfo.encode());
        } catch (Exception e) {
            logger.error("Error getting MDS cache info", e);
            ctx.response()
                .setStatusCode(500)
                .putHeader("Content-Type", "application/json")
                .end("{\"error\":\"Failed to get MDS cache info: " + e.getMessage() + "\"}");
        }
    }

    private void refreshMdsCache(RoutingContext ctx) {
        try {
            webAuthnService.getMdsService().refreshCache();
            
            ctx.response()
                .putHeader("Content-Type", "application/json")
                .end("{\"status\":\"success\",\"message\":\"MDS cache refreshed\"}");
                
        } catch (Exception e) {
            logger.error("Error refreshing MDS cache", e);
            ctx.response()
                .setStatusCode(500)
                .putHeader("Content-Type", "application/json")
                .end("{\"error\":\"Failed to refresh MDS cache: " + e.getMessage() + "\"}");
        }
    }

    private void clearMdsCache(RoutingContext ctx) {
        try {
            webAuthnService.getMdsService().clearCache();
            
            ctx.response()
                .putHeader("Content-Type", "application/json")
                .end("{\"status\":\"success\",\"message\":\"MDS cache cleared\"}");
                
        } catch (Exception e) {
            logger.error("Error clearing MDS cache", e);
            ctx.response()
                .setStatusCode(500)
                .putHeader("Content-Type", "application/json")
                .end("{\"error\":\"Failed to clear MDS cache: " + e.getMessage() + "\"}");
        }
    }

    private void getStatistics(RoutingContext ctx) {
        try {
            JsonObject stats = new JsonObject()
                .put("users", webAuthnService.getCredentials().size())
                .put("certificates", webAuthnService.getCertificateService().getCertificateCount())
                .put("certificateRotationSupported", webAuthnService.getCertificateService().isCertificateRotationSupported())
                .put("mdsEnabled", webAuthnService.getMdsService().isEnabled())
                .put("mdsOnlyMode", webAuthnService.isUseMdsOnly())
                .put("mdsCache", webAuthnService.getMdsService().getCacheStatistics());
                
            ctx.response()
                .putHeader("Content-Type", "application/json")
                .end(stats.encode());
        } catch (Exception e) {
            logger.error("Error getting statistics", e);
            ctx.response()
                .setStatusCode(500)
                .putHeader("Content-Type", "application/json")
                .end("{\"error\":\"Failed to get statistics: " + e.getMessage() + "\"}");
        }
    }

    private void getMdsOnlyMode(RoutingContext ctx) {
        try {
            JsonObject response = new JsonObject()
                .put("mdsOnlyMode", webAuthnService.isUseMdsOnly())
                .put("description", "MDS-only mode uses MDS for all validation instead of hardcoded certificates")
                .put("migrationMode", "This allows testing migration from hardcoded certificates to MDS-only validation");

            ctx.response()
                .putHeader("Content-Type", "application/json")
                .end(response.encode());
        } catch (Exception e) {
            logger.error("Failed to get MDS-only mode status", e);
            ctx.response()
                .setStatusCode(500)
                .putHeader("Content-Type", "application/json")
                .end("{\"error\":\"Failed to get MDS-only mode status: " + e.getMessage() + "\"}");
        }
    }
}
