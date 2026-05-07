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
package io.gravitee.am.management.handlers.management.api.adapter;

import io.gravitee.am.model.Application;
import io.gravitee.am.model.CimdMetadataDocument;
import io.gravitee.am.model.application.ApplicationOAuthSettings;
import io.gravitee.am.model.application.ApplicationSettings;
import io.gravitee.am.service.ApplicationService;
import io.gravitee.am.service.CimdMetadataDocumentService;
import io.reactivex.rxjava3.core.Maybe;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsentApplicationEntityFactoryTest {

    private static final String DOMAIN = "domain-1";
    private static final String CLIENT_URL = "https://CLIENT.EXAMPLE.COM/metadata";

    @Mock
    private ApplicationService applicationService;

    @Mock
    private CimdMetadataDocumentService cimdMetadataDocumentService;

    @InjectMocks
    private ConsentApplicationEntityFactory factory;

    @Test
    void shouldUseRegisteredApplicationWhenPresent() {
        Application app = new Application();
        app.setId("app-id");
        app.setName("My Application");
        ApplicationOAuthSettings oauth = new ApplicationOAuthSettings();
        oauth.setClientId("regular-id");
        ApplicationSettings settings = new ApplicationSettings();
        settings.setOauth(oauth);
        app.setSettings(settings);

        when(applicationService.findByDomainAndClientId(DOMAIN, "regular-id")).thenReturn(Maybe.just(app));

        factory.resolve(DOMAIN, "regular-id")
                .test()
                .assertValue(e -> "app-id".equals(e.getId()) && "My Application".equals(e.getName()) && "regular-id".equals(e.getClientId()));
    }

    @Test
    void shouldUseCimdClientEntityIdWhenMetadataDocumentFound() {
        when(applicationService.findByDomainAndClientId(DOMAIN, CLIENT_URL)).thenReturn(Maybe.empty());

        CimdMetadataDocument doc = new CimdMetadataDocument();
        doc.setMetadata("{\"client_name\":\"CIMD display\"}");
        when(cimdMetadataDocumentService.findByDomainAndClientId(DOMAIN, "https://client.example.com/metadata"))
                .thenReturn(Maybe.just(doc));

        factory.resolve(DOMAIN, CLIENT_URL)
                .test()
                .assertValue(e -> ConsentApplicationEntityFactory.CIMD_CLIENT.equals(e.getId())
                        && CLIENT_URL.equals(e.getClientId())
                        && "CIMD display".equals(e.getName()));
    }

    @Test
    void shouldFallbackToCanonicalUrlAsNameWhenClientNameMissingInMetadata() {
        when(applicationService.findByDomainAndClientId(DOMAIN, CLIENT_URL)).thenReturn(Maybe.empty());

        CimdMetadataDocument doc = new CimdMetadataDocument();
        doc.setMetadata("{\"client_id\":\"" + CLIENT_URL + "\"}");
        when(cimdMetadataDocumentService.findByDomainAndClientId(DOMAIN, "https://client.example.com/metadata"))
                .thenReturn(Maybe.just(doc));

        factory.resolve(DOMAIN, CLIENT_URL)
                .test()
                .assertValue(e -> ConsentApplicationEntityFactory.CIMD_CLIENT.equals(e.getId())
                        && CLIENT_URL.equals(e.getClientId())
                        && "https://client.example.com/metadata".equals(e.getName()));
    }

    @Test
    void shouldUseCimdClientAndCanonicalUrlAsNameWhenUrlShapedButNoCachedMetadata() {
        when(applicationService.findByDomainAndClientId(DOMAIN, CLIENT_URL)).thenReturn(Maybe.empty());
        when(cimdMetadataDocumentService.findByDomainAndClientId(anyString(), anyString())).thenReturn(Maybe.empty());

        factory.resolve(DOMAIN, CLIENT_URL)
                .test()
                .assertValue(e -> ConsentApplicationEntityFactory.CIMD_CLIENT.equals(e.getId())
                        && CLIENT_URL.equals(e.getClientId())
                        && "https://client.example.com/metadata".equals(e.getName()));
    }

    @Test
    void shouldUseUnknownWhenOpaqueClientAndNoApplication() {
        when(applicationService.findByDomainAndClientId(DOMAIN, "opaque")).thenReturn(Maybe.empty());

        factory.resolve(DOMAIN, "opaque")
                .test()
                .assertValue(e -> ScopeApprovalAdapterImpl.UNKNOWN_ID.equals(e.getId())
                        && "unknown-client-name".equals(e.getName()));
    }
}
