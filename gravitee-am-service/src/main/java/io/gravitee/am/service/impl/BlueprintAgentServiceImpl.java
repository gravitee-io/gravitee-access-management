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

import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.model.Application;
import io.gravitee.am.model.application.AgentSettings;
import io.gravitee.am.model.application.ApplicationOAuthSettings;
import io.gravitee.am.model.jose.JWK;
import io.gravitee.am.model.oidc.JWKSet;
import io.gravitee.am.model.Reference;
import io.gravitee.am.service.ApplicationService;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.BlueprintAgentService;
import io.gravitee.am.service.exception.ApplicationNotFoundException;
import io.gravitee.am.service.exception.InvalidClientMetadataException;
import io.gravitee.am.service.reporter.builder.AgentAuditBuilder;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.reactivex.rxjava3.core.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Manages the public key(s) used to verify agent jwt-bearer client assertions
 * for a blueprint application. Keys are stored on the application's existing
 * {@link ApplicationOAuthSettings#getJwks()} (the same surface used by
 * {@code private_key_jwt} client authentication); agent-specific bounds
 * (e.g. {@code maxPublicKeysPerWorkload}) are read from {@link AgentSettings}.
 */
@Component
public class BlueprintAgentServiceImpl implements BlueprintAgentService {

    private static final Logger LOGGER = LoggerFactory.getLogger(BlueprintAgentServiceImpl.class);

    @Autowired
    @Lazy
    private ApplicationService applicationService;

    @Autowired
    private AuditService auditService;

    @Override
    public Single<Application> addAgentKey(String applicationId, JWK key, User principal) {
        return applicationService.findById(applicationId)
                .switchIfEmpty(Single.error(new ApplicationNotFoundException(applicationId)))
                .flatMap(application -> requireAgent(application)
                        .flatMap(ctx -> {
                            if (key.getKid() == null || key.getKid().isBlank()) {
                                return Single.error(new InvalidClientMetadataException("JWK must have a kid"));
                            }

                            JWKSet jwks = ctx.oauth().getJwks();
                            if (jwks == null) {
                                jwks = new JWKSet();
                                jwks.setKeys(new ArrayList<>());
                                ctx.oauth().setJwks(jwks);
                            }
                            if (jwks.getKeys() == null) {
                                jwks.setKeys(new ArrayList<>());
                            }

                            boolean kidExists = jwks.getKeys().stream()
                                    .anyMatch(k -> key.getKid().equals(k.getKid()));
                            if (kidExists) {
                                return Single.error(new InvalidClientMetadataException("A key with kid '" + key.getKid() + "' already exists"));
                            }

                            int maxKeys = ctx.agent().getMaxPublicKeysPerWorkload();
                            if (jwks.getKeys().size() >= maxKeys) {
                                return Single.error(new InvalidClientMetadataException(
                                        "Maximum number of public keys (" + maxKeys + ") reached"));
                            }

                            jwks.getKeys().add(key);
                            return applicationService.update(application)
                                    .doOnSuccess(app -> auditService.report(AuditBuilder.builder(AgentAuditBuilder.class)
                                            .keyAdded()
                                            .principal(principal)
                                            .reference(Reference.domain(app.getDomain()))
                                            .blueprintId(app.getSettings() != null && app.getSettings().getOauth() != null
                                                    ? app.getSettings().getOauth().getClientId() : app.getId())
                                            .assertionKid(key.getKid())));
                        }));
    }

    @Override
    public Single<Application> removeAgentKey(String applicationId, String kid, User principal) {
        return applicationService.findById(applicationId)
                .switchIfEmpty(Single.error(new ApplicationNotFoundException(applicationId)))
                .flatMap(application -> requireAgent(application)
                        .flatMap(ctx -> {
                            JWKSet jwks = ctx.oauth().getJwks();
                            if (jwks == null || jwks.getKeys() == null) {
                                return Single.error(new InvalidClientMetadataException("No keys found on this application"));
                            }

                            boolean removed = jwks.getKeys().removeIf(k -> kid.equals(k.getKid()));
                            if (!removed) {
                                return Single.error(new InvalidClientMetadataException("Key with kid '" + kid + "' not found"));
                            }

                            return applicationService.update(application)
                                    .doOnSuccess(app -> auditService.report(AuditBuilder.builder(AgentAuditBuilder.class)
                                            .keyRemoved()
                                            .principal(principal)
                                            .reference(Reference.domain(app.getDomain()))
                                            .blueprintId(app.getSettings() != null && app.getSettings().getOauth() != null
                                                    ? app.getSettings().getOauth().getClientId() : app.getId())
                                            .assertionKid(kid)));
                        }));
    }

    @Override
    public Single<List<JWK>> listAgentKeys(String applicationId) {
        return applicationService.findById(applicationId)
                .switchIfEmpty(Single.error(new ApplicationNotFoundException(applicationId)))
                .flatMap(this::requireAgent)
                .map(ctx -> {
                    JWKSet jwks = ctx.oauth().getJwks();
                    if (jwks == null || jwks.getKeys() == null) {
                        return Collections.<JWK>emptyList();
                    }
                    return jwks.getKeys();
                });
    }

    private record AgentContext(AgentSettings agent, ApplicationOAuthSettings oauth) {}

    private Single<AgentContext> requireAgent(Application application) {
        if (application.getSettings() == null
                || application.getSettings().getAdvanced() == null
                || !application.getSettings().getAdvanced().isAgentIdentityMode()) {
            return Single.error(new InvalidClientMetadataException("Application is not in agent identity mode"));
        }
        AgentSettings agent = application.getSettings().getAgent();
        if (agent == null) {
            return Single.error(new InvalidClientMetadataException("Application has no agent settings configured"));
        }
        ApplicationOAuthSettings oauth = application.getSettings().getOauth();
        if (oauth == null) {
            oauth = new ApplicationOAuthSettings();
            application.getSettings().setOauth(oauth);
        }
        return Single.just(new AgentContext(agent, oauth));
    }
}
