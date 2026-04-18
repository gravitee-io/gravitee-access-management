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
package io.gravitee.am.management.handlers.management.api.resources.organizations.environments.domains;

import com.nimbusds.jose.jwk.KeyType;
import io.gravitee.am.model.jose.ECKey;
import io.gravitee.am.model.jose.JWK;
import io.gravitee.am.model.jose.OKPKey;
import io.gravitee.am.model.jose.RSAKey;
import io.gravitee.am.service.exception.InvalidClientMetadataException;

import java.text.ParseException;
import java.util.Map;

/**
 * Parses a raw JWK map posted by the console/API into a validated internal JWK subtype.
 * <p>
 * Rejects private-key material and symmetric (oct) keys — only public asymmetric keys
 * are acceptable for verifying agent jwt-bearer client-assertion signatures.
 */
final class AgentJwkMapper {

    private AgentJwkMapper() {}

    static JWK fromRaw(Map<String, Object> rawKey) {
        if (rawKey == null || rawKey.isEmpty()) {
            throw new InvalidClientMetadataException("JWK payload is empty");
        }

        final com.nimbusds.jose.jwk.JWK parsed;
        try {
            parsed = com.nimbusds.jose.jwk.JWK.parse(rawKey);
        } catch (ParseException ex) {
            throw new InvalidClientMetadataException("Invalid JWK: " + ex.getMessage());
        }

        if (parsed.isPrivate()) {
            throw new InvalidClientMetadataException("JWK must not contain private key material");
        }

        final KeyType kty = parsed.getKeyType();
        if (KeyType.OCT.equals(kty)) {
            throw new InvalidClientMetadataException("Symmetric (oct) keys are not allowed for agent authentication");
        }

        final JWK result;
        if (KeyType.RSA.equals(kty)) {
            final com.nimbusds.jose.jwk.RSAKey rsa = parsed.toRSAKey();
            final RSAKey out = new RSAKey();
            out.setN(rsa.getModulus().toString());
            out.setE(rsa.getPublicExponent().toString());
            result = out;
        } else if (KeyType.EC.equals(kty)) {
            final com.nimbusds.jose.jwk.ECKey ec = parsed.toECKey();
            final ECKey out = new ECKey();
            out.setCrv(ec.getCurve() != null ? ec.getCurve().getName() : null);
            out.setX(ec.getX().toString());
            out.setY(ec.getY().toString());
            result = out;
        } else if (KeyType.OKP.equals(kty)) {
            final com.nimbusds.jose.jwk.OctetKeyPair okp = parsed.toOctetKeyPair();
            final OKPKey out = new OKPKey();
            out.setCrv(okp.getCurve() != null ? okp.getCurve().getName() : null);
            out.setX(okp.getX().toString());
            result = out;
        } else {
            throw new InvalidClientMetadataException("Unsupported JWK key type: " + kty);
        }

        result.setKid(parsed.getKeyID());
        if (result.getKid() == null || result.getKid().isBlank()) {
            throw new InvalidClientMetadataException("JWK must include a kid");
        }
        if (parsed.getKeyUse() != null) {
            result.setUse(parsed.getKeyUse().identifier());
        }
        if (parsed.getAlgorithm() != null) {
            result.setAlg(parsed.getAlgorithm().getName());
        }
        return result;
    }
}
