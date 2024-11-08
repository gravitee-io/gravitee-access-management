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
package io.gravitee.am.certificate.api.jwk;

import com.nimbusds.jose.jwk.KeyOperation;
import com.nimbusds.jose.util.Base64;
import io.gravitee.am.model.jose.JWK;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RequiredArgsConstructor
abstract class JwkNimbusBaseConverter<T extends JWK, N extends com.nimbusds.jose.jwk.JWK> implements JwkNimbusConverter {
    protected final N nimbusJwk;
    protected final Set<String> usage;
    protected final String algorithm;

    @Override
    public Stream<JWK> createJwk() {
        return usage.stream().map(this::createJwk);
    }

    protected JWK createJwk(String use) {
        T jwk = updateJwk(use);
        updateJwk(jwk);
        return jwk;
    }

    protected abstract T init();

    protected abstract void updateJwk(T jwk);

    private T updateJwk(String use){
        T jwk = init();
        jwk.setUse(use);

        if (nimbusJwk.getKeyOperations() != null) {
            jwk.setKeyOps(nimbusJwk.getKeyOperations().stream().map(KeyOperation::identifier).collect(Collectors.toSet()));
        }

        if (algorithm != null && !algorithm.isEmpty()) {
            jwk.setAlg(algorithm);
        } else if (nimbusJwk.getAlgorithm() != null) {
            jwk.setAlg(nimbusJwk.getAlgorithm().getName());
        }
        if (nimbusJwk.getKeyID() != null) {
            jwk.setKid(nimbusJwk.getKeyID());
        }
        if (nimbusJwk.getX509CertURL() != null) {
            jwk.setX5u(nimbusJwk.getX509CertURL().toString());
        }
        if (nimbusJwk.getX509CertChain() != null) {
            jwk.setX5c(nimbusJwk.getX509CertChain().stream().map(Base64::toString).collect(Collectors.toSet()));
        }
        if (nimbusJwk.getX509CertThumbprint() != null) {
            jwk.setX5t(nimbusJwk.getX509CertThumbprint().toString());
        }
        if (nimbusJwk.getX509CertSHA256Thumbprint() != null) {
            jwk.setX5tS256(nimbusJwk.getX509CertSHA256Thumbprint().toString());
        }
        return jwk;
    }



}
