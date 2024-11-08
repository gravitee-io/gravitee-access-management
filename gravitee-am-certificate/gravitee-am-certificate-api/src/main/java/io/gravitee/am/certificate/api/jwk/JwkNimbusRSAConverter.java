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

import io.gravitee.am.model.jose.ECKey;
import io.gravitee.am.model.jose.JWK;
import io.gravitee.am.model.jose.RSAKey;
import lombok.Builder;

import java.util.List;
import java.util.Set;

public class JwkNimbusRSAConverter extends JwkNimbusBaseConverter<RSAKey, com.nimbusds.jose.jwk.RSAKey> {

    private final boolean includePrivate;

    @Builder
    public JwkNimbusRSAConverter(com.nimbusds.jose.jwk.RSAKey nimbusRsaJwk,
                                 boolean includePrivate,
                                 Set<String> usage,
                                 String algorithm) {
        super(nimbusRsaJwk, usage, algorithm);
        this.includePrivate = includePrivate;
    }

    @Override
    protected RSAKey init() {
        return new RSAKey();
    }

    @Override
    protected void updateJwk(RSAKey jwk) {
        if (nimbusJwk.getPublicExponent() != null) {
            jwk.setE(nimbusJwk.getPublicExponent().toString());
        }
        if (nimbusJwk.getModulus() != null) {
            jwk.setN(nimbusJwk.getModulus().toString());
        }

        if (includePrivate) {
            if (nimbusJwk.getPrivateExponent() != null) {
                jwk.setD(nimbusJwk.getPrivateExponent().toString());
            }

            if (nimbusJwk.getFirstPrimeFactor() != null) {
                jwk.setP(nimbusJwk.getFirstPrimeFactor().toString());
            }

            if (nimbusJwk.getFirstFactorCRTExponent() != null) {
                jwk.setDp(nimbusJwk.getFirstFactorCRTExponent().toString());
            }

            if (nimbusJwk.getFirstCRTCoefficient() != null) {
                jwk.setQi(nimbusJwk.getFirstCRTCoefficient().toString());
            }

            if (nimbusJwk.getSecondPrimeFactor() != null) {
                jwk.setQ(nimbusJwk.getSecondPrimeFactor().toString());
            }

            if (nimbusJwk.getSecondFactorCRTExponent() != null) {
                jwk.setDq(nimbusJwk.getSecondFactorCRTExponent().toString());
            }
        }
    }
}
