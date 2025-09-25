package io.gravitee.am.webauthn4j.poc;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simplified service for managing WebAuthn Metadata Service (MDS) integration
 * Provides basic MDS functionality for testing purposes
 */
public class MDSService {
    private static final Logger logger = LoggerFactory.getLogger(MDSService.class);

    // FIDO Alliance MDS endpoint (v3)
    private static final String FIDO_MDS_V3_URL = "https://mds.fidoalliance.org/";
    
    private final Vertx vertx;
    private final Object trustAnchorRepository;
    private final Map<String, JsonObject> cachedMetadata;
    
    private boolean enabled = true;
    private String mdsUrl = FIDO_MDS_V3_URL;
    private long cacheTimeoutMs = 24 * 60 * 60 * 1000; // 24 hours
    private long lastCacheUpdate = 0;
    private String mdsBearerToken = null; // Optional bearer token for MDS v3
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public MDSService(Vertx vertx, Object trustAnchorRepository) {
        this.vertx = vertx;
        this.trustAnchorRepository = trustAnchorRepository;
        this.cachedMetadata = new ConcurrentHashMap<>();
    }

    // Removed old verifyAuthenticator and mock metadata helpers

    

    /**
     * Create verification result JSON
     */
    private JsonObject createVerificationResult(JsonObject statement, boolean verified, String reason) {
        JsonObject result = new JsonObject()
            .put("verified", verified)
            .put("reason", reason)
            .put("timestamp", System.currentTimeMillis());
            
        if (statement != null) {
            result.put("aaguid", statement.getString("aaguid"))
                .put("description", statement.getString("description"))
                .put("authenticatorVersion", statement.getInteger("authenticatorVersion"))
                .put("protocolFamily", statement.getString("protocolFamily"))
                .put("schema", statement.getInteger("schema"))
                .put("upv", statement.getJsonArray("upv"))
                .put("authenticationAlgorithms", statement.getJsonArray("authenticationAlgorithms"))
                .put("publicKeyAlgAndEncodings", statement.getJsonArray("publicKeyAlgAndEncodings"))
                .put("attestationTypes", statement.getJsonArray("attestationTypes"))
                .put("userVerificationDetails", statement.getJsonArray("userVerificationDetails"))
                .put("keyProtection", statement.getJsonArray("keyProtection"))
                .put("matcherProtection", statement.getJsonArray("matcherProtection"))
                .put("cryptoStrength", statement.getInteger("cryptoStrength"))
                .put("operatingEnv", statement.getString("operatingEnv"))
                .put("attachmentHint", statement.getJsonArray("attachmentHint"))
                .put("isSecondFactorOnly", statement.getBoolean("isSecondFactorOnly"))
                .put("tcDisplay", statement.getInteger("tcDisplay"))
                .put("tcDisplayContentType", statement.getString("tcDisplayContentType"))
                .put("tcDisplayPNGCharacteristics", statement.getJsonObject("tcDisplayPNGCharacteristics"))
                .put("attestationRootCertificates", statement.getJsonArray("attestationRootCertificates"))
                .put("ecdaaTrustAnchors", statement.getJsonArray("ecdaaTrustAnchors"))
                .put("icon", statement.getString("icon"))
                .put("supportedExtensions", statement.getJsonArray("supportedExtensions"));
        }
        
        return result;
    }

