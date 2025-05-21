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

import io.gravitee.am.common.audit.EventType;
import io.gravitee.am.common.event.Action;
import io.gravitee.am.common.event.Type;
import io.gravitee.am.common.utils.SecureRandomString;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.model.Application;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.SecretExpirationSettings;
import io.gravitee.am.model.application.ApplicationSecretSettings;
import io.gravitee.am.model.application.ClientSecret;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.service.ApplicationSecretService;
import io.gravitee.am.service.ApplicationService;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.EventService;
import io.gravitee.am.service.exception.AbstractManagementException;
import io.gravitee.am.service.exception.ClientSecretDeleteException;
import io.gravitee.am.service.exception.ClientSecretInvalidException;
import io.gravitee.am.service.exception.ClientSecretNotFoundException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.exception.TooManyClientSecretsException;
import io.gravitee.am.service.model.NewClientSecret;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.management.ApplicationAuditBuilder;
import io.gravitee.am.service.spring.application.ApplicationSecretConfig;
import io.gravitee.am.service.spring.application.SecretHashAlgorithm;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

import static io.gravitee.am.common.oidc.ClientAuthenticationMethod.CLIENT_SECRET_JWT;
import static java.util.Optional.ofNullable;

@Component
@Slf4j
public class ApplicationSecretServiceImpl implements ApplicationSecretService {

    @Value("${applications.secretsMax:10}")
    private int secretsMax;

    @Autowired
    private ApplicationService applicationService;

    @Autowired
    private ApplicationSecretConfig applicationSecretConfig;

    @Autowired
    private AuditService auditService;

    @Autowired
    private SecretService secretService;

    @Autowired
    private EventService eventService;

