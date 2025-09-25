package io.gravitee.am.webauthn4j.poc;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.webauthn4j.WebAuthn4J;
import io.vertx.ext.auth.webauthn4j.RelyingParty;
import io.vertx.ext.auth.webauthn4j.WebAuthn4JOptions;
import io.vertx.ext.auth.webauthn4j.WebAuthn4JCredentials;
import io.vertx.ext.web.handler.SessionHandler;
import io.vertx.ext.web.sstore.LocalSessionStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;

import java.security.cert.X509Certificate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import com.webauthn4j.anchor.TrustAnchorRepository;

/**
 * Simplified WebAuthn service using Vert.x Auth WebAuthn4J with certificate rotation and MDS support
 */
public class WebAuthnService {
    private static final Logger logger = LoggerFactory.getLogger(WebAuthnService.class);

    private final Vertx vertx;
    private final CertificateService certificateService;
    private final MDSService mdsService;
    private WebAuthn4J webAuthn4j;
    
    // User and credential storage
    private final Map<String, String> users = new ConcurrentHashMap<>();
    private final Map<String, JsonObject> credentials = new ConcurrentHashMap<>();
    private final Map<String, String> challenges = new ConcurrentHashMap<>();
    private final Map<String, JsonObject> registrationOptions = new ConcurrentHashMap<>();
    private final Map<String, JsonObject> authenticationOptions = new ConcurrentHashMap<>();
    
    // Configuration flags
    private boolean useMdsOnly = false; // Use MDS instead of hardcoded certificates
    private String relyingPartyId = "localhost";
    private String relyingPartyName = "WebAuthn4J PoC";
    private String origin = "http://localhost:8080";
    private String domain = "localhost";

    public WebAuthnService(Vertx vertx, boolean useMdsOnly) {
        this.vertx = vertx;
        this.useMdsOnly = useMdsOnly;
        this.certificateService = new CertificateService();
        this.mdsService = new MDSService(vertx, certificateService.getTrustAnchorRepository());
        this.webAuthn4j = createWebAuthn4J();
    }

    public void initialize() {
        logger.info("Initializing WebAuthn4J service with Vert.x Auth WebAuthn4J...");
        logger.info("Mode: {}", useMdsOnly ? "MDS-only" : "Hardcoded certificates with dynamic trust anchor");
        logger.info("Loaded {} certificates for rotation support", certificateService.getCertificateCount());
        logger.info("MDS service enabled: {}", mdsService.isEnabled());
        
        if (useMdsOnly) {
            logger.info("Using MDS-only mode - all attestation validation will use Metadata Service");
        } else {
            logger.info("Using hardcoded certificates with dynamic TrustAnchorRepository");
        }
    }

    /**
     * Create WebAuthn4J instance with custom configuration
     */
    private WebAuthn4J createWebAuthn4J() {
        try {
            WebAuthn4JOptions options = new WebAuthn4JOptions()
                .setRelyingParty(new RelyingParty()
                    .setName(relyingPartyName)
                    .setId(relyingPartyId))
                .setAttestation(io.vertx.ext.auth.webauthn4j.Attestation.DIRECT)
                .setAuthenticatorAttachment(io.vertx.ext.auth.webauthn4j.AuthenticatorAttachment.PLATFORM)
                .setUserVerification(io.vertx.ext.auth.webauthn4j.UserVerification.PREFERRED)
                .setResidentKey(io.vertx.ext.auth.webauthn4j.ResidentKey.PREFERRED);

            // Configure certificate validation mode
            if (useMdsOnly) {
                // MDS-only mode
                options.setUseMetadata(true);
                logger.info("🔐 MDS-only mode enabled - using Metadata Service for validation");
            } else {
                // Hardcoded certificates mode with dynamic trust anchor
                logger.info("🔐 Hardcoded certificates mode enabled with dynamic trust anchor");
                logger.info("📋 Available certificates for rotation: {}", certificateService.getCertificateCount());
                
                // Create dynamic trust anchor repository
                TrustAnchorRepository trustAnchorRepo = certificateService.getTrustAnchorRepository();
                logger.info("✅ Dynamic TrustAnchorRepository created with {} certificates", 
                    certificateService.getCertificateCount());
                
                // Log certificate details for demonstration
                Map<String, Map<String, Object>> certInfo = certificateService.getCertificateInfo();
                for (Map.Entry<String, Map<String, Object>> entry : certInfo.entrySet()) {
                    logger.info("   📜 {}: {} (Valid: {} - {})", 
                        entry.getKey(),
                        entry.getValue().get("subject"),
                        entry.getValue().get("validFrom"),
                        entry.getValue().get("validTo"));
                }
                
                logger.info("🔄 Dynamic trust anchor allows runtime certificate updates");
                logger.info("   - New certificates can be added at runtime");
                logger.info("   - Expired certificates can be removed");
                logger.info("   - TrustAnchorRepository updates automatically");
            }

            return WebAuthn4J.create(vertx, options);
        } catch (Exception e) {
            logger.error("Failed to create WebAuthn4J instance", e);
            throw new RuntimeException("Failed to initialize WebAuthn4J", e);
        }
    }

