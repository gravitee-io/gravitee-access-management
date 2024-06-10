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
package io.gravitee.am.gateway.handler.oidc.service.jwk.converter;

import com.nimbusds.jose.Algorithm;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.KeyOperation;
import com.nimbusds.jose.jwk.OctetKeyPair;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.util.Base64;
import com.nimbusds.jose.util.Base64URL;
import io.gravitee.am.common.exception.oauth2.ServerErrorException;
import io.gravitee.am.model.jose.*;
import io.gravitee.am.service.exception.InvalidClientMetadataException;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.net.URI;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.stream.Collectors;

/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class JWKConverter {

    /*********************************
     * FROM GRAVITEE MODEL TO NIMBUS *
     *********************************/

    public static com.nimbusds.jose.jwk.RSAKey convert(io.gravitee.am.model.jose.RSAKey rsaKey) {
        try {
            if (!rsaKey.isPrivate()) {
                //Base64URL n, Base64URL e, KeyUse use, Set<KeyOperation> ops, Algorithm alg, String kid, URI x5u, Base64URL x5t, Base64URL x5t256, List<Base64> x5c, KeyStore ks
                return new com.nimbusds.jose.jwk.RSAKey(
                        new Base64URL(rsaKey.getN()),
                        new Base64URL(rsaKey.getE()),
                        rsaKey.getUse() != null ? com.nimbusds.jose.jwk.KeyUse.parse(rsaKey.getUse()) : null,
                        rsaKey.getKeyOps()!=null? KeyOperation.parse(new ArrayList<>(rsaKey.getKeyOps())):null,
                        rsaKey.getAlg()!=null?new Algorithm(rsaKey.getAlg()):null,
                        rsaKey.getKid(),
                        rsaKey.getX5u() != null ? URI.create(rsaKey.getX5u()) : null,
                        rsaKey.getX5t() != null ? new Base64URL(rsaKey.getX5t()) : null,
                        rsaKey.getX5tS256() != null ? new Base64URL(rsaKey.getX5tS256()) : null,
                        rsaKey.getX5c() != null ? rsaKey.getX5c().stream().map(Base64::encode).collect(Collectors.toList()) : null,
                        null
                );
            } else {
                return new com.nimbusds.jose.jwk.RSAKey(
                        new Base64URL(rsaKey.getN()),
                        new Base64URL(rsaKey.getE()),
                        rsaKey.getD() != null ? new Base64URL(rsaKey.getD()) : null,
                        rsaKey.getP() != null ? new Base64URL(rsaKey.getP()) : null,
                        rsaKey.getQ() != null ? new Base64URL(rsaKey.getQ()) : null,
                        rsaKey.getDp() != null ? new Base64URL(rsaKey.getDp()) : null,
                        rsaKey.getDq() != null ? new Base64URL(rsaKey.getDq()) : null,
                        rsaKey.getQi() != null ? new Base64URL(rsaKey.getQi()) : null,
                        null,
                        rsaKey.getUse() != null ? com.nimbusds.jose.jwk.KeyUse.parse(rsaKey.getUse()) : null,
                        rsaKey.getKeyOps()!=null? KeyOperation.parse(new ArrayList<>(rsaKey.getKeyOps())):null,
                        rsaKey.getAlg()!=null?new Algorithm(rsaKey.getAlg()):null,
                        rsaKey.getKid(),
                        rsaKey.getX5u() != null ? URI.create(rsaKey.getX5u()) : null,
                        rsaKey.getX5t() != null ? new Base64URL(rsaKey.getX5t()) : null,
                        rsaKey.getX5tS256() != null ? new Base64URL(rsaKey.getX5tS256()) : null,
                        rsaKey.getX5c() != null ? rsaKey.getX5c().stream().map(Base64::encode).collect(Collectors.toList()) : null);
            }
        } catch (ParseException e) {
            throw new ServerErrorException("Malformed rsa key encryption");
        }
    }

    public static com.nimbusds.jose.jwk.ECKey convert(io.gravitee.am.model.jose.ECKey ecKey) {
        try {
            if (!ecKey.isPrivate()) {
                //Curve crv, Base64URL x, Base64URL y, KeyUse use, Set<KeyOperation> ops, Algorithm alg, String kid, URI x5u, Base64URL x5t, Base64URL x5t256, List<Base64> x5c, KeyStore ks
                return new com.nimbusds.jose.jwk.ECKey(
                        Curve.parse(ecKey.getCrv()),
                        new Base64URL(ecKey.getX()),
                        new Base64URL(ecKey.getY()),
                        ecKey.getUse() != null ? com.nimbusds.jose.jwk.KeyUse.parse(ecKey.getUse()) : null,
                        ecKey.getKeyOps() != null ? KeyOperation.parse(new ArrayList<>(ecKey.getKeyOps())) : null,
                        ecKey.getAlg() != null ? new Algorithm(ecKey.getAlg()) : null,
                        ecKey.getKid(),
                        ecKey.getX5u() != null ? URI.create(ecKey.getX5u()) : null,
                        ecKey.getX5t() != null ? new Base64URL(ecKey.getX5t()) : null,
                        ecKey.getX5tS256() != null ? new Base64URL(ecKey.getX5tS256()) : null,
                        ecKey.getX5c() != null ? ecKey.getX5c().stream().map(Base64::encode).collect(Collectors.toList()) : null,
                        null
                );
            } else {
                return new com.nimbusds.jose.jwk.ECKey(
                        Curve.parse(ecKey.getCrv()),
                        new Base64URL(ecKey.getX()),
                        new Base64URL(ecKey.getY()),
                        new Base64URL(ecKey.getD()),
                        ecKey.getUse() != null ? com.nimbusds.jose.jwk.KeyUse.parse(ecKey.getUse()) : null,
                        ecKey.getKeyOps() != null ? KeyOperation.parse(new ArrayList<>(ecKey.getKeyOps())) : null,
                        ecKey.getAlg() != null ? new Algorithm(ecKey.getAlg()) : null,
                        ecKey.getKid(),
                        ecKey.getX5u() != null ? URI.create(ecKey.getX5u()) : null,
                        ecKey.getX5t() != null ? new Base64URL(ecKey.getX5t()) : null,
                        ecKey.getX5tS256() != null ? new Base64URL(ecKey.getX5tS256()) : null,
                        ecKey.getX5c() != null ? ecKey.getX5c().stream().map(Base64::encode).collect(Collectors.toList()) : null,
                        null);
            }
        } catch (ParseException e) {
            throw new ServerErrorException("Malformed Elliptic Curve key encryption");
        }
    }

    public static OctetKeyPair convert(io.gravitee.am.model.jose.OKPKey okpKey) {
        try {
            if (!okpKey.isPrivate()) {
                //Curve crv, Base64URL x, KeyUse use, Set<KeyOperation> ops, Algorithm alg, String kid, URI x5u, Base64URL x5t, Base64URL x5t256, List<Base64> x5c, KeyStore ks
                return new OctetKeyPair(
                        Curve.parse(okpKey.getCrv()),
                        new Base64URL(okpKey.getX()),
                        okpKey.getUse() != null ? com.nimbusds.jose.jwk.KeyUse.parse(okpKey.getUse()) : null,
                        okpKey.getKeyOps() != null ? KeyOperation.parse(new ArrayList<>(okpKey.getKeyOps())) : null,
                        okpKey.getAlg() != null ? new Algorithm(okpKey.getAlg()) : null,
                        okpKey.getKid(),
                        okpKey.getX5u() != null ? URI.create(okpKey.getX5u()) : null,
                        okpKey.getX5t() != null ? new Base64URL(okpKey.getX5t()) : null,
                        okpKey.getX5tS256() != null ? new Base64URL(okpKey.getX5tS256()) : null,
                        okpKey.getX5c() != null ? okpKey.getX5c().stream().map(Base64::encode).collect(Collectors.toList()) : null,
                        null
                );
            } else {
                return new OctetKeyPair(
                        Curve.parse(okpKey.getCrv()),
                        new Base64URL(okpKey.getX()),
                        new Base64URL(okpKey.getD()),
                        okpKey.getUse() != null ? com.nimbusds.jose.jwk.KeyUse.parse(okpKey.getUse()) : null,
                        okpKey.getKeyOps() != null ? KeyOperation.parse(new ArrayList<>(okpKey.getKeyOps())) : null,
                        okpKey.getAlg() != null ? new Algorithm(okpKey.getAlg()) : null,
                        okpKey.getKid(),
                        okpKey.getX5u() != null ? URI.create(okpKey.getX5u()) : null,
                        okpKey.getX5t() != null ? new Base64URL(okpKey.getX5t()) : null,
                        okpKey.getX5tS256() != null ? new Base64URL(okpKey.getX5tS256()) : null,
                        okpKey.getX5c() != null ? okpKey.getX5c().stream().map(Base64::encode).collect(Collectors.toList()) : null,
                        null);
            }
        } catch (ParseException e) {
            throw new ServerErrorException("Malformed Octet Key Pair encryption");
        }
    }

    public static OctetSequenceKey convert(io.gravitee.am.model.jose.OCTKey octKey) {
        try {
            //Base64URL k, KeyUse use, Set<KeyOperation> ops, Algorithm alg, String kid, URI x5u, Base64URL x5t, Base64URL x5t256, List<Base64> x5c, KeyStore ks
            return new OctetSequenceKey(
                    new Base64URL(octKey.getK()),
                    octKey.getUse() != null ? com.nimbusds.jose.jwk.KeyUse.parse(octKey.getUse()) : null,
                    octKey.getKeyOps()!=null?KeyOperation.parse(new ArrayList<>(octKey.getKeyOps())):null,
                    octKey.getAlg()!=null?new Algorithm(octKey.getAlg()):null,
                    octKey.getKid(),
                    octKey.getX5u() != null ? URI.create(octKey.getX5u()) : null,
                    octKey.getX5t() != null ? new Base64URL(octKey.getX5t()) : null,
                    octKey.getX5tS256() != null ? new Base64URL(octKey.getX5tS256()) : null,
                    octKey.getX5c() != null ? octKey.getX5c().stream().map(Base64::encode).collect(Collectors.toList()) : null,
                    null
            );
        } catch (ParseException e) {
            throw new ServerErrorException("Malformed Octet Key Pair encryption");
        }
    }

    /*********************************
     * FROM NIMBUS TO GRAVITEE MODEL *
     *********************************/

    public static JWK convert(com.nimbusds.jose.jwk.JWK jwk) {
        if (jwk == null) {
            return null;
        }

        return switch (KeyType.parse(jwk.getKeyType().getValue())) {
            case EC -> convert((com.nimbusds.jose.jwk.ECKey) jwk);
            case RSA -> convert((com.nimbusds.jose.jwk.RSAKey) jwk);
            case OCT -> convert((OctetSequenceKey) jwk);
            case OKP -> convert((OctetKeyPair) jwk);
            default -> throw new InvalidClientMetadataException("Unknown JWK Key Type (kty)");
        };
    }

    public static JWK convert(com.nimbusds.jose.jwk.RSAKey jwk) {
        RSAKey rsaKey = new RSAKey();
        rsaKey.setKty(KeyType.RSA.getKeyType());
        rsaKey.setKid(jwk.getKeyID());
        rsaKey.setUse(jwk.getKeyUse() != null ? jwk.getKeyUse().identifier() : null);
        rsaKey.setE(jwk.getPublicExponent() != null ? jwk.getPublicExponent().toString() : null);
        rsaKey.setN(jwk.getModulus() != null ? jwk.getModulus().toString() : null);
        rsaKey.setD(jwk.getPrivateExponent() != null ? jwk.getPrivateExponent().toString() : null);
        rsaKey.setP(jwk.getFirstPrimeFactor() != null ? jwk.getFirstPrimeFactor().toString() : null);
        rsaKey.setQ(jwk.getSecondPrimeFactor() != null ? jwk.getSecondPrimeFactor().toString() : null);
        rsaKey.setDp(jwk.getFirstFactorCRTExponent() != null ? jwk.getFirstFactorCRTExponent().toString() : null);
        rsaKey.setDq(jwk.getSecondFactorCRTExponent() != null ? jwk.getSecondFactorCRTExponent().toString() : null);
        rsaKey.setQi(jwk.getFirstCRTCoefficient() != null ? jwk.getFirstCRTCoefficient().toString() : null);
        return rsaKey;
    }

    public static JWK convert(com.nimbusds.jose.jwk.ECKey jwk) {
        ECKey ecKey = new ECKey();
        ecKey.setKty(KeyType.EC.getKeyType());
        ecKey.setKid(jwk.getKeyID());
        ecKey.setUse(jwk.getKeyUse() != null ? jwk.getKeyUse().identifier() : null);
        ecKey.setX(jwk.getX() != null ? jwk.getX().toString() : null);
        ecKey.setY(jwk.getY() != null ? jwk.getY().toString() : null);
        ecKey.setCrv(jwk.getCurve().getName());
        ecKey.setD(jwk.getD() != null ? jwk.getD().toString() : null);
        return ecKey;
    }

    public static JWK convert(com.nimbusds.jose.jwk.OctetKeyPair jwk) {
        OKPKey okpKey = new OKPKey();
        okpKey.setKty(KeyType.OKP.getKeyType());
        okpKey.setKid(jwk.getKeyID());
        okpKey.setUse(jwk.getKeyUse() != null ? jwk.getKeyUse().identifier() : null);
        okpKey.setX(jwk.getX() != null ? jwk.getX().toString() : null);
        okpKey.setCrv(jwk.getCurve().getName());
        okpKey.setD(jwk.getD() != null ? jwk.getD().toString() : null);
        return okpKey;
    }

    public static JWK convert(com.nimbusds.jose.jwk.OctetSequenceKey jwk) {
        OCTKey octKey = new OCTKey();
        octKey.setKty(KeyType.OCT.getKeyType());
        octKey.setKid(jwk.getKeyID());
        octKey.setUse(jwk.getKeyUse() != null ? jwk.getKeyUse().identifier() : null);
        octKey.setK(jwk.getKeyValue() != null ? jwk.getKeyValue().toString() : null);

        return octKey;
    }
}
