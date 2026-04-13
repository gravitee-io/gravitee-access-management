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
package io.gravitee.am.gateway.handler.aauth.spring;

import io.gravitee.am.gateway.handler.aauth.resources.endpoint.AAuthJWKSEndpoint;
import io.gravitee.am.gateway.handler.aauth.resources.handler.AAuthSignatureHandler;
import io.gravitee.am.gateway.handler.aauth.service.AgentMetadataFetcher;
import io.gravitee.am.gateway.handler.aauth.signing.AAuthSignatureVerifier;
import io.gravitee.am.gateway.handler.aauth.signing.ReplayDetector;
import io.gravitee.am.gateway.handler.aauth.signing.schemes.JWKSUriScheme;
import io.gravitee.am.gateway.handler.aauth.signing.schemes.SignatureSchemeFactory;
import io.gravitee.am.gateway.handler.api.ProtocolConfiguration;
import io.gravitee.am.gateway.handler.common.certificate.CertificateManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration for the AAUTH protocol plugin.
 *
 * @author GraviteeSource Team
 */
@Configuration
public class AAuthConfiguration implements ProtocolConfiguration {

    @Bean
    public ReplayDetector replayDetector() {
        return new ReplayDetector();
    }

    @Bean
    public AgentMetadataFetcher agentMetadataFetcher() {
        return new AgentMetadataFetcher();
    }

    @Bean
    public JWKSUriScheme jwksUriScheme(AgentMetadataFetcher fetcher) {
        return new JWKSUriScheme(fetcher);
    }

    @Bean
    public SignatureSchemeFactory signatureSchemeFactory(JWKSUriScheme jwksUriScheme) {
        var factory = new SignatureSchemeFactory();
        factory.register("jwks_uri", jwksUriScheme);
        return factory;
    }

    @Bean
    public AAuthSignatureVerifier aAuthSignatureVerifier(SignatureSchemeFactory schemeFactory,
                                                          ReplayDetector replayDetector) {
        return new AAuthSignatureVerifier(schemeFactory, replayDetector);
    }

    @Bean
    public AAuthSignatureHandler aAuthSignatureHandler(AAuthSignatureVerifier verifier) {
        return new AAuthSignatureHandler(verifier);
    }

    @Bean
    public AAuthJWKSEndpoint aAuthJWKSEndpoint(CertificateManager certificateManager) {
        return new AAuthJWKSEndpoint(certificateManager);
    }
}
