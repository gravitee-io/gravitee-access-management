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
package io.gravitee.am.service.impl;

import io.gravitee.am.common.oidc.ClientAuthenticationMethod;
import io.gravitee.am.model.Application;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.application.AgentType;
import io.gravitee.am.model.application.ApplicationOAuthSettings;
import io.gravitee.am.model.application.ApplicationSettings;
import io.gravitee.am.model.application.ApplicationType;
import io.gravitee.am.model.application.SpiffeApplicationSettings;
import io.gravitee.am.model.oidc.TrustDomain;
import io.gravitee.am.repository.management.api.TrustDomainRepository;
import io.gravitee.am.service.exception.InvalidClientMetadataException;
import io.reactivex.rxjava3.core.Maybe;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class ApplicationServiceImplSpiffeValidationTest {

    private static final String DOMAIN_ID = "domain-1";
    private static final String TRUST_DOMAIN = "acme";

    @Mock
    private TrustDomainRepository trustDomainRepository;

    private ApplicationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ApplicationServiceImpl();
        ReflectionTestUtils.setField(service, "trustDomainRepository", trustDomainRepository);
        lenient().when(trustDomainRepository.findByName(eq(ReferenceType.DOMAIN), eq(DOMAIN_ID), eq(TRUST_DOMAIN)))
                .thenReturn(Maybe.just(TrustDomain.builder().name(TRUST_DOMAIN).build()));
    }

    @Test
    void exactMode_default_passes() {
        Application app = agentApp(AgentType.HOSTED_DELEGATED,
                spiffeSettings("spiffe://acme/hotel-agent", null));

        service.validateSpiffeSettings(app).test().assertNoErrors().assertValue(app);
    }

    @Test
    void prefixMode_passes_forHostedDelegatedAgent() {
        Application app = agentApp(AgentType.HOSTED_DELEGATED,
                spiffeSettings("spiffe://acme/hotel-agent/",
                        SpiffeApplicationSettings.SubjectMatchMode.PREFIX));

        service.validateSpiffeSettings(app).test().assertNoErrors().assertValue(app);
    }

    @Test
    void prefixMode_passes_forAutonomousAgent() {
        Application app = agentApp(AgentType.AUTONOMOUS,
                spiffeSettings("spiffe://acme/hotel-agent/",
                        SpiffeApplicationSettings.SubjectMatchMode.PREFIX));

        service.validateSpiffeSettings(app).test().assertNoErrors().assertValue(app);
    }

    @Test
    void prefixMode_rejected_forNonAgentApp() {
        Application app = new Application();
        app.setDomain(DOMAIN_ID);
        app.setType(ApplicationType.SERVICE);
        app.setSettings(new ApplicationSettings());
        ApplicationOAuthSettings oauth = new ApplicationOAuthSettings();
        oauth.setTokenEndpointAuthMethod(ClientAuthenticationMethod.SPIFFE_JWT);
        app.getSettings().setOauth(oauth);
        app.getSettings().setWorkloadIdentitySettings(spiffeSettings("spiffe://acme/hotel-agent",
                SpiffeApplicationSettings.SubjectMatchMode.PREFIX));

        service.validateSpiffeSettings(app).test()
                .assertError(err -> err instanceof InvalidClientMetadataException
                        && err.getMessage().contains("PREFIX is only allowed"));
    }

    @Test
    void prefixMode_rejected_forUserEmbeddedAgent() {
        Application app = agentApp(AgentType.USER_EMBEDDED,
                spiffeSettings("spiffe://acme/hotel-agent",
                        SpiffeApplicationSettings.SubjectMatchMode.PREFIX));

        service.validateSpiffeSettings(app).test()
                .assertError(err -> err instanceof InvalidClientMetadataException
                        && err.getMessage().contains("PREFIX is only allowed"));
    }

    @Test
    void prefixMode_rejected_whenSubjectMissingTrailingSlash() {
        Application app = agentApp(AgentType.HOSTED_DELEGATED,
                spiffeSettings("spiffe://acme/hotel-agent",
                        SpiffeApplicationSettings.SubjectMatchMode.PREFIX));

        service.validateSpiffeSettings(app).test()
                .assertError(err -> err instanceof InvalidClientMetadataException
                        && err.getMessage().contains("must end with '/'"));
    }

    @Test
    void exactMode_stillEnforcedFor_subjectAnchor() {
        Application app = agentApp(AgentType.HOSTED_DELEGATED,
                spiffeSettings("spiffe://other/hotel-agent", null));

        service.validateSpiffeSettings(app).test()
                .assertError(err -> err instanceof InvalidClientMetadataException
                        && err.getMessage().contains("must start with"));
    }

    private static Application agentApp(AgentType agentType, SpiffeApplicationSettings spiffe) {
        Application app = new Application();
        app.setDomain(DOMAIN_ID);
        app.setType(ApplicationType.AGENT);
        app.setKind(agentType.name());
        app.setSettings(new ApplicationSettings());
        ApplicationOAuthSettings oauth = new ApplicationOAuthSettings();
        oauth.setTokenEndpointAuthMethod(ClientAuthenticationMethod.SPIFFE_JWT);
        app.getSettings().setOauth(oauth);
        app.getSettings().setWorkloadIdentitySettings(spiffe);
        return app;
    }

    private static SpiffeApplicationSettings spiffeSettings(String subject,
                                                            SpiffeApplicationSettings.SubjectMatchMode mode) {
        SpiffeApplicationSettings s = new SpiffeApplicationSettings();
        s.setTrustDomain(TRUST_DOMAIN);
        s.setSubject(subject);
        if (mode != null) {
            s.setSubjectMatchMode(mode);
        }
        return s;
    }
}
