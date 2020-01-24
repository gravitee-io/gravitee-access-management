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
package io.gravitee.am.gateway.certificate;

import io.gravitee.am.gateway.certificate.jwt.JWTBuilder;
import io.gravitee.am.gateway.certificate.jwt.JWTParser;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class CertificateProvider {
    private io.gravitee.am.certificate.api.CertificateProvider provider;
    private String domain;
    private JWTParser jwtParser;
    private JWTBuilder jwtBuilder;

    public CertificateProvider(io.gravitee.am.certificate.api.CertificateProvider provider) {
        this.provider = provider;
    }

    public io.gravitee.am.certificate.api.CertificateProvider getProvider() {
        return provider;
    }

    public void setProvider(io.gravitee.am.certificate.api.CertificateProvider provider) {
        this.provider = provider;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public JWTParser getJwtParser() {
        return jwtParser;
    }

    public void setJwtParser(JWTParser jwtParser) {
        this.jwtParser = jwtParser;
    }

    public JWTBuilder getJwtBuilder() {
        return jwtBuilder;
    }

    public void setJwtBuilder(JWTBuilder jwtBuilder) {
        this.jwtBuilder = jwtBuilder;
    }
}
