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
import io.gravitee.am.common.env.RepositoriesEnvironment;
import io.gravitee.am.common.event.Action;
import io.gravitee.am.common.event.Type;
import io.gravitee.am.common.utils.SecureRandomString;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.model.Application;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ManagedBy;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.SecretExpirationSettings;
import io.gravitee.am.model.application.ApplicationSecretSettings;
import io.gravitee.am.model.application.ClientSecret;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.repository.Scope;
import io.gravitee.am.service.ApplicationSecretService;
import io.gravitee.am.service.ApplicationService;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.EventService;
import io.gravitee.am.service.exception.AbstractManagementException;
import io.gravitee.am.service.exception.ApplicationNotFoundException;
import io.gravitee.am.service.exception.ClientSecretDeleteException;
import io.gravitee.am.service.exception.ClientSecretInvalidException;
import io.gravitee.am.service.exception.ClientSecretNotFoundException;
import io.gravitee.am.service.exception.InvalidParameterException;
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
import io.reactivex.rxjava3.functions.Supplier;
import lombok.CustomLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static io.gravitee.am.common.oidc.ClientAuthenticationMethod.CLIENT_SECRET_JWT;
import static io.gravitee.am.service.utils.OptimisticConcurrency.isConcurrentModification;
import static io.gravitee.am.service.utils.OptimisticConcurrency.retryOnConcurrentModification;
import static java.util.Optional.ofNullable;

@Component
@CustomLog
public class ApplicationSecretServiceImpl implements ApplicationSecretService {

    private static final int MIN_SECRET_LENGTH = 43;
    private static final int BCRYPT_MAX_BYTES = 72;
    // widths of application_client_secrets.name and .secret
    private static final int JDBC_MAX_KEY_LENGTH = 64;
    private static final int JDBC_MAX_PLAIN_SECRET_LENGTH = 374;
    private static final String MANAGEMENT_TYPE = Scope.MANAGEMENT.getRepositoryPropertyKey() + ".type";
    private static final String JDBC = "jdbc";

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

    @Autowired
    private RepositoriesEnvironment repositoriesEnvironment;

