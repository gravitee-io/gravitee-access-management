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
package io.gravitee.am.identityprovider.azure.jwt.processor;

import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.JWSKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import com.nimbusds.jwt.proc.JWTProcessor;
import io.gravitee.am.common.jwt.SignatureAlgorithm;
import io.gravitee.am.identityprovider.azure.jwt.jwks.JWKSourceResolver;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public abstract class AbstractKeyProcessor<C extends SecurityContext> implements KeyProcessor {

    private JWKSourceResolver<C> jwkSourceResolver;

    @Override
    public JWTProcessor create(SignatureAlgorithm signature) {
        JWKSource jwkSource = jwkSourceResolver.resolve();
        ConfigurableJWTProcessor<C> jwtProcessor = new DefaultJWTProcessor<>();
        jwtProcessor.setJWSKeySelector(jwsKeySelector(jwkSource, signature));

        return jwtProcessor;
    }

    public void setJwkSourceResolver(JWKSourceResolver<C> jwkSourceResolver) {
        this.jwkSourceResolver = jwkSourceResolver;
    }

    abstract JWSKeySelector<C> jwsKeySelector(JWKSource<C> jwkSource, SignatureAlgorithm signature);
}