    @Override
    public Single<ClientSecret> create(Domain domain, Application application, NewClientSecret newClientSecret, User principal) {
        List<ClientSecret> secrets = application.getSecrets();
        String newSecretName = newClientSecret.getName().trim();
        if (secrets.stream().map(ClientSecret::getName).anyMatch(n -> n.equals(newSecretName))) {
            return Single.error(() -> new ClientSecretInvalidException(String.format("Secret with description %s already exists", newSecretName)));
        }
        if (secrets.size() == secretsMax) {
            return Single.error(() -> new TooManyClientSecretsException(secretsMax));
        }
        final var rawSecret = SecureRandomString.generate();

        final ApplicationSecretSettings secretSettings = generateSecretSettings(application);
        // Adding the SecretSettings into the App config if the settings are different from others.
        if (!doesAppReferenceSecretSettings(application, secretSettings)) {
            application.getSecretSettings().add(secretSettings);
        }
        SecretExpirationSettings applicationSecretExpirationSettings;
        if (application.getSettings() == null) {
            applicationSecretExpirationSettings = null;
        } else {
            applicationSecretExpirationSettings = application.getSettings().getSecretExpirationSettings();
        }
        ClientSecret clientSecret = this.secretService.generateClientSecret(newSecretName, rawSecret, secretSettings, domain.getSecretExpirationSettings(), applicationSecretExpirationSettings);
        application.getSecrets().add(clientSecret);
        return applicationService.update(application)
                .doOnSuccess(updatedApplication -> auditService.report(AuditBuilder.builder(ApplicationAuditBuilder.class).principal(principal).type(EventType.APPLICATION_CLIENT_SECRET_CREATED).application(updatedApplication)))
                .doOnError(throwable -> auditService.report(AuditBuilder.builder(ApplicationAuditBuilder.class).principal(principal).reference(Reference.domain(domain.getId())).type(EventType.APPLICATION_CLIENT_SECRET_CREATED).throwable(throwable)))
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }
                    log.error("An error occurs while trying to create client secret for application {} and domain {}", application.getId(), domain, ex);
                    return Single.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to renew client secret for application %s and domain %s", application.getId(), domain), ex));
                }).flatMap(application1 -> {
                    Event event = new Event(Type.APPLICATION_SECRET, new Payload(clientSecret.getId(), ReferenceType.APPLICATION, application1.getId(), Action.CREATE));
                    return eventService.create(event).flatMap(domain1 -> Single.just(application1));})
                .map(__ -> {
                    //Replace secret with raw secret to be able to copy by user.
                    clientSecret.setSecret(rawSecret);
                    return clientSecret;
                });
    }

    @Override
    public Flowable<ClientSecret> findAllByApplication(Application application) {
        return Flowable.fromIterable(application.getSecrets())
                .map(ClientSecret::safeSecret);
    }

    @Override
    public Single<Application> renew(Domain domain, Application application, String id, User principal) {
        log.debug("Renew client secret for application {} and domain {}", application.getId(), domain);

        // check application
        if (application.getSettings() == null) {
            return Single.error(new IllegalStateException("Application settings is undefined"));
        }
        if (application.getSettings().getOauth() == null) {
            return Single.error(new IllegalStateException("Application OAuth 2.0 settings is undefined"));
        }

        Optional<ClientSecret> clientSecretOptional = application.getSecrets().stream().filter(clientSecret -> clientSecret.getId().equals(id)).findFirst();
        if (clientSecretOptional.isEmpty()) {
            return Single.error(new ClientSecretNotFoundException(id));
        }
        var clientSecret = clientSecretOptional.get();

        final ApplicationSecretSettings secretSettings = generateSecretSettings(application);
        // Adding the SecretSettings into the App config if the settings are different than others.
        if (!doesAppReferenceSecretSettings(application, secretSettings)) {
            application.getSecretSettings().add(secretSettings);
        }

        final var rawSecret = SecureRandomString.generate();

        clientSecret.setSecret(secretService.getOrCreatePasswordEncoder(secretSettings).encode(rawSecret));
        clientSecret.setExpiresAt(secretService.determinateExpireDate(domain.getSecretExpirationSettings(), application.getSettings().getSecretExpirationSettings()));
        clientSecret.setSettingsId(secretSettings.getId());

        return applicationService.update(application)
                .doOnSuccess(updatedApplication -> auditService.report(AuditBuilder.builder(ApplicationAuditBuilder.class).principal(principal).type(EventType.APPLICATION_CLIENT_SECRET_RENEWED).application(updatedApplication)))
                .doOnError(throwable -> auditService.report(AuditBuilder.builder(ApplicationAuditBuilder.class).principal(principal).reference(Reference.domain(domain.getId())).type(EventType.APPLICATION_CLIENT_SECRET_RENEWED).throwable(throwable)))
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }
                    log.error("An error occurs while trying to renew client secret for application {} and domain {}", application.getId(), domain, ex);
                    return Single.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to renew client secret for application %s and domain %s", application.getId(), domain), ex));
                }).flatMap(application1 -> {
                    Event event = new Event(Type.APPLICATION_SECRET, new Payload(clientSecret.getId(), ReferenceType.APPLICATION, application1.getId(), Action.UPDATE));
                    return eventService.create(event).flatMap(domain1 -> Single.just(application1));})
                .map(app -> {
                    //Replace secret with raw secret to be able to copy by user.
                    var secret = app.getSecrets().stream().filter(s -> s.getId().equals(id)).findFirst().orElse(new ClientSecret());
                    secret.setSecret(rawSecret);
                    return app;
                });
    }

    @Override
    public Completable delete(Domain domain, Application application, String id, User principal) {
        if (application.getSecrets().size() == 1) {
            return Completable.error(() -> new ClientSecretDeleteException("Cannot remove last secret"));
        }
        var secretToRemoveOptional = application.getSecrets().stream().filter(sc -> sc.getId().equals(id)).findFirst();

        if (secretToRemoveOptional.isEmpty()) {
            return Completable.error(() -> new ClientSecretNotFoundException(id));
        }
        var secretToRemove = secretToRemoveOptional.get();
        String secretSettingsId = secretToRemove.getSettingsId();

        // Remove secret settings if is no longer used by any other secret.
        application.getSecrets().removeIf(cs -> cs.getId().equals(id));
        boolean isSecretSettingsStillUsed = application.getSecrets().stream()
                .anyMatch(cs -> cs.getSettingsId().equals(secretSettingsId));
        // If not used by any other client secret, remove the corresponding secret settings.
        if (!isSecretSettingsStillUsed) {
            application.getSecretSettings().removeIf(ss -> ss.getId().equals(secretSettingsId));
        }

        return applicationService.update(application)
                .doOnSuccess(updatedApplication -> auditService.report(AuditBuilder.builder(ApplicationAuditBuilder.class).principal(principal).type(EventType.APPLICATION_CLIENT_SECRET_DELETED).application(updatedApplication)))
                .doOnError(throwable -> auditService.report(AuditBuilder.builder(ApplicationAuditBuilder.class).principal(principal).reference(Reference.domain(domain.getId())).type(EventType.APPLICATION_CLIENT_SECRET_DELETED).throwable(throwable)))
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }
                    log.error("An error occurs while trying to delete client secret for application {} and domain {}", application.getId(), domain, ex);
                    return Single.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to delete client secret for application %s and domain %s", application.getId(), domain), ex));
                }).flatMap(application1 -> {
                    Event event = new Event(Type.APPLICATION_SECRET, new Payload(id, ReferenceType.APPLICATION, application1.getId(), Action.DELETE));
                    return eventService.create(event).flatMap(domain1 -> Single.just(application1));})
                .ignoreElement();
    }

    @Override
    public Maybe<ClientSecret> findById(String applicationId, String id) {
        return applicationService.findById(applicationId)
                .flatMap(application -> application.getSecrets().stream()
                        .filter(secret -> secret.getId().equals(id))
                        .findFirst()
                        .map(ClientSecret::safeSecret)
                        .map(Maybe::just)
                        .orElseGet(Maybe::empty));
    }

    private static boolean doesAppReferenceSecretSettings(Application application, ApplicationSecretSettings secretSettings) {
        return ofNullable(application.getSecretSettings())
                .map(settings -> settings
                        .stream()
                        .anyMatch(conf -> conf.getId() != null && conf.getId().equals(secretSettings.getId()))
                ).orElse(false);
    }


    private ApplicationSecretSettings generateSecretSettings(Application application) {
        var secretSettings = this.applicationSecretConfig.toSecretSettings();
        if (!SecretHashAlgorithm.NONE.name().equals(secretSettings.getAlgorithm()) && CLIENT_SECRET_JWT.equals(application.getSettings().getOauth().getTokenEndpointAuthMethod())) {
            // client exist with client_secret_jwt authentication method,
            // we force the None algorithm to allow secret renewal
            secretSettings = ApplicationSecretConfig.buildNoneSecretSettings();
        }
        return secretSettings;
    }
}
