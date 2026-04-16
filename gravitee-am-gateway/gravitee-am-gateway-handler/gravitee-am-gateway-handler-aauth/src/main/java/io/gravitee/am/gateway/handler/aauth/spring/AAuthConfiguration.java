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

import io.gravitee.am.gateway.handler.aauth.resources.endpoint.AAuthConsentPostEndpoint;
import io.gravitee.am.gateway.handler.aauth.resources.endpoint.AAuthJWKSEndpoint;
import io.gravitee.am.gateway.handler.aauth.resources.endpoint.AAuthPendingEndpoint;
import io.gravitee.am.gateway.handler.aauth.resources.endpoint.AAuthTokenEndpoint;
import io.gravitee.am.gateway.handler.aauth.service.pending.AAuthPendingRequestService;
import io.gravitee.am.gateway.handler.aauth.resources.handler.AAuthAgentResolveHandler;
import io.gravitee.am.gateway.handler.aauth.resources.handler.AAuthConsentRedirectHandler;
import io.gravitee.am.gateway.handler.aauth.resources.handler.AAuthConsentHandler;
import io.gravitee.am.gateway.handler.aauth.resources.handler.AAuthInteractionResolveHandler;
import io.gravitee.am.gateway.handler.aauth.resources.handler.AAuthSignatureHandler;
import io.gravitee.am.gateway.handler.aauth.resources.handler.AAuthTokenRequestParseHandler;
import io.gravitee.am.gateway.handler.aauth.service.consent.AAuthConsentService;
import io.gravitee.am.service.ScopeApprovalService;
import io.gravitee.am.gateway.handler.aauth.service.AgentMetadataFetcher;
import io.gravitee.am.gateway.handler.aauth.service.registry.AAuthAgentRegistry;
import io.gravitee.am.gateway.handler.aauth.service.registry.AAuthAgentRegistryImpl;
import io.gravitee.am.gateway.handler.aauth.service.token.AAuthTokenService;
import io.gravitee.am.gateway.handler.aauth.service.token.ResourceTokenValidator;
import io.gravitee.am.repository.oidc.api.AAuthPendingRequestRepository;
import io.gravitee.am.gateway.handler.aauth.signing.AAuthSignatureVerifier;
import io.gravitee.am.gateway.handler.aauth.signing.ReplayDetector;
import io.gravitee.am.gateway.handler.aauth.signing.schemes.JWKSUriScheme;
import io.gravitee.am.gateway.handler.aauth.signing.schemes.SignatureSchemeFactory;
import io.gravitee.am.gateway.handler.api.ProtocolConfiguration;
import io.gravitee.am.gateway.handler.common.certificate.CertificateManager;
import io.gravitee.am.gateway.handler.common.jwt.JWTService;
import io.gravitee.am.model.Domain;
import io.gravitee.am.service.ApplicationService;
import io.vertx.rxjava3.ext.web.templ.thymeleaf.ThymeleafTemplateEngine;
import org.springframework.beans.factory.annotation.Value;
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

    @Bean
    public AAuthAgentRegistry aAuthAgentRegistry(ApplicationService applicationService,
                                                  AgentMetadataFetcher fetcher,
                                                  Domain domain) {
        return new AAuthAgentRegistryImpl(applicationService, fetcher, domain);
    }

    @Bean
    public AAuthAgentResolveHandler aAuthAgentResolveHandler(AAuthAgentRegistry registry,
                                                              Domain domain) {
        return new AAuthAgentResolveHandler(registry, domain.getId());
    }

    @Bean
    public ResourceTokenValidator resourceTokenValidator(AgentMetadataFetcher fetcher) {
        return new ResourceTokenValidator(fetcher);
    }

    @Bean
    public AAuthTokenService aAuthTokenService(JWTService jwtService,
                                                CertificateManager certificateManager,
                                                Domain domain) {
        int lifespan = domain.getAauth() != null ? domain.getAauth().getAuthTokenLifespan() : 300;
        return new AAuthTokenService(jwtService, certificateManager, lifespan);
    }

    @Bean
    public AAuthTokenRequestParseHandler aAuthTokenRequestParseHandler() {
        return new AAuthTokenRequestParseHandler();
    }

    @Bean
    public AAuthPendingRequestService aAuthPendingRequestService(AAuthPendingRequestRepository repository) {
        return new AAuthPendingRequestService(repository);
    }

    @Bean
    public AAuthPendingEndpoint aAuthPendingEndpoint(AAuthPendingRequestService pendingService) {
        return new AAuthPendingEndpoint(pendingService);
    }

    @Bean
    public AAuthTokenEndpoint aAuthTokenEndpoint(ResourceTokenValidator resourceTokenValidator,
                                                  AAuthTokenService aAuthTokenService,
                                                  AAuthPendingRequestService pendingService,
                                                  Domain domain) {
        int pendingTtl = domain.getAauth() != null ? domain.getAauth().getPendingRequestTtl() : 600;
        return new AAuthTokenEndpoint(resourceTokenValidator, aAuthTokenService, pendingService, domain.getId(), pendingTtl);
    }

    @Bean
    public AAuthInteractionResolveHandler aAuthInteractionResolveHandler(AAuthPendingRequestService pendingService,
                                                                          ApplicationService applicationService) {
        return new AAuthInteractionResolveHandler(pendingService, applicationService);
    }

    @Bean
    public AAuthConsentService aAuthConsentService(ScopeApprovalService scopeApprovalService,
                                                    Domain domain) {
        return new AAuthConsentService(scopeApprovalService, domain);
    }

    @Bean
    public AAuthConsentRedirectHandler aAuthConsentRedirectHandler() {
        return new AAuthConsentRedirectHandler();
    }

    @Bean
    public AAuthConsentHandler aAuthConsentHandler(AAuthConsentService consentService,
                                                                          AAuthPendingRequestService pendingService,
                                                                          AAuthTokenService tokenService,
                                                                          ApplicationService applicationService,
                                                                          ThymeleafTemplateEngine engine,
                                                                          Domain domain) {
        return new AAuthConsentHandler(consentService, pendingService, tokenService, applicationService, engine, domain);
    }

    @Bean
    public AAuthConsentPostEndpoint aAuthConsentPostEndpoint(AAuthPendingRequestService pendingService,
                                                                      AAuthTokenService tokenService,
                                                                      AAuthConsentService consentService,
                                                                      Domain domain) {
        return new AAuthConsentPostEndpoint(pendingService, tokenService, consentService, domain);
    }
}
