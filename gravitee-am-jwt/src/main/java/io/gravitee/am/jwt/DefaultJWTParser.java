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

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.crypto.bc.BouncyCastleProviderSingleton;
import com.nimbusds.jose.jca.JCASupport;
import com.nimbusds.jwt.SignedJWT;
import io.gravitee.am.common.exception.jwt.ExpiredJWTException;
import io.gravitee.am.common.exception.jwt.MalformedJWTException;
import io.gravitee.am.common.exception.jwt.PrematureJWTException;
import io.gravitee.am.common.exception.jwt.SignatureException;
import io.gravitee.am.common.jwt.JWT;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKey;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.PublicKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.text.ParseException;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class DefaultJWTParser implements JWTParser {

    private static final Logger logger = LoggerFactory.getLogger(DefaultJWTParser.class);
    public static final String NO_MATCHING_JWT_PARSER_FOR_KEY = "No matching JWT parser for key : ";
    private JWSVerifier verifier;

    public DefaultJWTParser(final Key key) throws InvalidKeyException {
        if (key instanceof PublicKey) {
            initialiseVerifier((PublicKey) key);
            // if JCA doesn't support at least the PS256 algorithm (jdk <= 8)
            // add BouncyCastle JCA provider
            if (!JCASupport.isSupported(JWSAlgorithm.PS256)) {
                verifier.getJCAContext().setProvider(BouncyCastleProviderSingleton.getInstance());
            }
        } else if (key instanceof SecretKey) {
            try {
                this.verifier = new MACVerifier((SecretKey) key);
            } catch (JOSEException e) {
                throw new InvalidKeyException(e);
            }
        } else {
            throw new InvalidKeyException(NO_MATCHING_JWT_PARSER_FOR_KEY + key);
        }
    }

    private void initialiseVerifier(PublicKey key) throws InvalidKeyException {
        if (key instanceof RSAPublicKey){
            verifier = new RSASSAVerifier((RSAPublicKey) key);
        } else if (key instanceof ECPublicKey) {
            try {
                verifier = new ECDSAVerifier((ECPublicKey) key);
            } catch (JOSEException e) {
                throw new InvalidKeyException(e);
            }
        } else {
            throw new InvalidKeyException(NO_MATCHING_JWT_PARSER_FOR_KEY + key);
        }
    }

    @Override
    public JWT parse(String payload) {
        try {
            // verify format
            SignedJWT signedJWT = SignedJWT.parse(payload);
            // verify signature
            boolean verified = signedJWT.verify(verifier);
            if (!verified) {
                throw new JOSEException("The signature was not verified");
            }
            Map<String, Object> claims = signedJWT
                    .getPayload()
                    .toJSONObject()
                    .entrySet()
                    .stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            JWT jwt = new JWT(claims);

            // verify exp and nbf values
            // https://tools.ietf.org/html/draft-ietf-oauth-json-web-token-30#section-4.1.4
            // token MUST NOT be accepted on or after any specified exp time
            Instant now = Instant.now();
            long allowedClockSkewMillis = 0;
            evaluateExp(jwt.getExp(), now, allowedClockSkewMillis);
            // https://tools.ietf.org/html/draft-ietf-oauth-json-web-token-30#section-4.1.5
            // token MUST NOT be accepted before any specified nbf time
            evaluateNbf(jwt.getNbf(), now, allowedClockSkewMillis);
            return jwt;
        } catch (ParseException ex) {
            logger.debug("The following JWT token : {} is malformed", payload);
            throw new MalformedJWTException("Token is malformed", ex);
        } catch (ExpiredJWTException ex) {
            logger.debug("The following JWT token : {} is expired", payload);
            throw new ExpiredJWTException("Token is expired", ex);
        } catch (PrematureJWTException ex) {
            logger.debug("The following JWT token : {} must not be accepted (nbf)", payload);
            throw new PrematureJWTException("Token must not be accepted (nbf)", ex);
        } catch (JOSEException ex) {
            logger.debug("Verifying JWT token signature : {} has failed", payload);
            throw new SignatureException("Token's signature is invalid", ex);
        } catch (Exception ex) {
            logger.error("An error occurs while parsing JWT token : {}", payload, ex);
            throw ex;
        }
    }

    /**
     * Throw {@link ExpiredJWTException} if now is before nbf
     *
     * @param nbf the not before time
     * @param now the current time
     */
    public static void evaluateNbf(long nbf, Instant now, long clockSkew) {
        if (nbf > 0) {
            Instant nbfInstant = Instant.ofEpochSecond(nbf);
            if (now.isBefore(nbfInstant)) {
                long differenceMillis = nbfInstant.toEpochMilli() - now.toEpochMilli();
                String msg = "JWT must not be accepted before " + nbfInstant + ". Current time: " + now +
                        ", a difference of " +
                        differenceMillis + " milliseconds.  Allowed clock skew: " +
                        clockSkew + " milliseconds.";
                throw new PrematureJWTException(msg);
            }
        }
    }

    /**
     * Throw {@link ExpiredJWTException} if exp is after now
     *
     * @param exp the expiration time of the JWT
     * @param now the current time
     */
    public static void evaluateExp(long exp, Instant now, long clockSkew) {
        if (exp > 0) {
            Instant expInstant = Instant.ofEpochSecond(exp);
            if (now.isAfter(expInstant)) {
                long differenceMillis = now.toEpochMilli() - expInstant.toEpochMilli();
                String msg = "JWT expired at " + expInstant + ". Current time: " + now + ", a difference of " +
                        differenceMillis + " milliseconds.  Allowed clock skew: " +
                        clockSkew + " milliseconds.";
                throw new ExpiredJWTException(msg);
            }
        }
    }
}
