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
package io.gravitee.am.extensiongrant.jwtbearer.provider;

import com.nimbusds.jose.util.ResourceRetriever;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.JWTProcessor;
import io.gravitee.am.common.exception.jwt.ExpiredJWTException;
import io.gravitee.am.common.exception.jwt.MalformedJWTException;
import io.gravitee.am.common.exception.jwt.PrematureJWTException;
import io.gravitee.am.common.exception.jwt.SignatureException;
import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.common.jwt.SignatureAlgorithm;
import io.gravitee.am.common.oidc.StandardClaims;
import io.gravitee.am.extensiongrant.api.ExtensionGrantProvider;
import io.gravitee.am.extensiongrant.api.exceptions.InvalidGrantException;
import io.gravitee.am.extensiongrant.jwtbearer.JWTBearerExtensionGrantConfiguration;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.identityprovider.common.oauth2.jwt.jwks.ecdsa.ECDSAJWKSourceResolver;
import io.gravitee.am.identityprovider.common.oauth2.jwt.jwks.hmac.MACJWKSourceResolver;
import io.gravitee.am.identityprovider.common.oauth2.jwt.jwks.remote.RemoteJWKSourceResolver;
import io.gravitee.am.identityprovider.common.oauth2.jwt.jwks.rsa.RSAJWKSourceResolver;
import io.gravitee.am.identityprovider.common.oauth2.jwt.processor.AbstractKeyProcessor;
import io.gravitee.am.identityprovider.common.oauth2.jwt.processor.HMACKeyProcessor;
import io.gravitee.am.identityprovider.common.oauth2.jwt.processor.JWKSKeyProcessor;
import io.gravitee.am.identityprovider.common.oauth2.jwt.processor.RSAKeyProcessor;
import io.gravitee.am.repository.oauth2.model.request.TokenRequest;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.Assert;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.gravitee.am.common.jwt.Claims.GIO_INTERNAL_SUB;
import static io.gravitee.am.common.jwt.Claims.SUB;
import static java.util.Arrays.copyOfRange;
import static java.util.Objects.nonNull;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Slf4j
public class JWTBearerExtensionGrantProvider implements ExtensionGrantProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(JWTBearerExtensionGrantProvider.class);
    private static final String ASSERTION_QUERY_PARAM = "assertion";
    static final Pattern SSH_PUB_KEY = Pattern.compile("((ecdsa)(.*)|ssh-(rsa|dsa)) ([A-Za-z0-9/+]+=*)( .*)?");
    private static final int OPENSSH_KEY_HEADER_LENGTH = 39;
    private static final SignatureAlgorithm[] ALLOWED_SIGNATURE_ALGORITHMS = new SignatureAlgorithm[]{
            SignatureAlgorithm.RS256,
            SignatureAlgorithm.RS384,
            SignatureAlgorithm.RS512,
            SignatureAlgorithm.ES256,
            SignatureAlgorithm.ES384,
            SignatureAlgorithm.ES512,
            SignatureAlgorithm.HS256,
            SignatureAlgorithm.HS384,
            SignatureAlgorithm.HS512};

    private static final Map<Integer, String> OPENSSH_KEY_LENGHTS = Map.of(
            104, "secp256r1",
            136, "secp384r1",
            172, "secp521r1"
    );

    @Autowired
    private JWTBearerExtensionGrantConfiguration jwtBearerTokenGranterConfiguration;

    @Autowired
    private ResourceRetriever resourceRetriever;

    @Override
    public Maybe<User> grant(TokenRequest tokenRequest) throws InvalidGrantException {
        String assertion = tokenRequest.getRequestParameters().get(ASSERTION_QUERY_PARAM);

        if (assertion == null) {
            throw new InvalidGrantException("Assertion value is missing");
        }
        return Observable.fromCallable(() -> {
            JWTProcessor<?> processor;
            try {
                processor = generateJWTProcessor(assertion);
                JWTClaimsSet claimsSet = processor.process(assertion, null);
                return createUser(claimsSet);
            } catch (MalformedJWTException | ExpiredJWTException | PrematureJWTException | SignatureException |
                     com.nimbusds.jwt.proc.ExpiredJWTException ex) {
                LOGGER.debug(ex.getMessage(), ex.getCause());
                throw new InvalidGrantException(ex.getMessage(), ex);
            } catch (Exception ex) {
                LOGGER.error(ex.getMessage(), ex.getCause());
                throw new InvalidGrantException(ex.getMessage(), ex);
            }
        })
        .subscribeOn(Schedulers.io())
        .firstElement()
        .observeOn(Schedulers.computation());
    }

    public User createUser(JWTClaimsSet claimsSet) {
        final String sub = claimsSet.getSubject();

        final String username = claimsSet.getClaims().containsKey(StandardClaims.PREFERRED_USERNAME) ?
                claimsSet.getClaim((StandardClaims.PREFERRED_USERNAME)).toString() : sub;
        DefaultUser user = new DefaultUser(username);
        user.setId(sub);
        // set claims
        Map<String, Object> additionalInformation = new HashMap<>();
        // add sub required claim
        additionalInformation.put(SUB, sub);
        if (claimsSet.getClaim(Claims.GIO_INTERNAL_SUB) != null) {
            additionalInformation.put(GIO_INTERNAL_SUB, claimsSet.getClaim(Claims.GIO_INTERNAL_SUB));
        }
        List<Map<String, String>> claimsMapper = jwtBearerTokenGranterConfiguration.getClaimsMapper();
        if (claimsMapper != null && !claimsMapper.isEmpty()) {
            claimsMapper.forEach(claimMapper -> {
                String assertionClaim = claimMapper.get("assertion_claim");
                String tokenClaim = claimMapper.get("token_claim");
                if (claimsSet.getClaims().containsKey(assertionClaim)) {
                    additionalInformation.put(tokenClaim, claimsSet.getClaim(assertionClaim));
                }
            });
        }
        user.setAdditionalInformation(additionalInformation);
        return user;
    }

    private JWTProcessor<?> generateJWTProcessor(String assertion) {
        SignatureAlgorithm signatureAlgorithm;
        try {
            signatureAlgorithm = SignatureAlgorithm.valueOf(extractAlgorithmFromJWT(assertion));
            if(!Arrays.asList(ALLOWED_SIGNATURE_ALGORITHMS).contains(signatureAlgorithm)) {
                LOGGER.warn("Algorithm [{}] is not supported. List of Supported Algorithms: {}", signatureAlgorithm, Arrays.asList(ALLOWED_SIGNATURE_ALGORITHMS));
                throw new InvalidGrantException("Algorithm [" + signatureAlgorithm + "] is not supported");
            }
        }catch (Exception e){
            LOGGER.error("Error extracting signature algorithm", e);
            throw new InvalidGrantException("Error extracting signature algorithm");
        }

        AbstractKeyProcessor<?> keyProcessor = null;
        if (jwtBearerTokenGranterConfiguration.usesJWKs()) {
            keyProcessor = new JWKSKeyProcessor<>();
            keyProcessor.setJwkSourceResolver(new RemoteJWKSourceResolver<>(resourceRetriever, jwtBearerTokenGranterConfiguration.getPublicKey()));
        } else {
            // get the corresponding key processor
            final String publicKey = jwtBearerTokenGranterConfiguration.getPublicKey();
            switch (signatureAlgorithm) {
                case RS256, RS384, RS512 -> {
                    keyProcessor = new RSAKeyProcessor<>();
                    RSAJWKSourceResolver resolver;
                    if (publicKey.startsWith("ssh-rsa")) {
                        resolver = new RSAJWKSourceResolver<>(parseSshRSAPublicKey(publicKey));
                    } else {
                        resolver = new RSAJWKSourceResolver<>(publicKey);
                    }
                    keyProcessor.setJwkSourceResolver(resolver);
                }
                case HS256, HS384, HS512 -> {
                    keyProcessor = new HMACKeyProcessor<>();
                    keyProcessor.setJwkSourceResolver(new MACJWKSourceResolver<>(publicKey));
                }
                case ES256, ES384, ES512 -> {
                    keyProcessor = new RSAKeyProcessor<>();
                    ECDSAJWKSourceResolver resolver;
                    if (publicKey.startsWith("ecdsa")) {
                        resolver = new ECDSAJWKSourceResolver<>(parseEcPublicKey(publicKey));
                    } else {
                        resolver = new ECDSAJWKSourceResolver<>(publicKey);
                    }
                    keyProcessor.setJwkSourceResolver(resolver);
                }
            }
        }
        Assert.notNull(keyProcessor, "A key processor must be set");
        return keyProcessor.create(signatureAlgorithm);
    }

    /**
     * Extracts the algorithm from JWT header
     */
    private String extractAlgorithmFromJWT(String jwt) {

        String[] parts = jwt.split("\\.");
        if (parts.length < 2) {
            throw new IllegalArgumentException("Invalid JWT format");
        }

        String header = new String(Base64.getDecoder().decode(parts[0]), StandardCharsets.UTF_8);
        String algPattern = "\"alg\"\\s*:\\s*\"([^\"]+)\"";
        Pattern pattern = Pattern.compile(algPattern);
        Matcher matcher = pattern.matcher(header);

        if (matcher.find()) {
            return matcher.group(1);
        } else {
            LOGGER.warn("Failed to extract algorithm from JWT header");
            throw new IllegalArgumentException("Algorithm not found in JWT header");
        }
    }

    /**
     * <pre>
     * Each rsa key should start with xxxxssh-rsa and then contains two big integer (modulus & exponent) which are prime number.
     * The modulus & exponent are used to generate the RSA Public Key.
     * <a href="https://en.wikipedia.org/wiki/RSA_(cryptosystem)">See wiki explanations for deeper understanding</a>
     * </pre>
     * @param encKey String
     * @return RSAPublicKey
     */
    private static RSAPublicKey parseSshRSAPublicKey(String encKey) {
        final byte[] PREFIX = new byte[]{0, 0, 0, 7, 's', 's', 'h', '-', 'r', 's', 'a'};

        String[] parts = encKey.split(" ");
        String encryptedKey = parts[1];
        ByteArrayInputStream in = new ByteArrayInputStream(Base64.getDecoder().decode(StandardCharsets.UTF_8.encode(encryptedKey)).array());

        byte[] prefix = new byte[11];

        try {
            if (in.read(prefix) != 11 || !Arrays.equals(PREFIX, prefix)) {
                throw new IllegalArgumentException("SSH key prefix not found");
            }

            BigInteger e = new BigInteger(readBigInteger(in));//public exponent
            BigInteger n = new BigInteger(readBigInteger(in));//modulus

            return createPublicKey(n, e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static RSAPublicKey createPublicKey(BigInteger n, BigInteger e) {
        try {
            return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(n, e));
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    static ECPublicKey parseEcPublicKey(String publicKey) {
        try {
            X509EncodedKeySpec x509KeySpec = getX509EncodedKeySpec(publicKey);
            var keyFactory = KeyFactory.getInstance("EC");
            return (ECPublicKey) keyFactory.generatePublic(x509KeySpec);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * Since Java follows the ASN.1-based encoding also specified in ANSI X9.62 and
     * OpenSSH the <a href="https://www.rfc-editor.org/rfc/rfc5656#section-3.1">RFC 5656, section 3.1.</a>
     * The only difference is that the size of the headers differs. OpenSSH uses a header of 39 in length.
     * Since the encoding of X and Y are similar in size in both conventions, in order to comply with the standard
     * we switch the header size to obtain the desired public key.
     * */
    private static X509EncodedKeySpec getX509EncodedKeySpec(String publicKey) throws InvalidAlgorithmParameterException, NoSuchAlgorithmException {
        String[] parts = publicKey.split(" ");
        if (parts.length < 2) {
            throw new IllegalArgumentException("Wrong SSH key format");
        }
        String base64 = parts[1];
        final byte[] decodedPubKey = Base64.getDecoder().decode(base64);
        if (nonNull(resolveAlgorithm(decodedPubKey.length))) {
            return getOpenSSHX509EncodedKeySpec(decodedPubKey);
        }
        return new X509EncodedKeySpec(decodedPubKey);
    }

    private static X509EncodedKeySpec getOpenSSHX509EncodedKeySpec(byte[] decodedPubKey) throws InvalidAlgorithmParameterException, NoSuchAlgorithmException {
        final byte[] suffix = copyOfRange(decodedPubKey, OPENSSH_KEY_HEADER_LENGTH, decodedPubKey.length);
        final byte[] prefix = generateEcDSAJavaHeader(decodedPubKey.length, suffix.length);
        final byte[] transformedPublickey = new byte[prefix.length + suffix.length];
        System.arraycopy(prefix, 0, transformedPublickey, 0, prefix.length);
        System.arraycopy(suffix, 0, transformedPublickey, prefix.length, suffix.length);
        return new X509EncodedKeySpec(transformedPublickey);
    }

    /**
     * bytes are not in the good order, they are in the big endian format, we reorder them before reading them...
     * Each time you call this method, the buffer position will move, so result are differents...
     * @param in byte array of a public encryption key without 11 "xxxxssh-rsa" first byte.
     * @return BigInteger public exponent on first call, then modulus.
     * @throws IOException exception
     */
    private static byte[] readBigInteger(ByteArrayInputStream in) throws IOException {
        byte[] b = new byte[4];

        if (in.read(b) != 4) {
            throw new IOException("Expected length data as 4 bytes");
        }

        int l = (b[0] << 24) | (b[1] << 16) | (b[2] << 8) | b[3];

        b = new byte[l];

        if (in.read(b) != l) {
            throw new IOException("Expected " + l + " key bytes");
        }

        return b;
    }

    public static byte[] generateEcDSAJavaHeader(int keySize, int suffixSize) throws InvalidAlgorithmParameterException, NoSuchAlgorithmException {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        kpg.initialize(new ECGenParameterSpec(resolveAlgorithm(keySize)));
        final byte[] encodedKey = kpg.genKeyPair().getPublic().getEncoded();
        return Arrays.copyOfRange(encodedKey, 0, encodedKey.length - suffixSize);
    }

    private static String resolveAlgorithm(int keySize) {
        return OPENSSH_KEY_LENGHTS.get(keySize);
    }

}
