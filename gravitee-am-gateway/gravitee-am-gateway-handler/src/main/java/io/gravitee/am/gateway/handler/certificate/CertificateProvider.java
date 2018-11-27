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
package io.gravitee.am.gateway.handler.certificate;

import io.gravitee.am.gateway.handler.jwt.JwtBuilder;
import io.gravitee.am.gateway.handler.jwt.JwtParser;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class CertificateProvider {

    private io.gravitee.am.certificate.api.CertificateProvider provider;
    private JwtParser jwtParser;
    private JwtBuilder jwtBuilder;

    public CertificateProvider(io.gravitee.am.certificate.api.CertificateProvider provider) {
        this.provider = provider;
    }

    public io.gravitee.am.certificate.api.CertificateProvider getProvider() {
        return provider;
    }

    public void setProvider(io.gravitee.am.certificate.api.CertificateProvider provider) {
        this.provider = provider;
    }

    public JwtParser getJwtParser() {
        return jwtParser;
    }

    public void setJwtParser(JwtParser jwtParser) {
        this.jwtParser = jwtParser;
    }

    public JwtBuilder getJwtBuilder() {
        return jwtBuilder;
    }

    public void setJwtBuilder(JwtBuilder jwtBuilder) {
        this.jwtBuilder = jwtBuilder;
    }
}
