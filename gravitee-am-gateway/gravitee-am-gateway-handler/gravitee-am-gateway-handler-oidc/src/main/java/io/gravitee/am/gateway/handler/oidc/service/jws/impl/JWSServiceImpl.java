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
package io.gravitee.am.gateway.handler.oidc.service.jws.impl;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.crypto.Ed25519Verifier;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.OctetKeyPair;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.util.Base64URL;
import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.SignedJWT;
import io.gravitee.am.gateway.handler.oidc.service.jws.JWSService;
import io.gravitee.am.model.jose.ECKey;
import io.gravitee.am.model.jose.JWK;
import io.gravitee.am.model.jose.KeyType;
import io.gravitee.am.model.jose.OCTKey;
import io.gravitee.am.model.jose.OKPKey;
import io.gravitee.am.model.jose.RSAKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.security.AlgorithmParameters;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.InvalidParameterSpecException;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;

/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
public class JWSServiceImpl implements JWSService {

    private static final Logger LOGGER = LoggerFactory.getLogger(JWSServiceImpl.class);


    @Override
    public boolean isValidSignature(JWT jwt, JWK jwk) {
        try {
            SignedJWT signedJwt = (SignedJWT)jwt;
            return signedJwt.verify(this.verifier(jwk));
        } catch (ClassCastException | JOSEException ex) {
            LOGGER.error(ex.getMessage(),ex);
            return false;
        }
    }

    @Override
    public JWSVerifier verifier(JWK jwk) {
        try {
            switch (KeyType.parse(jwk.getKty())) {
                case RSA:
                    return from((RSAKey) jwk);
                case EC:
                    return from((ECKey) jwk);
                case OCT:
                    return from((OCTKey) jwk);
                case OKP:
                    return from((OKPKey) jwk);
                default:
                    throw new IllegalArgumentException("Signature is using and unknown/not managed algorithm");
            }
        }catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Signature is using and unknown/not managed algorithm");
        }
    }

    private JWSVerifier from(RSAKey rsaKey) {
        try {
            byte[] modulus = Base64.getUrlDecoder().decode(rsaKey.getN());
            byte[] exponent = Base64.getUrlDecoder().decode(rsaKey.getE());
            RSAPublicKeySpec spec = new RSAPublicKeySpec(new BigInteger(1,modulus), new BigInteger(1,exponent));
            KeyFactory factory = KeyFactory.getInstance("RSA");
            return new RSASSAVerifier((RSAPublicKey) factory.generatePublic(spec));
        }
        catch (NoSuchAlgorithmException | InvalidKeySpecException ex) {
            LOGGER.error("Unable to build Signature Verifier from RSA key",ex);
            throw new IllegalArgumentException("Signature is using and unknown/not managed key");
        }
    }

    private JWSVerifier from(ECKey ecKey) {
        try {
            Curve curve = Curve.parse(ecKey.getCrv());
            if(curve.getStdName()==null) {
                throw new IllegalArgumentException("Unknown EC Curve: "+ecKey.getCrv());
            }
            AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
            parameters.init(new ECGenParameterSpec(curve.getStdName()));
            ECParameterSpec ecParameters = parameters.getParameterSpec(ECParameterSpec.class);

            byte[] x = Base64.getUrlDecoder().decode(ecKey.getX());
            byte[] y = Base64.getUrlDecoder().decode(ecKey.getY());
            ECPoint ecPoint = new ECPoint(new BigInteger(1,x), new BigInteger(1,y));

            ECPublicKeySpec ecPublicKeySpec = new ECPublicKeySpec(ecPoint, ecParameters);
            ECPublicKey ecPublicKey = (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(ecPublicKeySpec);
            return new ECDSAVerifier(ecPublicKey);
        }
        catch (NoSuchAlgorithmException | InvalidParameterSpecException | InvalidKeySpecException | JOSEException ex) {
            LOGGER.error("Unable to build Verifier from Elliptic Curve (EC) key",ex);
            throw new IllegalArgumentException("Signature is using and unknown/not managed key");
        }
    }

    private JWSVerifier from(OCTKey octKey) {
        try {
            OctetSequenceKey jwk = new OctetSequenceKey.Builder(new Base64URL(octKey.getK())).build();
            return new MACVerifier(jwk);
        }
        catch (JOSEException ex) {
            LOGGER.error("Unable to build Verifier from Edwards Curve (OKP) key",ex);
            throw new IllegalArgumentException("Signature is using and unknown/not managed key");
        }
    }

    private JWSVerifier from(OKPKey okpKey) {
        try {
            Curve curve = Curve.parse(okpKey.getCrv());
            if(curve.getStdName()==null) {
                throw new IllegalArgumentException("Unknown OKP Curve: "+okpKey.getCrv());
            }
            OctetKeyPair jwk = new OctetKeyPair.Builder(curve,new Base64URL(okpKey.getX())).build();
            return new Ed25519Verifier(jwk);
        }
        catch (JOSEException ex) {
            LOGGER.error("Unable to build Verifier from Message Authentication Code (MAC) key",ex);
            throw new IllegalArgumentException("Signature is using and unknown/not managed key");
        }
    }
}