    /**
     * Refresh MDS cache by downloading and parsing the MDS v3 TOC JWT.
    * This PoC does not verify the JWT signature. It extracts the payload and
    * loads metadata statements into a local cache keyed by AAGUID.
     */
    public void refreshCache() {
        if (!enabled) {
            logger.debug("MDS disabled, skipping cache refresh");
            return;
        }

        try {
            logger.info("Refreshing MDS cache from {}...", mdsUrl);
            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                .uri(URI.create(mdsUrl))
                .GET();
            if (mdsBearerToken != null && !mdsBearerToken.isBlank()) {
                reqBuilder.header("Authorization", "Bearer " + mdsBearerToken);
            }
            HttpResponse<String> resp = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                throw new IllegalStateException("MDS fetch failed with status " + resp.statusCode());
            }

            String tocJwt = resp.body();
            JsonObject payload = parseJwtPayload(tocJwt);
            if (payload == null) {
                throw new IllegalStateException("Failed to parse MDS TOC payload");
            }

            // entries: array of objects containing at least aaguid and url/hash
            var entries = payload.getJsonArray("entries");
            if (entries == null) {
                throw new IllegalStateException("MDS TOC missing entries");
            }

            Map<String, JsonObject> newCache = new ConcurrentHashMap<>();
            for (int i = 0; i < entries.size(); i++) {
                JsonObject entry = entries.getJsonObject(i);
                String aaguid = entry.getString("aaguid");
                if (aaguid == null) {
                    continue;
                }

                JsonObject statement = entry.getJsonObject("metadataStatement");
                if (statement == null) {
                    String url = entry.getString("url");
                    if (url != null && !url.isBlank()) {
                        try {
                            HttpRequest.Builder entryReq = HttpRequest.newBuilder().uri(URI.create(url)).GET();
                            if (mdsBearerToken != null && !mdsBearerToken.isBlank()) {
                                entryReq.header("Authorization", "Bearer " + mdsBearerToken);
                            }
                            HttpResponse<String> entryResp = httpClient.send(entryReq.build(), HttpResponse.BodyHandlers.ofString());
                            if (entryResp.statusCode() == 200) {
                                statement = new JsonObject(entryResp.body());
                            }
                        } catch (Exception ex) {
                            logger.warn("Failed to fetch metadata statement for {}: {}", aaguid, ex.getMessage());
                        }
                    }
                }

                if (statement == null) {
                    // fallback minimal statement
                    statement = new JsonObject()
                        .put("aaguid", aaguid)
                        .put("description", entry.getString("description", "Unknown Authenticator"));
                }

                // ensure aaguid present and cache
                if (!statement.containsKey("aaguid")) {
                    statement.put("aaguid", aaguid);
                }
                newCache.put(aaguid, statement);
            }

            cachedMetadata.clear();
            cachedMetadata.putAll(newCache);
            lastCacheUpdate = System.currentTimeMillis();
            logger.info("MDS cache loaded: {} entries", cachedMetadata.size());
            
        } catch (Exception e) {
            logger.error("Failed to refresh MDS cache", e);
        }
    }

    /**
     * Check if cache needs refresh
     */
    public boolean isCacheStale() {
        return System.currentTimeMillis() - lastCacheUpdate > cacheTimeoutMs;
    }

    /**
     * Get MDS configuration
     */
    public JsonObject getConfiguration() {
        return new JsonObject()
            .put("enabled", enabled)
            .put("mdsUrl", mdsUrl)
            .put("cacheTimeoutMs", cacheTimeoutMs)
            .put("lastCacheUpdate", lastCacheUpdate)
            .put("cachedEntries", cachedMetadata.size())
            .put("trustAnchorRepository", trustAnchorRepository.toString())
            .put("hasBearerToken", mdsBearerToken != null && !mdsBearerToken.isBlank());
    }

    /**
     * Update MDS configuration
     */
    public void updateConfiguration(JsonObject config) {
        if (config.containsKey("enabled")) {
            this.enabled = config.getBoolean("enabled");
        }
        if (config.containsKey("mdsUrl")) {
            this.mdsUrl = config.getString("mdsUrl");
        }
        if (config.containsKey("cacheTimeoutMs")) {
            this.cacheTimeoutMs = config.getLong("cacheTimeoutMs");
        }
        if (config.containsKey("mdsBearerToken")) {
            this.mdsBearerToken = config.getString("mdsBearerToken");
        }
        
        logger.info("MDS configuration updated: enabled={}, url={}", enabled, mdsUrl);
    }

    /**
     * Get cached metadata statistics
     */
    public JsonObject getCacheStatistics() {
        return new JsonObject()
            .put("cachedEntries", cachedMetadata.size())
            .put("lastUpdate", lastCacheUpdate)
            .put("isStale", isCacheStale())
            .put("timeoutMs", cacheTimeoutMs);
    }

    /**
     * Clear MDS cache
     */
    public void clearCache() {
        cachedMetadata.clear();
        lastCacheUpdate = 0;
        logger.info("MDS cache cleared");
    }

    /**
     * Return attestationRootCertificates (base64 DER) for the given AAGUID.
     * If cache is empty or stale, triggers a refresh.
     */
    public List<String> getAttestationRootCertificates(String aaguid) {
        try {
            if (aaguid == null || aaguid.isBlank()) {
                return Collections.emptyList();
            }
            if (isCacheStale() || cachedMetadata.isEmpty()) {
                refreshCache();
            }
            JsonObject statement = cachedMetadata.get(aaguid);
            if (statement == null) {
                return Collections.emptyList();
            }
            var arr = statement.getJsonArray("attestationRootCertificates");
            if (arr == null || arr.isEmpty()) {
                return Collections.emptyList();
            }
            List<String> roots = new ArrayList<>();
            for (int i = 0; i < arr.size(); i++) {
                roots.add(arr.getString(i));
            }
            return roots;
        } catch (Exception e) {
            logger.error("Failed to read attestationRootCertificates for {}", aaguid, e);
            return Collections.emptyList();
        }
    }

    private JsonObject parseJwtPayload(String jwt) {
        try {
            String[] parts = jwt.split("\\.");
            if (parts.length < 2) return null;
            String payloadB64 = parts[1]
                .replace('-', '+')
                .replace('_', '/');
            int pad = (4 - (payloadB64.length() % 4)) % 4;
            payloadB64 = payloadB64 + "=".repeat(pad);
            byte[] decoded = Base64.getDecoder().decode(payloadB64);
            return new JsonObject(new String(decoded));
        } catch (Exception e) {
            logger.error("Failed to parse JWT payload", e);
            return null;
        }
    }

    // Getters
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getMdsUrl() {
        return mdsUrl;
    }

    public void setMdsUrl(String mdsUrl) {
        this.mdsUrl = mdsUrl;
    }
}