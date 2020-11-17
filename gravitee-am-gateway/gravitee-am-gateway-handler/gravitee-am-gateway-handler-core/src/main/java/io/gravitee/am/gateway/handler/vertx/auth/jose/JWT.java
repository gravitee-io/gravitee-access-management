/**
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.am.gateway.handler.vertx.auth.jose;

import io.gravitee.am.gateway.handler.vertx.auth.CertificateHelper;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * JWT and JWS implementation draft-ietf-oauth-json-web-token-32.
 *
 * @author Paulo Lopes
 */
public final class JWT {

    private final Logger logger = LoggerFactory.getLogger(JWT.class);

    // simple random as its value is just to create entropy
    private static final Random RND = new Random();

    private static final Charset UTF8 = StandardCharsets.UTF_8;

    // as described in the terminology section: https://tools.ietf.org/html/rfc7515#section-2
    private static final Base64.Encoder urlEncoder = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder urlDecoder = Base64.getUrlDecoder();
    private static final Base64.Decoder decoder = Base64.getDecoder();

    private boolean allowEmbeddedKey = false;

    // keep 2 maps (1 for encode, 1 for decode)
    private final Map<String, List<Crypto>> SIGN = new ConcurrentHashMap<>();
    private final Map<String, List<Crypto>> VERIFY = new ConcurrentHashMap<>();

    public JWT() {
        // Spec requires "none" to always be available
        SIGN.put("none", Collections.singletonList(new CryptoNone()));
        VERIFY.put("none", Collections.singletonList(new CryptoNone()));
    }

    /**
     * Adds a JSON Web Key (rfc7517) to the crypto map.
     *
     * @param jwk a JSON Web Key
     * @return self
     */
    public JWT addJWK(JWK jwk) {

        List<Crypto> current = null;

        if (jwk.isFor(JWK.USE_ENC)) {
            current = VERIFY.computeIfAbsent(jwk.getAlgorithm(), k -> new ArrayList<>());
            addJWK(current, jwk);
        }

        if (jwk.isFor(JWK.USE_SIG)) {
            current = SIGN.computeIfAbsent(jwk.getAlgorithm(), k -> new ArrayList<>());
            addJWK(current, jwk);
        }

        if (current == null) {
            throw new IllegalStateException("unknown JWK use: " + jwk.getUse());
        }

        return this;
    }

    /**
     * Enable/Disable support for embedded keys. Default {@code false}.
     *
     * By default this is disabled as it could be used as an attack vector to the application. A malicious user could
     * generate a self signed certificate and embed the public certificate on the token, which would always pass the
     * validation.
     *
     * Users of this feature should regardless of the validation status, ensure that the chain is valid by adding a
     * well known root certificate (that has been previously agreed with the server).
     *
     * @param allowEmbeddedKey when true embedded keys are used to check the signature.
     * @return fluent self.
     */
    public JWT allowEmbeddedKey(boolean allowEmbeddedKey) {
        this.allowEmbeddedKey = allowEmbeddedKey;
        return this;
    }

    private void addJWK(List<Crypto> current, JWK jwk) {
        boolean replaced = false;
        for (int i = 0; i < current.size(); i++) {
            if (current.get(i).getLabel().equals(jwk.getLabel())) {
                // replace
                current.set(i, jwk);
                replaced = true;
                break;
            }
        }

        if (!replaced) {
            // non existent, add it!
            current.add(jwk);
        }
    }

    public static JsonObject parse(final byte[] token) {
        return parse(new String(token, UTF8));
    }

    public static JsonObject parse(final String token) {
        String[] segments = token.split("\\.");
        if (segments.length < 2 || segments.length > 3) {
            throw new RuntimeException("Not enough or too many segments [" + segments.length + "]");
        }

        // All segment should be base64
        String headerSeg = segments[0];
        String payloadSeg = segments[1];
        String signatureSeg = segments.length == 2 ? null : segments[2];

        // base64 decode and parse JSON
        JsonObject header = new JsonObject(new String(base64urlDecode(headerSeg), UTF8));
        JsonObject payload = new JsonObject(new String(base64urlDecode(payloadSeg), UTF8));

        return new JsonObject()
                .put("header", header)
                .put("payload", payload)
                .put("signatureBase", (headerSeg + "." + payloadSeg))
                .put("signature", signatureSeg);
    }

    public JsonObject decode(final String token) {
        return decode(token, false);
    }