    /**
     * Create session handler
     */
    public SessionHandler createSessionHandler() {
        return SessionHandler.create(LocalSessionStore.create(vertx));
    }

    // Getters for configuration
    public boolean isUseMdsOnly() {
        return useMdsOnly;
    }

    public String getRelyingPartyId() {
        return relyingPartyId;
    }

    public String getRelyingPartyName() {
        return relyingPartyName;
    }

    public CertificateService getCertificateService() {
        return certificateService;
    }

    /**
     * Update runtime configuration flags. Allows toggling MDS-only mode
     * without restarting the service.
     */
    public synchronized JsonObject updateConfiguration(JsonObject config) {
        boolean recreate = false;

        if (config.containsKey("useMdsOnly")) {
            boolean newValue = config.getBoolean("useMdsOnly");
            if (this.useMdsOnly != newValue) {
                this.useMdsOnly = newValue;
                recreate = true;
                logger.info("useMdsOnly set to {}", this.useMdsOnly);
            }
        }

        // Back-compat with scripts using useMdsForValidation
        if (config.containsKey("useMdsForValidation")) {
            boolean enabled = config.getBoolean("useMdsForValidation");
            this.mdsService.setEnabled(enabled);
            logger.info("MDS service enabled set to {} via useMdsForValidation", enabled);
        }

        if (config.containsKey("mdsEnabled")) {
            boolean enabled = config.getBoolean("mdsEnabled");
            this.mdsService.setEnabled(enabled);
            logger.info("MDS service enabled set to {}", enabled);
        }

        if (recreate) {
            this.webAuthn4j = createWebAuthn4J();
        }

        return new JsonObject()
            .put("useMdsOnly", this.useMdsOnly)
            .put("mdsEnabled", this.mdsService.isEnabled())
            .put("certificateCount", this.certificateService.getCertificateCount())
            .put("relyingPartyId", this.relyingPartyId)
            .put("relyingPartyName", this.relyingPartyName);
    }

