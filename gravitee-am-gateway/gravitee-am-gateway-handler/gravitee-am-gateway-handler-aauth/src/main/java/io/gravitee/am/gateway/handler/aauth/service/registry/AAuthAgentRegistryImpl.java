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
package io.gravitee.am.gateway.handler.aauth.service.registry;

import io.gravitee.am.gateway.handler.aauth.service.AgentMetadata;
import io.gravitee.am.gateway.handler.aauth.service.AgentMetadataFetcher;
import io.gravitee.am.gateway.handler.aauth.signing.VerificationResult;
import io.gravitee.am.model.Application;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.application.ApplicationOAuthSettings;
import io.gravitee.am.model.application.ApplicationSettings;
import io.gravitee.am.model.application.ApplicationType;
import io.gravitee.am.service.ApplicationService;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Resolves or auto-creates {@code Application(type=AAUTH_AGENT)} for verified agents.
 * <p>
 * The Application is keyed by the agent's metadata URL ({@code clientId = metadata URL}).
 * On first contact, the registry fetches the agent's metadata to get the display name,
 * creates the Application via {@link ApplicationService}, and returns it.
 */
@Slf4j
@RequiredArgsConstructor
public class AAuthAgentRegistryImpl implements AAuthAgentRegistry {

    private final ApplicationService applicationService;
    private final AgentMetadataFetcher metadataFetcher;
    private final Domain domain;

    @Override
    public Maybe<Application> resolveOrCreate(VerificationResult verification, String domainId) {
        String agentServerUrl = verification.agentServerUrl();

        // Pseudonymous (hwk) → no identity URL → empty
        if (agentServerUrl == null) {
            return Maybe.empty();
        }

        return applicationService.findByDomainAndClientId(domainId, agentServerUrl)
                .switchIfEmpty(Maybe.defer(() ->
                        autoCreateApplication(domainId, agentServerUrl).toMaybe()));
    }

    private Single<Application> autoCreateApplication(String domainId, String agentMetadataUrl) {
        return Single.fromCallable(() -> {
            // Fetch agent metadata to get display name and logo
            String agentName = agentMetadataUrl; // fallback
            String logoUri = null;
            try {
                AgentMetadata metadata = metadataFetcher.fetchMetadata(agentMetadataUrl);
                if (metadata.clientName() != null && !metadata.clientName().isBlank()) {
                    agentName = metadata.clientName();
                }
                logoUri = metadata.logoUri();
            } catch (Exception e) {
                log.warn("Could not fetch agent metadata from {}, using URL as name: {}",
                        agentMetadataUrl, e.getMessage());
            }

            return buildApplication(domainId, agentMetadataUrl, agentName, logoUri);
        }).flatMap(app -> applicationService.create(domain, app));
    }

    private Application buildApplication(String domainId, String agentMetadataUrl, String agentName, String logoUri) {
        var app = new Application();
        app.setType(ApplicationType.AAUTH_AGENT);
        app.setDomain(domainId);
        app.setName(agentName);
        app.setEnabled(true);

        // Set clientId = agent metadata URL
        var oauthSettings = new ApplicationOAuthSettings();
        oauthSettings.setClientId(agentMetadataUrl);
        if (logoUri != null) {
            oauthSettings.setLogoUri(logoUri);
        }
        var settings = new ApplicationSettings();
        settings.setOauth(oauthSettings);
        app.setSettings(settings);

        // AAUTH-specific metadata
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("aauth.metadataUrl", agentMetadataUrl);
        metadata.put("aauth.firstSeenAt", Instant.now().toString());
        metadata.put("aauth.lastSeenAt", Instant.now().toString());
        app.setMetadata(metadata);

        return app;
    }
}