    @Override
    public Single<ClientSecret> create(Domain domain, Application application, NewClientSecret newClientSecret, User principal) {
        if (!belongsToDomain(application, domain)) {
            return Single.error(() -> new ApplicationNotFoundException(application.getId()));
        }
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
        addSecretSettingsIfMissing(application, secretSettings);
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

    private static boolean belongsToDomain(Application application, Domain domain) {
        return domain != null && domain.getId() != null && domain.getId().equals(application.getDomain());
    }

    @Override
    public Flowable<ClientSecret> findAllByApplication(Application application) {
        return Flowable.fromIterable(application.getSecrets())
                .map(ClientSecret::safeSecret);
    }

    @Override
    public Single<Application> renew(Domain domain, Application application, String id, User principal) {
        log.debug("Renew client secret for application {} and domain {}", application.getId(), domain);
        if (!belongsToDomain(application, domain)) {
            return Single.error(() -> new ApplicationNotFoundException(application.getId()));
        }

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
        addSecretSettingsIfMissing(application, secretSettings);

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
        if (!belongsToDomain(application, domain)) {
            return Completable.error(() -> new ApplicationNotFoundException(application.getId()));
        }
        if (application.getSecrets().size() == 1) {
            return Completable.error(() -> new ClientSecretDeleteException("Cannot remove last secret"));
        }
        var secretToRemoveOptional = application.getSecrets().stream().filter(sc -> sc.getId().equals(id)).findFirst();

        if (secretToRemoveOptional.isEmpty()) {
            return Completable.error(() -> new ClientSecretNotFoundException(id));
        }
        removeSecret(application, secretToRemoveOptional.get());

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

    @Override
    public Single<ClientSecret> validateApply(Domain domain, Application application, ClientSecret desired, String rawSecret) {
        return Single.fromCallable(() -> {
            if (!belongsToDomain(application, domain)) {
                throw new ApplicationNotFoundException(application.getId());
            }
            ApplicationSecretSettings secretSettings = generateSecretSettings(application);
            return findUnchangedSecret(application, desired, rawSecret, secretSettings)
                    .orElseGet(() -> buildSecret(domain, application, desired, rawSecret, secretSettings))
                    .safeSecret();
        });
    }

    @Override
    public Single<ClientSecret> apply(Domain domain, Application application, ClientSecret desired, String rawSecret, User principal) {
        log.debug("Apply client secret {} to application {} and domain {}", desired.getId(), application.getId(), domain.getId());
        Supplier<Single<AppliedSecret>> attempt = () -> readApplication(domain, application.getId())
                .flatMap(current -> {
                    ApplicationSecretSettings secretSettings = generateSecretSettings(current);
                    Optional<ClientSecret> unchanged = findUnchangedSecret(current, desired, rawSecret, secretSettings);
                    if (unchanged.isPresent()) {
                        return Single.just(new AppliedSecret(current, unchanged.get(), false));
                    }
                    ClientSecret secret = buildSecret(domain, current, desired, rawSecret, secretSettings);
                    addSecretSettingsIfMissing(current, secretSettings);
                    current.getSecrets().add(secret);
                    return applicationService.updateIfUnchanged(current, principal)
                            .doOnError(throwable -> reportFailure(domain, principal, EventType.APPLICATION_CLIENT_SECRET_CREATED, throwable))
                            .map(updated -> new AppliedSecret(updated, secret, true));
                });

        return retryOnConcurrentModification(attempt)
                .flatMap(applied -> {
                    if (!applied.written()) {
                        return Single.just(applied.secret());
                    }
                    auditService.report(AuditBuilder.builder(ApplicationAuditBuilder.class).principal(principal).type(EventType.APPLICATION_CLIENT_SECRET_CREATED).application(applied.application()));
                    Event event = new Event(Type.APPLICATION_SECRET, new Payload(applied.secret().getId(), ReferenceType.APPLICATION, applied.application().getId(), Action.CREATE));
                    return eventService.create(event).map(__ -> applied.secret());
                })
                .map(ClientSecret::safeSecret)
                .onErrorResumeNext(ex -> technicalError(ex, "apply", application, domain));
    }

    @Override
    public Completable remove(Domain domain, Application application, String secretId, User principal) {
        log.debug("Remove client secret {} from application {} and domain {}", secretId, application.getId(), domain.getId());
        // an application deleted meanwhile has no secret left to remove
        Supplier<Single<Optional<Application>>> attempt = () -> applicationService.findById(domain.getId(), application.getId())
                .flatMapSingle(current -> {
                    Optional<ClientSecret> secretToRemove = current.getSecrets().stream().filter(secret -> secret.getId().equals(secretId)).findFirst();
                    if (secretToRemove.isEmpty()) {
                        return Single.just(Optional.<Application>empty());
                    }
                    removeSecret(current, secretToRemove.get());
                    return applicationService.updateIfUnchanged(current, principal)
                            .doOnError(throwable -> reportFailure(domain, principal, EventType.APPLICATION_CLIENT_SECRET_DELETED, throwable))
                            .map(Optional::of);
                })
                .defaultIfEmpty(Optional.empty());

        return retryOnConcurrentModification(attempt)
                .flatMapCompletable(updated -> updated
                        .map(app -> {
                            auditService.report(AuditBuilder.builder(ApplicationAuditBuilder.class).principal(principal).type(EventType.APPLICATION_CLIENT_SECRET_DELETED).application(app));
                            Event event = new Event(Type.APPLICATION_SECRET, new Payload(secretId, ReferenceType.APPLICATION, app.getId(), Action.DELETE));
                            return eventService.create(event).ignoreElement();
                        })
                        .orElse(Completable.complete()))
                .onErrorResumeNext(ex -> technicalError(ex, "remove", application, domain).ignoreElement());
    }

    private Single<Application> readApplication(Domain domain, String applicationId) {
        return applicationService.findById(domain.getId(), applicationId)
                .switchIfEmpty(Single.error(() -> new ApplicationNotFoundException(applicationId)));
    }

    /**
     * Checks {@code desired} and {@code rawSecret} against the application.
     * An existing secret is only compared with {@code rawSecret}: the value rules apply to new secrets, so a secret
     * stored under other hashing settings can still be applied again with its value.
     *
     * @return the stored secret when it already exists with this value; empty when {@code desired} can be added
     */
    private Optional<ClientSecret> findUnchangedSecret(Application application, ClientSecret desired, String rawSecret, ApplicationSecretSettings secretSettings) {
        Optional<ClientSecret> existing = application.getSecrets().stream().filter(secret -> secret.getId().equals(desired.getId())).findFirst();
        if (existing.isPresent()) {
            if (!matches(application, existing.get(), rawSecret)) {
                throw new ClientSecretInvalidException("Client secret value is immutable; use a new key to rotate");
            }
            return existing;
        }
        validateSecretValue(desired, rawSecret, secretSettings);
        if (!desired.isManagedBy(ManagedBy.AUTOMATION_API)) {
            throw new ClientSecretNotFoundException(desired.getId());
        }
        if (application.getSecrets().stream().anyMatch(secret -> desired.getName().equals(secret.getName()))) {
            throw new ClientSecretInvalidException(String.format("Client secret key '%s' is already the name of another secret", desired.getName()));
        }
        if (application.getSecrets().size() >= secretsMax) {
            throw new TooManyClientSecretsException(secretsMax);
        }
        return Optional.empty();
    }

    private void validateSecretValue(ClientSecret desired, String rawSecret, ApplicationSecretSettings secretSettings) {
        if (rawSecret == null || rawSecret.length() < MIN_SECRET_LENGTH) {
            throw new InvalidParameterException("Client secret value must be at least " + MIN_SECRET_LENGTH + " characters");
        }
        SecretHashAlgorithm algorithm = SecretHashAlgorithm.valueOf(secretSettings.getAlgorithm());
        if (algorithm == SecretHashAlgorithm.BCRYPT && rawSecret.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES) {
            throw new InvalidParameterException("BCrypt only uses the first " + BCRYPT_MAX_BYTES + " bytes; the secret must be at most " + BCRYPT_MAX_BYTES + " bytes");
        }
        if (JDBC.equals(repositoriesEnvironment.getProperty(MANAGEMENT_TYPE))) {
            if (desired.getName() != null && desired.getName().length() > JDBC_MAX_KEY_LENGTH) {
                throw new InvalidParameterException("Client secret key must be at most " + JDBC_MAX_KEY_LENGTH + " characters");
            }
            if (algorithm == SecretHashAlgorithm.NONE && rawSecret.length() > JDBC_MAX_PLAIN_SECRET_LENGTH) {
                throw new InvalidParameterException("Client secret value must be at most " + JDBC_MAX_PLAIN_SECRET_LENGTH + " characters");
            }
        }
    }

    private boolean matches(Application application, ClientSecret secret, String rawSecret) {
        ApplicationSecretSettings storedSettings = ofNullable(application.getSecretSettings()).orElse(List.of()).stream()
                .filter(settings -> settings.getId().equals(secret.getSettingsId()))
                .findFirst()
                .orElse(null);
        return secretService.getOrCreatePasswordEncoder(storedSettings).matches(rawSecret, secret.getSecret());
    }

    /**
     * Builds the secret with the identity chosen by the caller; the expiry date is computed as for the Management API.
     */
    private ClientSecret buildSecret(Domain domain, Application application, ClientSecret desired, String rawSecret, ApplicationSecretSettings secretSettings) {
        SecretExpirationSettings applicationSecretExpirationSettings = application.getSettings() == null ? null : application.getSettings().getSecretExpirationSettings();
        ClientSecret secret = secretService.generateClientSecret(desired.getName(), rawSecret, secretSettings, domain.getSecretExpirationSettings(), applicationSecretExpirationSettings);
        secret.setId(desired.getId());
        secret.setAutomationKey(desired.getAutomationKey());
        secret.setManagedBy(desired.getManagedBy());
        return secret;
    }

    private void reportFailure(Domain domain, User principal, String eventType, Throwable throwable) {
        // a concurrent modification wrote nothing and is retried
        if (!isConcurrentModification(throwable)) {
            auditService.report(AuditBuilder.builder(ApplicationAuditBuilder.class).principal(principal).reference(Reference.domain(domain.getId())).type(eventType).throwable(throwable));
        }
    }

    private static <T> Single<T> technicalError(Throwable ex, String operation, Application application, Domain domain) {
        if (ex instanceof AbstractManagementException) {
            return Single.error(ex);
        }
        log.error("An error occurs while trying to {} client secret for application {} and domain {}", operation, application.getId(), domain.getId(), ex);
        return Single.error(new TechnicalManagementException(
                String.format("An error occurs while trying to %s client secret for application %s and domain %s", operation, application.getId(), domain.getId()), ex));
    }

    private static void addSecretSettingsIfMissing(Application application, ApplicationSecretSettings secretSettings) {
        // Adding the SecretSettings into the App config if the settings are different from others.
        if (!doesAppReferenceSecretSettings(application, secretSettings)) {
            application.getSecretSettings().add(secretSettings);
        }
    }

    private static void removeSecret(Application application, ClientSecret secretToRemove) {
        String secretSettingsId = secretToRemove.getSettingsId();

        // Remove secret settings if is no longer used by any other secret.
        application.getSecrets().removeIf(cs -> cs.getId().equals(secretToRemove.getId()));
        boolean isSecretSettingsStillUsed = application.getSecrets().stream()
                .anyMatch(cs -> cs.getSettingsId().equals(secretSettingsId));
        // If not used by any other client secret, remove the corresponding secret settings.
        if (!isSecretSettingsStillUsed) {
            application.getSecretSettings().removeIf(ss -> ss.getId().equals(secretSettingsId));
        }
    }

    private record AppliedSecret(Application application, ClientSecret secret, boolean written) {
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
        if (!SecretHashAlgorithm.NONE.name().equals(secretSettings.getAlgorithm()) && usesClientSecretJwt(application)) {
            // client exist with client_secret_jwt authentication method,
            // we force the None algorithm to allow secret renewal
            secretSettings = ApplicationSecretConfig.buildNoneSecretSettings();
        }
        return secretSettings;
    }

    private static boolean usesClientSecretJwt(Application application) {
        return application.getSettings() != null && application.getSettings().getOauth() != null
                && CLIENT_SECRET_JWT.equals(application.getSettings().getOauth().getTokenEndpointAuthMethod());
    }
}
