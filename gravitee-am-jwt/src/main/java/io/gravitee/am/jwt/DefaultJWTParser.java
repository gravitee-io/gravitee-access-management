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
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
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
    private final JWSVerifier verifier;
    private long allowedClockSkewMillis = 0;

    public DefaultJWTParser(final Key key) throws InvalidKeyException {
        if (key instanceof RSAPublicKey) {
            this.verifier = new RSASSAVerifier((RSAPublicKey) key);
        } else if (key instanceof SecretKey) {
            try {
                this.verifier = new MACVerifier((SecretKey) key);
            } catch (JOSEException e) {
                throw new InvalidKeyException(e);
            }
        } else {
            throw new InvalidKeyException("No matching JWT parser for key : " + key);
        }
    }

    @Override
    public JWT parse(String payload) {
        try {
            // verify format
            SignedJWT signedJWT = SignedJWT.parse(payload);
            // verify signature
            signedJWT.verify(verifier);
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
            if (jwt.getExp() > 0) {
                Instant exp = Instant.ofEpochSecond(jwt.getExp());
                if (now.isAfter(exp)) {
                    long differenceMillis = now.toEpochMilli() - exp.toEpochMilli();
                    String msg = "JWT expired at " + exp + ". Current time: " + now + ", a difference of " +
                            differenceMillis + " milliseconds.  Allowed clock skew: " +
                            this.allowedClockSkewMillis + " milliseconds.";
                    throw new ExpiredJWTException(msg);
                }
            }
            // https://tools.ietf.org/html/draft-ietf-oauth-json-web-token-30#section-4.1.5
            // token MUST NOT be accepted before any specified nbf time
            if (jwt.getNbf() > 0) {
                Instant nbf = Instant.ofEpochSecond(jwt.getNbf());
                if (now.isBefore(nbf)) {
                    long differenceMillis = nbf.toEpochMilli() - now.toEpochMilli();
                    String msg = "JWT must not be accepted before " + nbf + ". Current time: " + now +
                            ", a difference of " +
                            differenceMillis + " milliseconds.  Allowed clock skew: " +
                            this.allowedClockSkewMillis + " milliseconds.";
                    throw new PrematureJWTException(msg);
                }
            }
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
}
