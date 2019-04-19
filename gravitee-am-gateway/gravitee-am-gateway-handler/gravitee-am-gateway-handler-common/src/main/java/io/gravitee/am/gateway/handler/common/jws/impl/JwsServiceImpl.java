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
package io.gravitee.am.gateway.handler.common.jws.impl;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.SignedJWT;
import io.gravitee.am.gateway.handler.common.jws.JwsService;
import io.gravitee.am.model.jose.*;
import io.gravitee.am.service.exception.NotImplementedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.security.AlgorithmParameters;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.*;
import java.util.Base64;
import java.util.Optional;

/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
public class JwsServiceImpl implements JwsService {

    private static final Logger LOGGER = LoggerFactory.getLogger(JwsServiceImpl.class);


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
            switch (KeyType.valueOf(jwk.getKty())) {
                case RSA:
                    return from((RSAKey) jwk);
                case EC:
                    return from((ECKey) jwk);
                case OCT:
                    throw new NotImplementedException("JWK Key Type:"+KeyType.OCT.getKeyType());
                case OKP:
                    throw new NotImplementedException("JWK Key Type:"+KeyType.OKP.getKeyType());
                default:
                    throw new IllegalArgumentException("Assertion is using and unknown/not managed algorithm");
            }
        }catch (IllegalArgumentException | NotImplementedException ex) {
            throw new IllegalArgumentException("Assertion is using and unknown/not managed algorithm: "+ex.getMessage());
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
            LOGGER.error(ex.getMessage(),ex);
            throw new IllegalArgumentException("Assertion is using and unknown/not managed key");
        }
    }

    private JWSVerifier from(ECKey ecKey) {
        try {
            Optional<Curve> curve = Curve.getByName(ecKey.getCrv());
            if(!curve.isPresent()) {
                throw new IllegalArgumentException("Unknown EC Curve: "+ecKey.getCrv());
            }
            AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
            parameters.init(new ECGenParameterSpec(curve.get().getStdName()));
            ECParameterSpec ecParameters = parameters.getParameterSpec(ECParameterSpec.class);

            byte[] x = Base64.getUrlDecoder().decode(ecKey.getX());
            byte[] y = Base64.getUrlDecoder().decode(ecKey.getY());
            ECPoint ecPoint = new ECPoint(new BigInteger(1,x), new BigInteger(1,y));

            ECPublicKeySpec ecPublicKeySpec = new ECPublicKeySpec(ecPoint, ecParameters);
            ECPublicKey ecPublicKey = (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(ecPublicKeySpec);
            return new ECDSAVerifier(ecPublicKey);
        }
        catch (NoSuchAlgorithmException | InvalidParameterSpecException | InvalidKeySpecException | JOSEException ex) {
            LOGGER.error(ex.getMessage(),ex);
            throw new IllegalArgumentException("Assertion is using and unknown/not managed key");
        }
    }
}
