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
package io.gravitee.am.gateway.handler.jws;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import io.gravitee.am.gateway.handler.jws.impl.JwsServiceImpl;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidClientException;
import io.gravitee.am.model.jose.Curve;
import io.gravitee.am.model.jose.ECKey;
import io.gravitee.am.model.jose.JWK;
import io.gravitee.am.model.jose.RSAKey;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import java.math.BigInteger;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class JwsServiceTest {

    private static final String KID = "keyIdentifier";

    private JwsService jwsService = new JwsServiceImpl();

    @Test
    public void testisValidSignature_PlainJwt() throws NoSuchAlgorithmException {
        JWT assertion = new PlainJWT(
                new JWTClaimsSet.Builder()
                        .issuer("iss")
                        .subject("client")
                        .expirationTime(Date.from(Instant.now().plus(1, ChronoUnit.DAYS)))
                        .build()
        );

        assertFalse("Should return false due to ClassCastException",jwsService.isValidSignature(assertion, null));
    }

    @Test
    public void testValidSignature_RSA() throws NoSuchAlgorithmException, JOSEException {
        //Generate RSA key
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(512);
        KeyPair rsaKey = kpg.generateKeyPair();

        RSAPublicKey publicKey = (RSAPublicKey) rsaKey.getPublic();
        RSAKey key = new RSAKey();
        key.setKty("RSA");
        key.setKid(KID);
        key.setE(Base64.getUrlEncoder().encodeToString(publicKey.getPublicExponent().toByteArray()));
        key.setN(Base64.getUrlEncoder().encodeToString(publicKey.getModulus().toByteArray()));

        //Signe JWT with RSA algorithm
        SignedJWT signedJWT = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KID).build(),
                new JWTClaimsSet.Builder()
                        .expirationTime(Date.from(Instant.now().plus(1, ChronoUnit.DAYS)))
                        .build()
        );
        signedJWT.sign(new RSASSASigner((RSAPrivateKey) rsaKey.getPrivate()));

        assertTrue("Should be ok",jwsService.isValidSignature(signedJWT, key));
    }

    @Test
    public void testValidSignature_EC() throws NoSuchAlgorithmException, InvalidAlgorithmParameterException, InvalidKeySpecException, JOSEException {
        //Generate EC key
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        ECGenParameterSpec gps = new ECGenParameterSpec (Curve.P_521.getStdName());
        kpg.initialize(gps);
        KeyPair ecKey = kpg.generateKeyPair();

        ECPublicKey ecPublicKey  = (ECPublicKey)ecKey.getPublic();
        ECKey key = new ECKey();
        key.setKty("EC");
        key.setKid(KID);
        key.setCrv(Curve.P_521.getName());
        key.setX(Base64.getUrlEncoder().encodeToString(ecPublicKey.getW().getAffineX().toByteArray()));
        key.setY(Base64.getUrlEncoder().encodeToString(ecPublicKey.getW().getAffineY().toByteArray()));

        //Signe JWT with EC algorithm
        SignedJWT signedJWT = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.ES512).keyID(KID).build(),
                new JWTClaimsSet.Builder()
                        .expirationTime(Date.from(Instant.now().plus(1, ChronoUnit.DAYS)))
                        .build()
        );
        signedJWT.sign(new ECDSASigner((ECPrivateKey) ecKey.getPrivate()));

        assertTrue("Should be ok",jwsService.isValidSignature(signedJWT, key));
    }

    @Test(expected = InvalidClientException.class)
    public void testVerifier_UnknownAlgorithm() throws NoSuchAlgorithmException {
        JWK jwk = new JWK() {
            @Override
            public String getKty() {
                return "unknown";
            }
        };

        jwsService.verifier(jwk);//Should throw InvalidClientException due to unknown algorithm
    }

    @Test(expected = InvalidClientException.class)
    public void testVerifier_NotManagedAlgorithm() throws NoSuchAlgorithmException {
        JWK jwk = new JWK() {
            @Override
            public String getKty() {
                return "OKP";
            }
        };

        jwsService.verifier(jwk);//Should throw InvalidClientException due to NotImplementedException
    }

    @Test(expected = InvalidClientException.class)
    public void testVerifier_unknownEcCurve() throws NoSuchAlgorithmException {
        ECKey key = new ECKey();
        key.setKty("EC");
        key.setCrv("unknown");

        jwsService.verifier(key);//Should throw InvalidClientException due to unknown curve
    }
}
