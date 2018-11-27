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
package io.gravitee.am.gateway.handler.oidc.jwk.impl;

import io.gravitee.am.gateway.handler.certificate.CertificateManager;
import io.gravitee.am.gateway.handler.oidc.jwk.JWK;
import io.gravitee.am.gateway.handler.oidc.jwk.JWKSet;
import io.gravitee.am.gateway.handler.oidc.jwk.JWKSetService;
import io.gravitee.am.gateway.handler.oidc.jwk.RSAKey;
import io.reactivex.Flowable;
import io.reactivex.Single;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class JWKSetServiceImpl implements JWKSetService {

    @Autowired
    private CertificateManager certificateManager;

    @Override
    public Single<JWKSet> getKeys() {
        return Flowable.fromIterable(certificateManager.providers())
                .flatMap(certificateProvider -> certificateProvider.getProvider().keys())
                .map(this::convert)
                .toList()
                .map(keys -> {
                    JWKSet jwkSet = new JWKSet();
                    jwkSet.setKeys(keys);
                    return jwkSet;
                });

    }

    private JWK convert(io.gravitee.am.model.jose.JWK jwk) {
        JWK jwk1 = new RSAKey();
        jwk1.setKty(jwk.getKty());
        jwk1.setUse(jwk.getUse());
        jwk1.setKeyOps(jwk.getKeyOps());
        jwk1.setAlg(jwk.getAlg());
        jwk1.setKid(jwk.getKid());
        jwk1.setX5u(jwk.getX5u());
        jwk1.setX5c(jwk.getX5c());
        jwk1.setX5t(jwk.getX5t());
        jwk1.setX5tS256(jwk.getX5tS256());

        // specific RSA Key
        ((RSAKey) jwk1).setE(((io.gravitee.am.model.jose.RSAKey)jwk).getE());
        ((RSAKey) jwk1).setN(((io.gravitee.am.model.jose.RSAKey)jwk).getN());

        return jwk1;
    }
}