    public JsonObject decode(final String token, boolean full) {
        // lock the secure state
        String[] segments = token.split("\\.");

        if (segments.length < 2) {
            throw new IllegalStateException("Invalid format for JWT");
        }

        // All segment should be base64
        String headerSeg = segments[0];
        String payloadSeg = segments[1];
        String signatureSeg = segments.length == 3 ? segments[2] : null;

        // empty signature is never allowed
        if ("".equals(signatureSeg)) {
            throw new IllegalStateException("Signature is required");
        }

        // base64 decode and parse JSON
        JsonObject header = new JsonObject(Buffer.buffer(base64urlDecode(headerSeg)));

        final boolean unsecure = isUnsecure();
        if (unsecure) {
            // if there isn't a certificate chain in the header, we are dealing with a strictly
            // unsecure mode validation. In this case the number of segments must be 2
            // if there is a certificate chain, we allow it to proceed and later we will assert
            // against this chain
            if (!allowEmbeddedKey && segments.length != 2) {
                throw new IllegalStateException("JWT is in unsecured mode but token is signed.");
            }
        } else {
            if (!allowEmbeddedKey && segments.length != 3) {
                throw new IllegalStateException("JWT is in secure mode but token is not signed.");
            }
        }

        JsonObject payload = new JsonObject(Buffer.buffer(base64urlDecode(payloadSeg)));

        String alg = header.getString("alg");

        // if we only allow secure alg, then none is not a valid option
        if (!unsecure && "none".equals(alg)) {
            throw new IllegalStateException("Algorithm \"none\" not allowed");
        }

        // handle the x5c case, only in allowEmbeddedKey mode
        if (allowEmbeddedKey && header.containsKey("x5c")) {
            // if signatureSeg is null fail
            if (signatureSeg == null) {
                throw new IllegalStateException("missing signature segment");
            }

            try {
                JsonArray chain = header.getJsonArray("x5c");
                List<X509Certificate> certChain = new ArrayList<>();

                if (chain == null || chain.size() == 0) {
                    throw new IllegalStateException("x5c chain is null or empty");
                }

                for (int i = 0; i < chain.size(); i++) {
                    // "x5c" (X.509 Certificate Chain) Header Parameter
                    // https://tools.ietf.org/html/rfc7515#section-4.1.6
                    // states:
                    // Each string in the array is a base64-encoded (Section 4 of [RFC4648] -- not base64url-encoded) DER
                    // [ITU.X690.2008] PKIX certificate value.
                    certChain.add(JWS.parseX5c(decoder.decode(chain.getString(i).getBytes(UTF8))));
                }

                CertificateHelper.checkValidity(certChain, false, null);

                if (JWS.verifySignature(alg, certChain.get(0), base64urlDecode(signatureSeg), (headerSeg + "." + payloadSeg).getBytes(UTF8))) {
                    // ok
                    return full ? new JsonObject().put("header", header).put("payload", payload) : payload;
                } else {
                    throw new RuntimeException("Signature verification failed");
                }
            } catch (CertificateException | NoSuchAlgorithmException | InvalidKeyException | SignatureException | InvalidAlgorithmParameterException | NoSuchProviderException e) {
                throw new RuntimeException("Signature verification failed", e);
            }
        }

        List<Crypto> cryptos = VERIFY.get(alg);

        if (cryptos == null || cryptos.size() == 0) {
            throw new NoSuchKeyIdException(alg);
        }

        // verify signature. `sign` will return base64 string.
        if (!unsecure) {
            // if signatureSeg is null fail
            if (signatureSeg == null) {
                throw new IllegalStateException("missing signature segment");
            }
            byte[] payloadInput = base64urlDecode(signatureSeg);
            byte[] signingInput = (headerSeg + "." + payloadSeg).getBytes(UTF8);

            String kid = header.getString("kid");
            boolean hasKey = false;

            for (Crypto c : cryptos) {
                // if a token has a kid and it doesn't match the crypto id skip it
                if (kid != null && c.getId() != null && !kid.equals(c.getId())) {
                    continue;
                }
                // signal that this object crypto's list has the required key
                hasKey = true;
                if (c.verify(payloadInput, signingInput)) {
                    return full ? new JsonObject().put("header", header).put("payload", payload) : payload;
                }
            }

            if (hasKey) {
                throw new RuntimeException("Signature verification failed");
            } else {
                throw new NoSuchKeyIdException(alg, kid);
            }
        }

        return full ? new JsonObject().put("header", header).put("payload", payload) : payload;
    }

