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

import io.gravitee.am.common.exception.jwt.ExpiredJWTException;
import io.gravitee.am.common.exception.jwt.MalformedJWTException;
import io.gravitee.am.common.exception.jwt.PrematureJWTException;
import io.gravitee.am.common.exception.jwt.SignatureException;
import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.common.oidc.StandardClaims;
import io.gravitee.am.extensiongrant.api.ExtensionGrantProvider;
import io.gravitee.am.extensiongrant.api.exceptions.InvalidGrantException;
import io.gravitee.am.extensiongrant.jwtbearer.JWTBearerExtensionGrantConfiguration;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.jwt.DefaultJWTParser;
import io.gravitee.am.jwt.JWTParser;
import io.gravitee.am.repository.oauth2.model.request.TokenRequest;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
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
public class JWTBearerExtensionGrantProvider implements ExtensionGrantProvider, InitializingBean {

    private static final Logger LOGGER = LoggerFactory.getLogger(JWTBearerExtensionGrantProvider.class);
    private static final String ASSERTION_QUERY_PARAM = "assertion";
    static final Pattern SSH_PUB_KEY = Pattern.compile("((ecdsa)(.*)|ssh-(rsa|dsa)) ([A-Za-z0-9/+]+=*)( .*)?");
    private static final int OPENSSH_KEY_HEADER_LENGTH = 39;
    private JWTParser jwtParser;

    private static final Map<Integer, String> OPENSSH_KEY_LENGHTS = Map.of(
            104, "secp256r1",
            136, "secp384r1",
            172, "secp521r1"
    );

    @Autowired
    private JWTBearerExtensionGrantConfiguration jwtBearerTokenGranterConfiguration;

    @Override
    public void afterPropertiesSet() throws Exception {
        PublicKey publicKey = parsePublicKey(jwtBearerTokenGranterConfiguration.getPublicKey());
        jwtParser = new DefaultJWTParser(publicKey);
    }

    @Override
    public Maybe<User> grant(TokenRequest tokenRequest) throws InvalidGrantException {
        String assertion = tokenRequest.getRequestParameters().get(ASSERTION_QUERY_PARAM);

        if (assertion == null) {
            throw new InvalidGrantException("Assertion value is missing");
        }
        return Observable.fromCallable(() -> {
            try {
                JWT jwt = jwtParser.parse(assertion);
                return createUser(jwt);
            } catch (MalformedJWTException | ExpiredJWTException | PrematureJWTException | SignatureException ex) {
                LOGGER.debug(ex.getMessage(), ex.getCause());
                throw new InvalidGrantException(ex.getMessage(), ex);
            } catch (Exception ex) {
                LOGGER.error(ex.getMessage(), ex.getCause());
                throw new InvalidGrantException(ex.getMessage(), ex);
            }
        })
        .subscribeOn(Schedulers.io())
        .firstElement();
    }

    public User createUser(JWT jwt) {
        final String sub = jwt.getSub();
        final String username = jwt.containsKey(StandardClaims.PREFERRED_USERNAME) ?
                jwt.get(StandardClaims.PREFERRED_USERNAME).toString() : sub;
        DefaultUser user = new DefaultUser(username);
        user.setId(sub);
        // set claims
        Map<String, Object> additionalInformation = new HashMap<>();
        // add sub required claim
        additionalInformation.put(SUB, sub);
        if (jwt.getInternalSub() != null) {
            additionalInformation.put(GIO_INTERNAL_SUB, jwt.getInternalSub());
        }
        List<Map<String, String>> claimsMapper = jwtBearerTokenGranterConfiguration.getClaimsMapper();
        if (claimsMapper != null && !claimsMapper.isEmpty()) {
            claimsMapper.forEach(claimMapper -> {
                String assertionClaim = claimMapper.get("assertion_claim");
                String tokenClaim = claimMapper.get("token_claim");
                if (jwt.containsKey(assertionClaim)) {
                    additionalInformation.put(tokenClaim, jwt.get(assertionClaim));
                }
            });
        }
        user.setAdditionalInformation(additionalInformation);
        return user;
    }



    /**
     * Generate RSA Public Key from the ssh-(rsa|dsa) ([A-Za-z0-9/+]+=*) (.*) stored key.
     * @param key String.
     * @return RSAPublicKey
     */
    static PublicKey parsePublicKey(String key) {
        Matcher m = SSH_PUB_KEY.matcher(key);

        if (m.matches()) {
            String alg = m.group(2) != null ? m.group(2) : m.group(4);
            String encKey = m.group(5);

            final boolean isRSA = "rsa".equalsIgnoreCase(alg);
            final boolean isECDSA = "ecdsa".equalsIgnoreCase(alg);
            if (!(isRSA || isECDSA)) {
                throw new IllegalArgumentException("Only RSA or ECDSA is currently supported, but algorithm was " + alg);
            }

            return isRSA ? parseSshRSAPublicKey(encKey) : parseEcPublicKey(encKey);
        }

        return null;
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
    private static PublicKey parseSshRSAPublicKey(String encKey) {
        final byte[] PREFIX = new byte[]{0, 0, 0, 7, 's', 's', 'h', '-', 'r', 's', 'a'};
        ByteArrayInputStream in = new ByteArrayInputStream(Base64.getDecoder().decode(StandardCharsets.UTF_8.encode(encKey)).array());

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
        final byte[] decodedPubKey = Base64.getDecoder().decode(publicKey);
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
     * @throws IOException
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
