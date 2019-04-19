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
import io.gravitee.am.gateway.handler.oidc.utils.JWKSetUtils;
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
                .map(JWKSetUtils::convert)
                .toList()
                .map(keys -> {
                    JWKSet jwkSet = new JWKSet();
                    jwkSet.setKeys(keys);
                    return jwkSet;
                });
    }
}
