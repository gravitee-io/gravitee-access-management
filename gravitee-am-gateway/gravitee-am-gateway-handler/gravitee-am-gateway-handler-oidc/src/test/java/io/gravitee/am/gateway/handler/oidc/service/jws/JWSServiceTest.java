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
package io.gravitee.am.gateway.handler.oidc.service.jws;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.Ed25519Signer;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.OctetKeyPair;
import com.nimbusds.jose.jwk.gen.OctetKeyPairGenerator;
import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import io.gravitee.am.gateway.handler.oidc.service.jws.impl.JWSServiceImpl;
import io.gravitee.am.model.jose.ECKey;
import io.gravitee.am.model.jose.JWK;
import io.gravitee.am.model.jose.OCTKey;
import io.gravitee.am.model.jose.OKPKey;
import io.gravitee.am.model.jose.RSAKey;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.security.InvalidAlgorithmParameterException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;

import static org.junit.Assert.*;

/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class JWSServiceTest {

    private static final String KID = "keyIdentifier";

    private JWSService jwsService = new JWSServiceImpl();

    @Test
    public void testisValidSignature_PlainJwt() {
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
        kpg.initialize(2048);
        KeyPair rsaKey = kpg.generateKeyPair();

        RSAPublicKey publicKey = (RSAPublicKey) rsaKey.getPublic();
        RSAKey key = new RSAKey();
        key.setKty("RSA");
        key.setKid(KID);
        key.setE(Base64.getUrlEncoder().encodeToString(publicKey.getPublicExponent().toByteArray()));
        key.setN(Base64.getUrlEncoder().encodeToString(publicKey.getModulus().toByteArray()));

        //Sign JWT with RSA algorithm
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
    public void testValidSignature_EC() throws NoSuchAlgorithmException, InvalidAlgorithmParameterException, JOSEException {
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

        //Sign JWT with Elliptic Curve algorithm
        SignedJWT signedJWT = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.ES512).keyID(KID).build(),
                new JWTClaimsSet.Builder()
                        .expirationTime(Date.from(Instant.now().plus(1, ChronoUnit.DAYS)))
                        .build()
        );
        signedJWT.sign(new ECDSASigner((ECPrivateKey) ecKey.getPrivate()));

        assertTrue("Should be ok",jwsService.isValidSignature(signedJWT, key));
    }

    @Test
    public void testValidSignature_OKP() throws JOSEException{
        //Generate OKP key
        OctetKeyPair okp = new OctetKeyPairGenerator(Curve.Ed25519).generate();
        OKPKey key = new OKPKey();
        key.setKty("OKP");
        key.setKid(KID);
        key.setCrv(okp.getCurve().getStdName());
        key.setX(okp.getX().toString());

        //Sign JWT with Edward Curve algorithm
        SignedJWT signedJWT = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.EdDSA).keyID(KID).build(),
                new JWTClaimsSet.Builder()
                        .expirationTime(Date.from(Instant.now().plus(1, ChronoUnit.DAYS)))
                        .build()
        );
        signedJWT.sign(new Ed25519Signer(okp));

        assertTrue("Should be ok",jwsService.isValidSignature(signedJWT, key));
    }

    @Test
    public void testValidSignature_OCT() throws JOSEException{
        // Generate random 256-bit (32-byte) shared secret
        SecureRandom random = new SecureRandom();
        byte[] sharedSecret = new byte[32];
        random.nextBytes(sharedSecret);

        OCTKey key = new OCTKey();
        key.setKty("oct");
        key.setKid(KID);
        key.setK(Base64.getEncoder().encodeToString(sharedSecret));

        //Sign JWT with MAC algorithm
        SignedJWT signedJWT = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.HS256).keyID(KID).build(),
                new JWTClaimsSet.Builder()
                        .expirationTime(Date.from(Instant.now().plus(1, ChronoUnit.DAYS)))
                        .build()
        );
        signedJWT.sign(new MACSigner(sharedSecret));

        assertTrue("Should be ok",jwsService.isValidSignature(signedJWT, key));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testVerifier_UnknownAlgorithm() {
        JWK jwk = new JWK() {
            @Override
            public String getKty() {
                return "unknown";
            }
        };

        jwsService.verifier(jwk);//Should throw InvalidClientException due to unknown algorithm
    }

    @Test(expected = IllegalArgumentException.class)
    public void testVerifier_RSA_invalid() {
        RSAKey key = new RSAKey();
        key.setKty("RSA");
        key.setKid(KID);
        key.setE(Base64.getUrlEncoder().encodeToString("exponent".getBytes()));
        key.setN(Base64.getUrlEncoder().encodeToString("modulus".getBytes()));

        jwsService.verifier(key);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testVerifier_EC_unknownCurve() {
        ECKey key = new ECKey();
        key.setKty("EC");
        key.setCrv("unknown");

        jwsService.verifier(key);//Should throw InvalidClientException due to unknown curve
    }

    @Test(expected = IllegalArgumentException.class)
    public void testVerifier_EC_invalid() {
        ECKey key = new ECKey();
        key.setKty("EC");
        key.setKid(KID);
        key.setCrv("Ed25519");//Not Elliptic Curve
        key.setX("R4JmPwezbzLuyGkonWIkezzplUfed5b6F5PL4j0zdf8");
        key.setY("QQRGKwRV9jHSlHjUhOQ0FqdQEddFBPCHZXpoFjvGmcY");

        jwsService.verifier(key);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testVerifier_OKP_unknownCurve() {
        OKPKey key = new OKPKey();
        key.setKty("OKP");
        key.setCrv("unknown");

        jwsService.verifier(key);//Should throw InvalidClientException due to unknown curve
    }

    @Test(expected = IllegalArgumentException.class)
    public void testVerifier_OKP_invalid() {
        OKPKey key = new OKPKey();
        key.setKty("OKP");
        key.setKid(KID);
        key.setCrv("X25519");//Not Signature curve
        key.setX("vBNW8f19leF79U4U6NrDDQaK_i5kL0iMKghB39AUT2I");

        jwsService.verifier(key);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testVerifier_OCT_invalid() {
        OCTKey key = new OCTKey();
        key.setKty("oct");
        key.setKid(KID);
        key.setK("too_short");

        jwsService.verifier(key);
    }
}
