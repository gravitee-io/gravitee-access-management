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

import java.util.Set;

public class JwkNimbusECConverter extends JwkNimbusBaseConverter<ECKey, com.nimbusds.jose.jwk.ECKey> {

    private final boolean includePrivate;

    public JwkNimbusECConverter(com.nimbusds.jose.jwk.ECKey ecKey,
                                boolean includePrivate,
                                Set<String> usage,
                                String algorithm) {
        super(ecKey, usage, algorithm);
        this.includePrivate = includePrivate;
    }

    @Override
    protected ECKey init() {
        return new ECKey();
    }

    @Override
    protected void updateJwk(ECKey jwk) {
        if (nimbusJwk.getCurve() != null) {
            jwk.setCrv(nimbusJwk.getCurve().toString());
        }
        if (nimbusJwk.getX() != null) {
            jwk.setX(nimbusJwk.getX().toString());
        }
        if (nimbusJwk.getY() != null) {
            jwk.setY(nimbusJwk.getY().toString());
        }
        if (includePrivate) {
            jwk.setD(nimbusJwk.getD().toString());
        }
    }

}
