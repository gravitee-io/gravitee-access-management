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
package io.gravitee.am.jwt;

import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import io.gravitee.am.common.exception.jwt.ExpiredJWTException;
import io.gravitee.am.common.exception.jwt.MalformedJWTException;
import io.gravitee.am.common.exception.jwt.PrematureJWTException;
import io.gravitee.am.common.exception.jwt.SignatureException;
import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.common.jwt.SignatureAlgorithm;
import io.vertx.core.http.impl.CookieImpl;
import io.vertx.core.http.impl.Http1xServerResponse;
import org.junit.Test;
import org.mockito.Mockito;

import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class DefaultJWTParserTest {

    @Test
    public void shouldParse_ec() throws Exception {
        ECKey ecKey = new ECKeyGenerator(Curve.P_256)
                .keyID("123")
                .generate();
        ECPrivateKey rsaPrivateKey = ecKey.toECPrivateKey();
        ECPublicKey rsaPublicKey = ecKey.toECPublicKey();
        String algorithm = SignatureAlgorithm.ES256.getValue();
        JWTBuilder jwtBuilder  = new DefaultJWTBuilder(rsaPrivateKey, algorithm, ecKey.getKeyID());
        JWTParser jwtParser = new DefaultJWTParser(rsaPublicKey);
        assertJwt(jwtBuilder, jwtParser, algorithm);
    }

    @Test
    public void shouldParse_rsa() throws Exception {
        RSAKey rsaJWK = new RSAKeyGenerator(2048)
                .keyID("123")
                .generate();
        RSAPrivateKey rsaPrivateKey = rsaJWK.toRSAPrivateKey();
        RSAPublicKey rsaPublicKey = rsaJWK.toRSAPublicKey();
        String algorithm = SignatureAlgorithm.RS256.getValue();
        JWTBuilder jwtBuilder  = new DefaultJWTBuilder(rsaPrivateKey, algorithm, rsaJWK.getKeyID());
        JWTParser jwtParser = new DefaultJWTParser(rsaPublicKey);
        assertJwt(jwtBuilder, jwtParser, algorithm);
    }

    @Test(expected = SignatureException.class)
    public void shouldNotParse_rsa_wrongSignature() throws Exception {
        RSAKey rsaJWK = new RSAKeyGenerator(2048)
                .keyID("123")
                .generate();
        RSAPrivateKey rsaPrivateKey = rsaJWK.toRSAPrivateKey();

        RSAKey wrongRsaJWK = new RSAKeyGenerator(2048)
                .keyID("456")
                .generate();
        RSAPublicKey wrongRsaPublicKey = wrongRsaJWK.toRSAPublicKey();

        JWTBuilder jwtBuilder  = new DefaultJWTBuilder(rsaPrivateKey, SignatureAlgorithm.RS256.getValue(), rsaJWK.getKeyID());
        JWTParser jwtParser = new DefaultJWTParser(wrongRsaPublicKey);

        JWT jwt = new JWT();
        jwt.setIss("https://gravitee.io");
        jwt.setSub("alice");
        jwt.setIat(Instant.now().getEpochSecond());
        jwt.setExp(Instant.now().plus(60, ChronoUnit.MINUTES).getEpochSecond());
        String signedJWT = jwtBuilder.sign(jwt);

        jwtParser.parse(signedJWT);
    }

    @Test(expected = PrematureJWTException.class)
    public void shouldNotParse_rsa_prematureToken() throws Exception {
        RSAKey rsaJWK = new RSAKeyGenerator(2048)
                .keyID("123")
                .generate();
        RSAPrivateKey rsaPrivateKey = rsaJWK.toRSAPrivateKey();
        RSAPublicKey rsaPublicKey = rsaJWK.toRSAPublicKey();
        JWTBuilder jwtBuilder  = new DefaultJWTBuilder(rsaPrivateKey, SignatureAlgorithm.RS256.getValue(), rsaJWK.getKeyID());
        JWTParser jwtParser = new DefaultJWTParser(rsaPublicKey);

        JWT jwt = new JWT();
        jwt.setIss("https://gravitee.io");
        jwt.setSub("alice");
        jwt.setNbf(Instant.now().plus(60, ChronoUnit.MINUTES).getEpochSecond());
        jwt.setExp(Instant.now().plus(60, ChronoUnit.MINUTES).getEpochSecond());
        String signedJWT = jwtBuilder.sign(jwt);

        jwtParser.parse(signedJWT);
    }

    @Test(expected = ExpiredJWTException.class)
    public void shouldNotParse_rsa_expiredToken() throws Exception {
        RSAKey rsaJWK = new RSAKeyGenerator(2048)
                .keyID("123")
                .generate();
        RSAPrivateKey rsaPrivateKey = rsaJWK.toRSAPrivateKey();
        RSAPublicKey rsaPublicKey = rsaJWK.toRSAPublicKey();
        JWTBuilder jwtBuilder  = new DefaultJWTBuilder(rsaPrivateKey, SignatureAlgorithm.RS256.getValue(), rsaJWK.getKeyID());
        JWTParser jwtParser = new DefaultJWTParser(rsaPublicKey);

        JWT jwt = new JWT();
        jwt.setIss("https://gravitee.io");
        jwt.setSub("alice");
        jwt.setIat(Instant.now().getEpochSecond());
        jwt.setExp(Instant.now().minus(60, ChronoUnit.MINUTES).getEpochSecond());
        String signedJWT = jwtBuilder.sign(jwt);

        jwtParser.parse(signedJWT);
    }

    @Test(expected = MalformedJWTException.class)
    public void shouldNotParse_rsa_malformedToken() throws Exception {
        RSAKey rsaJWK = new RSAKeyGenerator(2048)
                .keyID("123")
                .generate();
        RSAPublicKey rsaPublicKey = rsaJWK.toRSAPublicKey();
        JWTParser jwtParser = new DefaultJWTParser(rsaPublicKey);

        jwtParser.parse("malformed-token");
    }

    @Test
    public void shouldParse_hmac() throws Exception {
        SecureRandom random = new SecureRandom();
        byte[] sharedSecret = new byte[32];
        random.nextBytes(sharedSecret);
        SecretKeySpec secretKeySpec = new SecretKeySpec(sharedSecret, SignatureAlgorithm.HS256.getJcaName());
        JWTBuilder jwtBuilder  = new DefaultJWTBuilder(secretKeySpec, SignatureAlgorithm.HS256.getValue(), "123");
        JWTParser jwtParser = new DefaultJWTParser(secretKeySpec);

        JWT jwt = new JWT();
        jwt.setIss("https://gravitee.io");
        jwt.setSub("alice");
        jwt.setIat(Instant.now().getEpochSecond());
        jwt.setExp(Instant.now().plus(60, ChronoUnit.MINUTES).getEpochSecond());
        String signedJWT = jwtBuilder.sign(jwt);

        // check header
        String header = new String(Base64.getDecoder().decode(signedJWT.split("\\.")[0]), UTF_8);
        assertTrue(header.equals("{\"kid\":\"123\",\"typ\":\"JWT\",\"alg\":\"HS256\"}"));

        JWT parsedJWT = jwtParser.parse(signedJWT);
        assertEquals("alice", parsedJWT.getSub());
        assertEquals("https://gravitee.io", parsedJWT.getIss());
        assertTrue(new Date().before(new Date(parsedJWT.getExp() * 1000)));
    }

    @Test(expected = SignatureException.class)
    public void shouldNotParse_hmac_wrongSignature() throws Exception {
        SecureRandom random = new SecureRandom();
        byte[] sharedSecret = new byte[32];
        random.nextBytes(sharedSecret);
        SecretKeySpec secretKeySpec = new SecretKeySpec(sharedSecret, SignatureAlgorithm.HS256.getJcaName());

        SecureRandom wrongRandom = new SecureRandom();
        byte[] wrongSharedSecret = new byte[32];
        wrongRandom.nextBytes(wrongSharedSecret);
        SecretKeySpec wrongSecretKeySpec = new SecretKeySpec(wrongSharedSecret, SignatureAlgorithm.HS256.getJcaName());

        JWTBuilder jwtBuilder  = new DefaultJWTBuilder(secretKeySpec, SignatureAlgorithm.HS256.getValue(), "123");
        JWTParser jwtParser = new DefaultJWTParser(wrongSecretKeySpec);

        JWT jwt = new JWT();
        jwt.setIss("https://gravitee.io");
        jwt.setSub("alice");
        jwt.setIat(Instant.now().getEpochSecond());
        jwt.setExp(Instant.now().plus(60, ChronoUnit.MINUTES).getEpochSecond());
        String signedJWT = jwtBuilder.sign(jwt);

        jwtParser.parse(signedJWT);
    }

    @Test(expected = PrematureJWTException.class)
    public void shouldNotParse_hmac_prematureToken() throws Exception {
        SecureRandom random = new SecureRandom();
        byte[] sharedSecret = new byte[32];
        random.nextBytes(sharedSecret);
        SecretKeySpec secretKeySpec = new SecretKeySpec(sharedSecret, SignatureAlgorithm.HS256.getJcaName());
        JWTBuilder jwtBuilder  = new DefaultJWTBuilder(secretKeySpec, SignatureAlgorithm.HS256.getValue(), "123");
        JWTParser jwtParser = new DefaultJWTParser(secretKeySpec);

        JWT jwt = new JWT();
        jwt.setIss("https://gravitee.io");
        jwt.setSub("alice");
        jwt.setNbf(Instant.now().plus(60, ChronoUnit.MINUTES).getEpochSecond());
        jwt.setExp(Instant.now().plus(60, ChronoUnit.MINUTES).getEpochSecond());
        String signedJWT = jwtBuilder.sign(jwt);

        jwtParser.parse(signedJWT);
    }

    @Test(expected = ExpiredJWTException.class)
    public void shouldNotParse_hmac_expiredToken() throws Exception {
        SecureRandom random = new SecureRandom();
        byte[] sharedSecret = new byte[32];
        random.nextBytes(sharedSecret);
        SecretKeySpec secretKeySpec = new SecretKeySpec(sharedSecret, SignatureAlgorithm.HS256.getJcaName());
        JWTBuilder jwtBuilder  = new DefaultJWTBuilder(secretKeySpec, SignatureAlgorithm.HS256.getValue(), "123");
        JWTParser jwtParser = new DefaultJWTParser(secretKeySpec);

        JWT jwt = new JWT();
        jwt.setIss("https://gravitee.io");
        jwt.setSub("alice");
        jwt.setIat(Instant.now().getEpochSecond());
        jwt.setExp(Instant.now().minus(60, ChronoUnit.MINUTES).getEpochSecond());
        String signedJWT = jwtBuilder.sign(jwt);

        jwtParser.parse(signedJWT);
    }

    @Test(expected = MalformedJWTException.class)
    public void shouldNotParse_hmac_malformedToken() throws Exception {
        SecureRandom random = new SecureRandom();
        byte[] sharedSecret = new byte[32];
        random.nextBytes(sharedSecret);
        SecretKeySpec secretKeySpec = new SecretKeySpec(sharedSecret, SignatureAlgorithm.HS256.getJcaName());
        JWTParser jwtParser = new DefaultJWTParser(secretKeySpec);

        jwtParser.parse("malformed-token");
    }

    @Test
    public void shouldParse_withEmptyHttpResponse() throws Exception {
        RSAKey rsaJWK = new RSAKeyGenerator(2048)
                .keyID("123")
                .generate();
        RSAPrivateKey rsaPrivateKey = rsaJWK.toRSAPrivateKey();
        RSAPublicKey rsaPublicKey = rsaJWK.toRSAPublicKey();
        String algorithm = SignatureAlgorithm.RS256.getValue();
        JWTBuilder jwtBuilder  = new DefaultJWTBuilder(rsaPrivateKey, algorithm, rsaJWK.getKeyID());
        JWTParser jwtParser = new DefaultJWTParser(rsaPublicKey);
        JWT jwt = new JWT();
        jwt.setIss("https://gravitee.io");
        jwt.setSub("alice");
        jwt.setIat(Instant.now().plus(24 * 365, ChronoUnit.HOURS).getEpochSecond());
        jwt.setExp(Instant.now().plus(24 * 365 * 2, ChronoUnit.HOURS).getEpochSecond());
        Http1xServerResponse response = Mockito.mock(Http1xServerResponse.class);
        response.addCookie(new CookieImpl("test cookie","coockie"));
        jwt.put("test",response);
        jwt.put("test2","test claim");
        String signedJWT = jwtBuilder.sign(jwt);
        JWT parsedJWT = jwtParser.parse(signedJWT);
        assertFalse(parsedJWT.containsKey("test"));
        assertTrue(parsedJWT.containsKey("test2"));

    }

    private void assertJwt(JWTBuilder jwtBuilder, JWTParser jwtParser, String algorithm) {
        JWT jwt = new JWT();
        jwt.setIss("https://gravitee.io");
        jwt.setSub("alice");
        jwt.setIat(Instant.now().plus(24 * 365, ChronoUnit.HOURS).getEpochSecond());
        jwt.setExp(Instant.now().plus(24 * 365 * 2, ChronoUnit.HOURS).getEpochSecond());
        String signedJWT = jwtBuilder.sign(jwt);

        // check header
        String actualHeader = new String(Base64.getDecoder().decode(signedJWT.split("\\.")[0]), UTF_8);
        String expectedHeader = String.format("{\"kid\":\"123\",\"typ\":\"JWT\",\"alg\":\"%s\"}", algorithm);
        assertEquals(expectedHeader, actualHeader);

        JWT parsedJWT = jwtParser.parse(signedJWT);
        assertEquals("alice", parsedJWT.getSub());
        assertEquals("https://gravitee.io", parsedJWT.getIss());
        assertTrue(new Date().before(new Date(parsedJWT.getExp() * 1000)));
    }
}