    /**
     * Scope claim are used to grant access to a specific resource.
     * They are included into the JWT when the user consent access to the resource,
     * or sometimes without user consent (bypass approval).
     * @param jwt JsonObject decoded json web token value.
     * @param options JWTOptions coming from the provider.
     * @return true if required scopes are into the JWT.
     */
    public boolean isScopeGranted(JsonObject jwt, JWTOptions options) {

        if(jwt == null) {
            return false;
        }

        if(options.getScopes() == null || options.getScopes().isEmpty()) {
            return true; // no scopes to check
        }

        if(jwt.getValue("scope") == null) {
            if (logger.isDebugEnabled()) {
                logger.debug("Invalid JWT: scope claim is required");
            }
            return false;
        }

        JsonArray target;
        if (jwt.getValue("scope") instanceof String) {
            target = new JsonArray(
                    Stream.of(jwt.getString("scope")
                            .split(options.getScopeDelimiter()))
                            .collect(Collectors.toList())
            );
        } else {
            target = jwt.getJsonArray("scope");
        }

        if(!target.getList().containsAll(options.getScopes())) {
            if (logger.isDebugEnabled()) {
                logger.debug(String.format("Invalid JWT scopes expected[%s] actual[%s]", options.getScopes(), target.getList()));
            }
            return false;
        }

        return true;
    }

    public String sign(JsonObject payload, JWTOptions options) {
        final String algorithm = options.getAlgorithm();

        List<Crypto> cryptos = SIGN.get(algorithm);

        if (cryptos == null || cryptos.size() == 0) {
            throw new RuntimeException("Algorithm not supported: " + algorithm);
        }

        // lock the crypto implementation
        final Crypto crypto = cryptos.get(RND.nextInt(cryptos.size()));

        // header, typ is fixed value.
        JsonObject header = new JsonObject()
                .mergeIn(options.getHeader())
                .put("typ", "JWT")
                .put("alg", algorithm);

        // add kid if present
        if (crypto.getId() != null) {
            header.put("kid", crypto.getId());
        }

        // NumericDate is a number is seconds since 1st Jan 1970 in UTC
        long timestamp = System.currentTimeMillis() / 1000;

        if (!options.isNoTimestamp()) {
            payload.put("iat", payload.getValue("iat", timestamp));
        }

        if (options.getExpiresInSeconds() > 0) {
            payload.put("exp", timestamp + options.getExpiresInSeconds());
        }

        if (options.getAudience() != null && options.getAudience().size() >= 1) {
            if (options.getAudience().size() > 1) {
                payload.put("aud", new JsonArray(options.getAudience()));
            } else {
                payload.put("aud", options.getAudience().get(0));
            }
        }

        if(options.getScopes() != null && options.getScopes().size() >= 1) {
            if(options.hasScopeDelimiter()) {
                payload.put("scope", String.join(options.getScopeDelimiter(), options.getScopes()));
            } else {
                payload.put("scope", new JsonArray(options.getScopes()));
            }
        }

        if (options.getIssuer() != null) {
            payload.put("iss", options.getIssuer());
        }

        if (options.getSubject() != null) {
            payload.put("sub", options.getSubject());
        }

        // create segments, all segment should be base64 string
        String headerSegment = base64urlEncode(header.encode());
        String payloadSegment = base64urlEncode(payload.encode());
        String signingInput = headerSegment + "." + payloadSegment;
        String signSegment = base64urlEncode(crypto.sign(signingInput.getBytes(UTF8)));

        return headerSegment + "." + payloadSegment + "." + signSegment;
    }

    private static byte[] base64urlDecode(String str) {
        return urlDecoder.decode(str.getBytes(UTF8));
    }

    private static String base64urlEncode(String str) {
        return base64urlEncode(str.getBytes(UTF8));
    }

    private static String base64urlEncode(byte[] bytes) {
        return urlEncoder.encodeToString(bytes);
    }

    public boolean isUnsecure() {
        return VERIFY.size() == 1 && SIGN.size() == 1;
    }

    public Collection<String> availableAlgorithms() {
        Set<String> algorithms = new HashSet<>();

        algorithms.addAll(VERIFY.keySet());
        algorithms.addAll(SIGN.keySet());

        return algorithms;
    }
}