    // WebAuthn operations
    public JsonObject createRegistrationOptions(String username) {
        try {
            // Generate a simple challenge
            String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
                UUID.randomUUID().toString().getBytes());
            challenges.put(username, challenge);
            users.put(username, username);

            // Create registration options manually
            JsonObject options = new JsonObject()
                .put("challenge", challenge)
                .put("rp", new JsonObject()
                    .put("name", relyingPartyName)
                    .put("id", relyingPartyId))
                .put("user", new JsonObject()
                    .put("id", Base64.getUrlEncoder().withoutPadding().encodeToString(username.getBytes()))
                    .put("name", username)
                    .put("displayName", username))
                .put("pubKeyCredParams", Arrays.asList(
                    new JsonObject().put("type", "public-key").put("alg", -7), // ES256
                    new JsonObject().put("type", "public-key").put("alg", -257) // RS256
                ))
                .put("timeout", 30000)
                .put("attestation", "direct")
                .put("authenticatorSelection", new JsonObject()
                    .put("authenticatorAttachment", "platform")
                    .put("userVerification", "preferred")
                    .put("residentKey", "preferred"));
            
            // Store options for validation
            registrationOptions.put(username, options);
            
            logger.info("Created registration options for user: {}", username);
            return options;
            
        } catch (Exception e) {
            logger.error("Failed to create registration options for user: {}", username, e);
            throw new RuntimeException("Failed to create registration options", e);
        }
    }

    public JsonObject createAuthenticationOptions(String username) {
        try {
            // Generate a simple challenge
            String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
                UUID.randomUUID().toString().getBytes());
            challenges.put(username, challenge);

            // Get user's credentials
            List<JsonObject> allowCredentials = new ArrayList<>();
            for (Map.Entry<String, JsonObject> entry : credentials.entrySet()) {
                JsonObject cred = entry.getValue();
                if (username.equals(cred.getString("username"))) {
                    allowCredentials.add(new JsonObject()
                        .put("type", "public-key")
                        .put("id", cred.getString("credentialId"))
                        .put("transports", Arrays.asList("internal", "usb", "nfc", "ble")));
                }
            }

            // Create authentication options manually
            JsonObject options = new JsonObject()
                .put("challenge", challenge)
                .put("timeout", 30000)
                .put("rpId", relyingPartyId)
                .put("allowCredentials", allowCredentials)
                .put("userVerification", "preferred");

            // Store options for validation
            authenticationOptions.put(username, options);
            
            logger.info("Created authentication options for user: {} with {} credentials", username, allowCredentials.size());
            return options;
            
        } catch (Exception e) {
            logger.error("Failed to create authentication options for user: {}", username, e);
            throw new RuntimeException("Failed to create authentication options", e);
        }
    }

    public void validateRegistration(Object credential, String username) {
        try {
            JsonObject credJson = (JsonObject) credential;
            
            if (!users.containsKey(username)) {
                throw new IllegalArgumentException("User not found: " + username);
            }

            logger.info("Starting registration validation for user: {}", username);

            // Pre-validate attestation chain anchoring against MDS or dynamic trust anchors
            preValidateAttestationAnchoring(credJson);

            // Extract challenge from client data JSON
            String challenge = extractChallengeFromClientData(credJson);
            if (challenge == null || challenge.isEmpty()) {
                throw new IllegalArgumentException("No challenge found in client data JSON");
            }

            // Create WebAuthn4JCredentials with the credential data from client
            WebAuthn4JCredentials credentials = new WebAuthn4JCredentials()
                .setUsername(username)
                .setChallenge(challenge)
                .setOrigin(origin)  // Required for security validation
                .setDomain(domain)  // Required for security validation
                .setWebauthn(credJson);

            // Use WebAuthn4J to validate registration
            webAuthn4j.authenticate(credentials)
                .onSuccess(user -> {
                    // Registration successful
                    String credentialId = credJson.getString("id");
                    
                    // Store credential data
                    JsonObject credentialData = new JsonObject()
                        .put("credentialId", credentialId)
                        .put("username", username)
                        .put("attestationObject", credJson.getJsonObject("response").getString("attestationObject"))
                        .put("clientDataJSON", credJson.getJsonObject("response").getString("clientDataJSON"))
                        .put("createdAt", System.currentTimeMillis());

                    this.credentials.put(credentialId, credentialData);

                    logger.info("Registration validated successfully for user: {} with credential: {}", username, credentialId);
                })
                .onFailure(throwable -> {
                    logger.error("Registration validation failed for user: {}", username, throwable);
                    throw new RuntimeException("Registration validation failed: " + throwable.getMessage(), throwable);
                });

            // Perform MDS verification if enabled
            if (useMdsOnly) {
                JsonObject response = credJson.getJsonObject("response");
                if (response != null && response.getString("attestationObject") != null) {
                    // Decode attestation object to get certificate chain
                    // This would require additional parsing of the attestation object
                    // For now, we'll log that MDS verification would be performed
                    logger.info("MDS verification would be performed for attestation object");
                }
            }
            
        } catch (Exception e) {
            logger.error("Registration validation failed for user: {}", username, e);
            throw new RuntimeException("Registration validation failed: " + e.getMessage(), e);
        }
    }

    public void validateAuthentication(Object credential, String username) {
        try {
            JsonObject credJson = (JsonObject) credential;
            
            logger.info("Starting authentication validation for user: {}", username);

            // Extract challenge from client data JSON
            String challenge = extractChallengeFromClientData(credJson);
            if (challenge == null || challenge.isEmpty()) {
                throw new IllegalArgumentException("No challenge found in client data JSON");
            }

            // Create WebAuthn4JCredentials with the credential data from client
            WebAuthn4JCredentials credentials = new WebAuthn4JCredentials()
                .setUsername(username)
                .setChallenge(challenge)
                .setOrigin(origin)  // Required for security validation
                .setDomain(domain)  // Required for security validation
                .setWebauthn(credJson);

            // Use WebAuthn4J to validate authentication
            webAuthn4j.authenticate(credentials)
                .onSuccess(user -> {
                    // Authentication successful
                    String credentialId = credJson.getString("id");
                    
                    // Update credential usage
                    JsonObject storedCredential = this.credentials.get(credentialId);
                    if (storedCredential != null) {
                        storedCredential.put("lastUsedAt", System.currentTimeMillis());
                        this.credentials.put(credentialId, storedCredential);
                    }

                    logger.info("Authentication validated successfully for user: {} with credential: {}", username, credentialId);
                })
                .onFailure(throwable -> {
                    logger.error("Authentication validation failed for user: {}", username, throwable);
                    throw new RuntimeException("Authentication validation failed: " + throwable.getMessage(), throwable);
                });
            
        } catch (Exception e) {
            logger.error("Authentication validation failed for user: {}", username, e);
            throw new RuntimeException("Authentication validation failed: " + e.getMessage(), e);
        }
    }

    // Additional service methods

    private String extractChallengeFromClientData(JsonObject credential) {
        try {
            JsonObject response = credential.getJsonObject("response");
            if (response == null) {
                return null;
            }
            
            String clientDataJSON = response.getString("clientDataJSON");
            if (clientDataJSON == null || clientDataJSON.isEmpty()) {
                return null;
            }
            
            // Decode base64 client data JSON
            byte[] decodedBytes = Base64.getUrlDecoder().decode(clientDataJSON);
            String clientData = new String(decodedBytes);
            
            // Parse JSON to extract challenge
            JsonObject clientDataObj = new JsonObject(clientData);
            String challenge = clientDataObj.getString("challenge");
            
            // The challenge from client data JSON is already base64url encoded
            // We need to return it as-is for the WebAuthn4J library
            return challenge;
            
        } catch (Exception e) {
            logger.error("Failed to extract challenge from client data JSON", e);
            return null;
        }
    }

    public MDSService getMdsService() {
        return mdsService;
    }


    public Map<String, JsonObject> getCredentials() {
        return new HashMap<>(credentials);
    }

    public JsonObject getCredential(String credentialId) {
        return credentials.get(credentialId);
    }

    public boolean removeCredential(String credentialId) {
        return credentials.remove(credentialId) != null;
    }

    public WebAuthn4J getWebAuthn4J() {
        return webAuthn4j;
    }

    /**
     * Extract attestation x5c and AAGUID, then verify chain anchoring against either
     * MDS-provided roots (when enabled / useMdsOnly) or the dynamic TrustAnchorRepository.
     * If verification fails, throws an exception to abort the registration.
     */
    private void preValidateAttestationAnchoring(JsonObject credential) {
        try {
            JsonObject response = credential.getJsonObject("response");
            if (response == null) {
                return; // let WebAuthn4J handle errors
            }
            String attestationObjectB64 = response.getString("attestationObject");
            if (attestationObjectB64 == null || attestationObjectB64.isEmpty()) {
                return; // let WebAuthn4J handle errors
            }

            byte[] attestationObject = Base64.getUrlDecoder().decode(attestationObjectB64);
            ObjectMapper cborMapper = new ObjectMapper(new CBORFactory());
            Map<String, Object> attObj = cborMapper.readValue(attestationObject, new TypeReference<Map<String, Object>>() {});

            // Extract x5c array from attStmt
            @SuppressWarnings("unchecked")
            Map<String, Object> attStmt = (Map<String, Object>) attObj.get("attStmt");
            List<X509Certificate> chain = new ArrayList<>();
            if (attStmt != null && attStmt.get("x5c") instanceof List) {
                @SuppressWarnings("unchecked")
                List<Object> x5cList = (List<Object>) attStmt.get("x5c");
                for (Object o : x5cList) {
                    if (o instanceof byte[]) {
                        X509Certificate cert = (X509Certificate) java.security.cert.CertificateFactory.getInstance("X.509")
                            .generateCertificate(new java.io.ByteArrayInputStream((byte[]) o));
                        chain.add(cert);
                    }
                }
            }

            // Extract AAGUID from authData (if present)
            String aaguidStr = null;
            Object authDataObj = attObj.get("authData");
            if (authDataObj instanceof byte[]) {
                byte[] authData = (byte[]) authDataObj;
                if (authData.length >= 37 + 16) {
                    // flags at 32, counter 33..36; attested credential data follows if flags bit 6 set
                    byte flags = authData[32];
                    boolean attestedCredDataPresent = (flags & 0x40) != 0;
                    if (attestedCredDataPresent) {
                        int offset = 37; // 32 rpIdHash + 1 flags + 4 counter
                        byte[] aaguid = Arrays.copyOfRange(authData, offset, offset + 16);
                        // Convert to UUID string
                        java.nio.ByteBuffer bb = java.nio.ByteBuffer.wrap(aaguid).order(java.nio.ByteOrder.BIG_ENDIAN);
                        long msb = bb.getLong();
                        long lsb = bb.getLong();
                        aaguidStr = new java.util.UUID(msb, lsb).toString();
                    }
                }
            }

            if (chain.isEmpty()) {
                logger.debug("No x5c in attestation; skipping pre-validation");
                return;
            }

            boolean ok;
            if (useMdsOnly && aaguidStr != null && mdsService.isEnabled()) {
                List<String> roots = mdsService.getAttestationRootCertificates(aaguidStr);
                ok = certificateService.verifyChainAgainstMdsRoots(chain, roots);
                if (!ok) {
                    throw new IllegalStateException("Attestation chain not anchored to MDS roots for AAGUID " + aaguidStr);
                }
                logger.info("Attestation chain anchored via MDS for AAGUID {}", aaguidStr);
            } else {
                ok = certificateService.verifyAndroidKeyAttestationChain(chain);
                if (!ok) {
                    throw new IllegalStateException("Attestation chain not anchored to dynamic trust anchors");
                }
                logger.info("Attestation chain anchored via dynamic trust anchors");
            }
        } catch (Exception e) {
            throw new RuntimeException("Pre-validation of attestation chain failed: " + e.getMessage(), e);
        }
    }
}