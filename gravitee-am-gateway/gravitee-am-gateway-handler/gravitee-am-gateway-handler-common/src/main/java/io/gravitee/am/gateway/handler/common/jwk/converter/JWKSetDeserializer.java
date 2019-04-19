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
package io.gravitee.am.gateway.handler.common.jwk.converter;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.fasterxml.jackson.databind.util.StdConverter;
import io.gravitee.am.model.jose.*;
import io.gravitee.am.model.oidc.JWKSet;
import io.gravitee.am.service.exception.InvalidClientMetadataException;
import io.gravitee.am.service.exception.NotImplementedException;
import org.slf4j.LoggerFactory;

import java.text.ParseException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
public class JWKSetDeserializer extends StdConverter<ObjectNode, Optional<JWKSet>> {

    private final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(JWKSetDeserializer.class);

    @Override
    public Optional<JWKSet> convert(ObjectNode node) {
        if (node == null) {
            return null;
        }

        if (node.get("keys") == null ||
                node.get("keys").isNull() ||
                node.get("keys").equals(new TextNode("null")) ||
                node.get("keys").equals(new TextNode(""))
        ) {
            return Optional.empty();
        }

        return convert(node.toString());
    }

    public Optional<JWKSet> convert(String jwkSetAsString) {
        try {
            com.nimbusds.jose.jwk.JWKSet jwkSet = com.nimbusds.jose.jwk.JWKSet.parse(jwkSetAsString);
            List<JWK> jwkList = jwkSet.getKeys()
                    .stream()
                    .map(this::convert)
                    .collect(Collectors.toList());

            JWKSet result = new JWKSet();
            result.setKeys(jwkList);
            return Optional.of(result);
        } catch (ParseException ex) {
            LOGGER.error(ex.getMessage(), ex);
            throw new InvalidClientMetadataException("Unable to parse jwks content");
        }
    }

    private JWK convert(com.nimbusds.jose.jwk.JWK jwk) {
        if (jwk == null) {
            return null;
        }

        switch (KeyType.valueOf(jwk.getKeyType().getValue())) {
            case EC:
                return fromEC((com.nimbusds.jose.jwk.ECKey) jwk);
            case RSA:
                return fromRSA((com.nimbusds.jose.jwk.RSAKey) jwk);
            case OCT:
                throw new NotImplementedException("JWK Key Type:" + KeyType.OCT.getKeyType());
            case OKP:
                throw new NotImplementedException("JWK Key Type:" + KeyType.OKP.getKeyType());
            default:
                throw new InvalidClientMetadataException("Unknown JWK Key Type (kty)");
        }
    }

    private JWK fromRSA(com.nimbusds.jose.jwk.RSAKey jwk) {
        RSAKey rsaKey = new RSAKey();
        rsaKey.setKty(KeyType.RSA.getKeyType());
        rsaKey.setKid(jwk.getKeyID());
        rsaKey.setUse(jwk.getKeyUse() != null ? jwk.getKeyUse().identifier() : null);
        rsaKey.setE(jwk.getPublicExponent() != null ? jwk.getPublicExponent().toString() : null);
        rsaKey.setN(jwk.getModulus() != null ? jwk.getModulus().toString() : null);

        return rsaKey;
    }

    private JWK fromEC(com.nimbusds.jose.jwk.ECKey jwk) {
        ECKey ecKey = new ECKey();
        ecKey.setKty(KeyType.EC.getKeyType());
        ecKey.setKid(jwk.getKeyID());
        ecKey.setUse(jwk.getKeyUse() != null ? jwk.getKeyUse().identifier() : null);
        ecKey.setX(jwk.getX() != null ? jwk.getX().toString() : null);
        ecKey.setY(jwk.getY() != null ? jwk.getY().toString() : null);

        Optional<Curve> curve = Curve.getByName(jwk.getCurve().getName());
        //if not present, parse method will fail before...
        ecKey.setCrv(curve.get().getName());

        return ecKey;
    }
}
