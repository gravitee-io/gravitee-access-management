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
package io.gravitee.am.service;

import io.gravitee.am.common.audit.EventType;
import io.gravitee.am.common.env.RepositoriesEnvironment;
import io.gravitee.am.common.event.Action;
import io.gravitee.am.common.event.Type;
import io.gravitee.am.common.oidc.ClientAuthenticationMethod;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.model.Application;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ManagedBy;
import io.gravitee.am.model.SecretExpirationSettings;
import io.gravitee.am.model.application.ApplicationOAuthSettings;
import io.gravitee.am.model.application.ApplicationSecretSettings;
import io.gravitee.am.model.application.ApplicationSettings;
import io.gravitee.am.model.application.ClientSecret;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.repository.Scope;
import io.gravitee.am.service.exception.ApplicationConcurrentModificationException;
import io.gravitee.am.service.exception.ApplicationNotFoundException;
import io.gravitee.am.service.exception.ClientSecretDeleteException;
import io.gravitee.am.service.exception.ClientSecretInvalidException;
import io.gravitee.am.service.exception.ClientSecretNotFoundException;
import io.gravitee.am.service.exception.InvalidParameterException;
import io.gravitee.am.service.exception.TooManyClientSecretsException;
import io.gravitee.am.service.impl.ApplicationSecretServiceImpl;
import io.gravitee.am.service.impl.SecretService;
import io.gravitee.am.service.model.NewClientSecret;
import io.gravitee.am.service.spring.application.ApplicationSecretConfig;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import io.reactivex.rxjava3.subscribers.TestSubscriber;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApplicationSecretServiceTest {

    @InjectMocks
    private final ApplicationSecretService applicationSecretService = new ApplicationSecretServiceImpl();

    @Mock
    private ApplicationService applicationService;

    @Mock
    private AuditService auditService;

    @Mock
    private EventService eventService;

    @Spy
    private SecretService secretService = new SecretService();

    @Spy
    private ApplicationSecretConfig applicationSecretConfig = new ApplicationSecretConfig("BCrypt", mock(ConfigurableEnvironment.class));

    @Mock
    private RepositoriesEnvironment repositoriesEnvironment;

    private final static Domain DOMAIN = new Domain("domain1");

    private static final String VALID_SECRET = "s".repeat(43);

    @BeforeEach
    public void initValues() {
        ReflectionTestUtils.setField(applicationSecretService, "secretsMax", 10);
    }

    @Test
    public void shouldCreateNewSecret() {
        Application client = applicationWithSecret(1);

        when(applicationService.update(any())).thenAnswer(invocation -> Single.just(invocation.getArgument(0)));
        when(eventService.create(any())).thenReturn(Single.just(new Event()));

        NewClientSecret newClientSecret = new NewClientSecret();
        newClientSecret.setName("new-secret");

        TestObserver<ClientSecret> testObserver = applicationSecretService.create(DOMAIN, client, newClientSecret, new DefaultUser()).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(applicationService, times(1)).update(argThat(app ->
                app.getSecrets() != null &&
                        app.getSecrets().size() == 2 &&
                        app.getSecrets().get(1).getName().equals("new-secret")));
    }

    @Test
    public void shouldCreateNewSecret_andSecretSettingIfChanged() {
        //Default Alg for those tests is BCrypt
        Application client = applicationWithSecret(1);

        when(applicationService.update(any())).thenAnswer(invocation -> Single.just(invocation.getArgument(0)));
        when(eventService.create(any())).thenReturn(Single.just(new Event()));

        NewClientSecret newClientSecret = new NewClientSecret();
        newClientSecret.setName("new-secret");

        TestObserver<ClientSecret> testObserver = applicationSecretService.create(DOMAIN, client, newClientSecret, new DefaultUser()).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(applicationService, times(1)).update(argThat(app ->
                app.getSecrets() != null &&
                        app.getSecrets().size() == 2 &&
                        app.getSecrets().get(1).getName().equals("new-secret") &&
                        app.getSecretSettings() != null &&
                        app.getSecretSettings().size() == 2 &&
                        app.getSecretSettings().stream().anyMatch(ss -> ss.getAlgorithm().equals("BCRYPT"))
        ));
    }

    @Test
    public void shouldNotCreate_limitReached() {
        Application client = applicationWithSecret(10);

        NewClientSecret newClientSecret = new NewClientSecret();
        newClientSecret.setName("new-secret");

        TestObserver<ClientSecret> testObserver = applicationSecretService.create(DOMAIN, client, newClientSecret, new DefaultUser()).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertError(TooManyClientSecretsException.class);
    }


    @Test
    public void shouldRenewSecret() {
        Application client = applicationWithSecret(1);

        when(applicationService.update(any(Application.class))).thenReturn(Single.just(client));
        when(eventService.create(any())).thenReturn(Single.just(new Event()));

        TestObserver<Application> testObserver = applicationSecretService.renew(DOMAIN, client, "secret-id0", new DefaultUser()).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(app -> app.getSecrets().stream().noneMatch(s -> s.getSecret().equals("secret123")));

        verify(applicationService, times(1)).update(any());
    }

    @Test
    public void shouldRenew_withNewSecretSettings() {
        Application client = applicationWithSecret(1);

        when(applicationService.update(any(Application.class))).thenReturn(Single.just(client));
        when(eventService.create(any())).thenReturn(Single.just(new Event()));

        TestObserver<Application> testObserver = applicationSecretService.renew(DOMAIN, client, "secret-id0", new DefaultUser()).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(app -> app.getSecrets().stream().noneMatch(s -> s.getSecret().equals("secret123") && !s.getSettingsId().equals("settings-id")));

        verify(applicationService, times(1)).update(any());
    }

    @Test
    public void shouldNotRenew_clientSecretNotFound() {
        Application client = applicationWithSecret(1);

        TestObserver<Application> testObserver = applicationSecretService.renew(DOMAIN, client, "dummy-id", new DefaultUser()).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertError(ClientSecretNotFoundException.class);
    }

    @Test
    public void shouldReturnAllClientSecrets_withoutSecrets() {
        Application client = applicationWithSecret(5);

        TestSubscriber<ClientSecret> testSubscriber = applicationSecretService.findAllByApplication(client).test();
        testSubscriber.awaitDone(10, TimeUnit.SECONDS);

        testSubscriber.assertComplete();
        testSubscriber.assertNoErrors();
        testSubscriber.assertValueAt(1, s -> s.getSecret() == null);
    }

    /**
     * Since we introduce the client secret hashing, the renew secret action for an existing application
     * using client_secret_jwt as auth method will generate a None hashed secret as we need it to validate
     * the jwt signature. (for this Test Class default algo for client secret hash is BCrypt)
     */
    @Test
    public void shouldRenewSecret_withNone_If_client_secret_jwt_method() {
        Application client = applicationWithSecret(1);
        ApplicationSettings applicationSettings = new ApplicationSettings();
        ApplicationOAuthSettings applicationOAuthSettings = new ApplicationOAuthSettings();
        applicationOAuthSettings.setTokenEndpointAuthMethod(ClientAuthenticationMethod.CLIENT_SECRET_JWT);
        applicationSettings.setOauth(applicationOAuthSettings);
        client.setSettings(applicationSettings);

        when(applicationService.update(any(Application.class))).thenReturn(Single.just(client));
        when(eventService.create(any())).thenReturn(Single.just(new Event()));

        TestObserver<Application> testObserver = applicationSecretService.renew(DOMAIN, client, "secret-id0", new DefaultUser()).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(applicationService, times(1)).update(any(Application.class));
        verify(applicationService, times(1)).update(argThat(app ->
                app.getSettings() != null &&
                        app.getSecretSettings() != null &&
                        app.getSecretSettings().get(0).getAlgorithm().equalsIgnoreCase("none") &&
                        app.getSecrets() != null &&
                        !app.getSecrets().isEmpty())
        );
    }

    @Test
    public void shouldDeleteClientSecret() {
        Application client = applicationWithSecret(2);

        when(applicationService.update(any())).thenAnswer(invocation -> Single.just(invocation.getArgument(0)));
        when(eventService.create(any())).thenReturn(Single.just(new Event()));

        TestObserver<Void> testObserver = applicationSecretService.delete(DOMAIN, client, "secret-id1", new DefaultUser()).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(applicationService, times(1)).update(argThat(app ->
                app.getSecrets() != null &&
                        app.getSecrets().size() == 1 &&
                        app.getSecrets().getFirst().getName().equals("name0")));
    }

    @Test
    public void shouldNotDeleteClientSecret_lastSecret() {
        Application client = applicationWithSecret(1);

        TestObserver<Void> testObserver = applicationSecretService.delete(DOMAIN, client, "secret-id0", new DefaultUser()).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertError(ClientSecretDeleteException.class);
    }

    @Test
    public void shouldDeleteClientSecret_andCorrespondingSecretSettings() {
        Application client = applicationWithSecret(2);

        when(applicationService.update(any())).thenAnswer(invocation -> Single.just(invocation.getArgument(0)));
        when(eventService.create(any())).thenReturn(Single.just(new Event()));

        TestObserver<Void> testObserver = applicationSecretService.delete(DOMAIN, client, "secret-id1", new DefaultUser()).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(applicationService, times(1)).update(argThat(app ->
                app.getSecrets() != null &&
                        app.getSecrets().size() == 1 &&
                        app.getSecrets().getFirst().getName().equals("name0") &&
                        app.getSecretSettings().size() == 1 &&
                        app.getSecretSettings().getFirst().getAlgorithm().equalsIgnoreCase("none")));
    }

    @Test
    public void shouldNotCreateSecret_applicationFromAnotherDomain() {
        Application client = applicationWithSecret(1);
        client.setDomain("other-domain");
        NewClientSecret newClientSecret = new NewClientSecret();
        newClientSecret.setName("new-secret");

        applicationSecretService.create(DOMAIN, client, newClientSecret, new DefaultUser()).test()
                .awaitDone(10, TimeUnit.SECONDS)
                .assertError(ApplicationNotFoundException.class);

        verify(applicationService, never()).update(any());
    }

    @Test
    public void shouldNotRenewSecret_applicationFromAnotherDomain() {
        Application client = applicationWithSecret(1);
        client.setDomain("other-domain");

        applicationSecretService.renew(DOMAIN, client, "secret-id0", new DefaultUser()).test()
                .awaitDone(10, TimeUnit.SECONDS)
                .assertError(ApplicationNotFoundException.class);

        verify(applicationService, never()).update(any());
    }

    @Test
    public void shouldNotDeleteSecret_applicationFromAnotherDomain() {
        Application client = applicationWithSecret(2);
        client.setDomain("other-domain");

        applicationSecretService.delete(DOMAIN, client, "secret-id1", new DefaultUser()).test()
                .awaitDone(10, TimeUnit.SECONDS)
                .assertError(ApplicationNotFoundException.class);

        verify(applicationService, never()).update(any());
    }

    @Test
    public void shouldApplyAutomationSecret() {
        Application client = applicationWithSecret(1);
        stubRead(client);
        stubSuccessfulWrite();

        TestObserver<ClientSecret> testObserver = applicationSecretService.apply(DOMAIN, client, automationSecret("ci-secret"), VALID_SECRET, new DefaultUser()).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertValue(secret -> "ci-secret-id".equals(secret.getId())
                && "ci-secret".equals(secret.getName())
                && "ci-secret".equals(secret.getAutomationKey())
                && secret.isManagedBy(ManagedBy.AUTOMATION_API)
                && secret.getSecret() == null);
        verify(applicationService).updateIfUnchanged(argThat(app -> app.getSecrets().size() == 2
                && app.getSecretSettings().stream().anyMatch(ss -> ss.getAlgorithm().equals("BCRYPT"))));
        verify(auditService).report(argThat(audit -> EventType.APPLICATION_CLIENT_SECRET_CREATED.equals(audit.getType())));
        verify(eventService).create(argThat(event -> event.getType() == Type.APPLICATION_SECRET
                && "ci-secret-id".equals(event.getPayload().getId())
                && event.getPayload().getAction() == Action.CREATE));
    }

    @Test
    public void shouldNotApply_valueShorterThan43Characters() {
        Application client = applicationWithSecret(1);
        stubRead(client);

        applicationSecretService.apply(DOMAIN, client, automationSecret("ci-secret"), "a".repeat(42), new DefaultUser()).test()
                .awaitDone(10, TimeUnit.SECONDS)
                .assertError(InvalidParameterException.class);

        verify(applicationService, never()).updateIfUnchanged(any());
    }

    @Test
    public void shouldNotApply_bcryptValueLongerThan72Bytes() {
        Application client = applicationWithSecret(1);
        stubRead(client);

        applicationSecretService.apply(DOMAIN, client, automationSecret("ci-secret"), "é".repeat(30) + "a".repeat(13), new DefaultUser()).test()
                .awaitDone(10, TimeUnit.SECONDS)
                .assertError(error -> error instanceof InvalidParameterException && error.getMessage().startsWith("BCrypt only uses the first 72 bytes"));
    }

    @Test
    public void shouldValidateApply_bcryptValueOf72Bytes() {
        applicationSecretService.validateApply(DOMAIN, applicationWithSecret(1), automationSecret("ci-secret"), "é".repeat(29) + "a".repeat(14)).test()
                .awaitDone(10, TimeUnit.SECONDS)
                .assertValue(secret -> "ci-secret-id".equals(secret.getId()) && secret.getSecret() == null);

        verify(applicationService, never()).updateIfUnchanged(any());
    }

    @Test
    public void shouldValidateApply_pbkdf2ValueOf100Bytes() {
        useAlgorithm("PBKDF2");

        applicationSecretService.validateApply(DOMAIN, applicationWithSecret(1), automationSecret("ci-secret"), "a".repeat(100)).test()
                .awaitDone(10, TimeUnit.SECONDS)
                .assertValueCount(1);
    }

    @Test
    public void shouldNotValidateApply_jdbcKeyLongerThan64Characters() {
        useJdbc();

        applicationSecretService.validateApply(DOMAIN, applicationWithSecret(1), automationSecret("k".repeat(65)), VALID_SECRET).test()
                .awaitDone(10, TimeUnit.SECONDS)
                .assertError(InvalidParameterException.class);
    }

    @Test
    public void shouldNotValidateApply_jdbcPlainValueLongerThan374Characters() {
        useAlgorithm("None");
        useJdbc();

        applicationSecretService.validateApply(DOMAIN, applicationWithSecret(1), automationSecret("ci-secret"), "a".repeat(375)).test()
                .awaitDone(10, TimeUnit.SECONDS)
                .assertError(InvalidParameterException.class);
    }

    @Test
    public void shouldValidateApply_mongoPlainValueLongerThan374Characters() {
        useAlgorithm("None");

        applicationSecretService.validateApply(DOMAIN, applicationWithSecret(1), automationSecret("ci-secret"), "a".repeat(375)).test()
                .awaitDone(10, TimeUnit.SECONDS)
                .assertValueCount(1);
    }

    @Test
    public void shouldValidateApply_expiryFromTheApplicationSettingsBeforeTheDomain() {
        Application client = applicationWithSecret(1);
        client.getSettings().setSecretExpirationSettings(expiration(3600L));
        Domain domain = new Domain(DOMAIN.getId());
        domain.setSecretExpirationSettings(expiration(60L));
        long before = System.currentTimeMillis();

        applicationSecretService.validateApply(domain, client, automationSecret("ci-secret"), VALID_SECRET).test()
                .awaitDone(10, TimeUnit.SECONDS)
                .assertValue(secret -> secret.getExpiresAt().getTime() >= before + 3_600_000
                        && secret.getExpiresAt().getTime() <= System.currentTimeMillis() + 3_600_000);
    }

    @Test
    public void shouldValidateApply_expiryFromTheDomainSettings() {
        Domain domain = new Domain(DOMAIN.getId());
        domain.setSecretExpirationSettings(expiration(60L));
        long before = System.currentTimeMillis();

        applicationSecretService.validateApply(domain, applicationWithSecret(1), automationSecret("ci-secret"), VALID_SECRET).test()
                .awaitDone(10, TimeUnit.SECONDS)
                .assertValue(secret -> secret.getExpiresAt().getTime() >= before + 60_000
                        && secret.getExpiresAt().getTime() <= System.currentTimeMillis() + 60_000);
    }

    @Test
    public void shouldApply_sameValueWithoutWriting() {
        Application client = applicationWithSecret(1);
        ApplicationSecretSettings settings = applicationSecretConfig.toSecretSettings();
        client.getSecretSettings().add(settings);
        ClientSecret stored = secretService.generateClientSecret("ci-secret", VALID_SECRET, settings, null, null);
        stored.setId("ci-secret-id");
        client.getSecrets().add(stored);
        stubRead(client);

        applicationSecretService.apply(DOMAIN, client, automationSecret("ci-secret"), VALID_SECRET, new DefaultUser()).test()
                .awaitDone(10, TimeUnit.SECONDS)
                .assertValue(secret -> "ci-secret-id".equals(secret.getId()) && secret.getSecret() == null);

        verify(applicationService, never()).updateIfUnchanged(any());
        verify(auditService, never()).report(any());
        verify(eventService, never()).create(any());
    }

    @Test
    public void shouldApply_sameValueStoredUnderOtherHashingSettings() {
        // stored unhashed (e.g. while the application used client_secret_jwt), longer than BCrypt accepts
        String longValue = "s".repeat(100);
        Application client = applicationWithSecret(1);
        ApplicationSecretSettings noneSettings = ApplicationSecretConfig.buildNoneSecretSettings();
        ClientSecret stored = secretService.generateClientSecret("ci-secret", longValue, noneSettings, null, null);
        stored.setId("ci-secret-id");
        client.getSecrets().add(stored);
        stubRead(client);

        applicationSecretService.apply(DOMAIN, client, automationSecret("ci-secret"), longValue, new DefaultUser()).test()
                .awaitDone(10, TimeUnit.SECONDS)
                .assertValue(secret -> "ci-secret-id".equals(secret.getId()));

        verify(applicationService, never()).updateIfUnchanged(any());
    }

    @Test
    public void shouldNotApply_differentValueForAnExistingSecret() {
        Application client = applicationWithSecret(1);
        stubRead(client);
        ClientSecret consoleSecret = new ClientSecret();
        consoleSecret.setId("secret-id0");

        applicationSecretService.apply(DOMAIN, client, consoleSecret, VALID_SECRET, new DefaultUser()).test()
                .awaitDone(10, TimeUnit.SECONDS)
                .assertError(error -> error instanceof ClientSecretInvalidException
                        && error.getMessage().equals("Client secret value is immutable; use a new key to rotate"));
    }

    @Test
    public void shouldNotApply_unknownSecretNotManagedByAutomation() {
        Application client = applicationWithSecret(1);
        stubRead(client);
        ClientSecret byId = new ClientSecret();
        byId.setId("unknown-id");

        applicationSecretService.apply(DOMAIN, client, byId, VALID_SECRET, new DefaultUser()).test()
                .awaitDone(10, TimeUnit.SECONDS)
                .assertError(ClientSecretNotFoundException.class);
    }

    @Test
    public void shouldNotApply_nameOfAnotherSecret() {
        Application client = applicationWithSecret(1);
        stubRead(client);

        applicationSecretService.apply(DOMAIN, client, automationSecret("name0"), VALID_SECRET, new DefaultUser()).test()
                .awaitDone(10, TimeUnit.SECONDS)
                .assertError(ClientSecretInvalidException.class);
    }

    @Test
    public void shouldNotApply_limitReachedWithConsoleSecrets() {
        Application client = applicationWithSecret(10);
        stubRead(client);

        applicationSecretService.apply(DOMAIN, client, automationSecret("ci-secret"), VALID_SECRET, new DefaultUser()).test()
                .awaitDone(10, TimeUnit.SECONDS)
                .assertError(TooManyClientSecretsException.class);
    }

    @Test
    public void shouldApply_withNoneSettingsForClientSecretJwt() {
        Application client = applicationWithSecret(1);
        client.getSettings().getOauth().setTokenEndpointAuthMethod(ClientAuthenticationMethod.CLIENT_SECRET_JWT);
        stubRead(client);
        stubSuccessfulWrite();

        applicationSecretService.apply(DOMAIN, client, automationSecret("ci-secret"), VALID_SECRET, new DefaultUser()).test()
                .awaitDone(10, TimeUnit.SECONDS)
                .assertComplete();

        String noneSettingsId = ApplicationSecretConfig.buildNoneSecretSettings().getId();
        verify(applicationService).updateIfUnchanged(argThat(app -> app.getSecretSettings().size() == 1
                && app.getSecrets().stream().anyMatch(secret -> "ci-secret-id".equals(secret.getId())
                && noneSettingsId.equals(secret.getSettingsId())
                && VALID_SECRET.equals(secret.getSecret()))));
    }

    @Test
    public void shouldApply_rereadingTheApplicationAfterAConflict() {
        Application client = applicationWithSecret(1);
        stubRead(client);
        when(applicationService.updateIfUnchanged(any()))
                .thenReturn(Single.error(new ApplicationConcurrentModificationException(client.getId())))
                .thenAnswer(invocation -> Single.just(invocation.getArgument(0)));
        when(eventService.create(any())).thenReturn(Single.just(new Event()));

        applicationSecretService.apply(DOMAIN, client, automationSecret("ci-secret"), VALID_SECRET, new DefaultUser()).test()
                .awaitDone(10, TimeUnit.SECONDS)
                .assertComplete();

        verify(applicationService, times(2)).findById(DOMAIN.getId(), client.getId());
        verify(auditService, times(1)).report(any());
        verify(eventService, times(1)).create(any());
    }

    @Test
    public void shouldNotApply_applicationFromAnotherDomain() {
        Application client = applicationWithSecret(1);
        when(applicationService.findById(DOMAIN.getId(), client.getId())).thenReturn(Maybe.empty());

        applicationSecretService.apply(DOMAIN, client, automationSecret("ci-secret"), VALID_SECRET, new DefaultUser()).test()
                .awaitDone(10, TimeUnit.SECONDS)
                .assertError(ApplicationNotFoundException.class);
    }

    @Test
    public void shouldRemoveTheLastSecret() {
        Application client = applicationWithSecret(1);
        stubRead(client);
        stubSuccessfulWrite();

        applicationSecretService.remove(DOMAIN, client, "secret-id0", new DefaultUser()).test()
                .awaitDone(10, TimeUnit.SECONDS)
                .assertComplete();

        verify(applicationService).updateIfUnchanged(argThat(app -> app.getSecrets().isEmpty()));
        verify(auditService).report(argThat(audit -> EventType.APPLICATION_CLIENT_SECRET_DELETED.equals(audit.getType())));
        verify(eventService).create(argThat(event -> "secret-id0".equals(event.getPayload().getId())
                && event.getPayload().getAction() == Action.DELETE));
    }

    @Test
    public void shouldRemove_missingSecretWithoutWriting() {
        Application client = applicationWithSecret(1);
        stubRead(client);

        applicationSecretService.remove(DOMAIN, client, "unknown-id", new DefaultUser()).test()
                .awaitDone(10, TimeUnit.SECONDS)
                .assertComplete();

        verify(applicationService, never()).updateIfUnchanged(any());
        verify(auditService, never()).report(any());
    }

    private void stubRead(Application application) {
        when(applicationService.findById(DOMAIN.getId(), application.getId())).thenAnswer(invocation -> Maybe.just(new Application(application)));
    }

    private void stubSuccessfulWrite() {
        when(applicationService.updateIfUnchanged(any())).thenAnswer(invocation -> Single.just(invocation.getArgument(0)));
        when(eventService.create(any())).thenReturn(Single.just(new Event()));
    }

    private void useAlgorithm(String algorithm) {
        ReflectionTestUtils.setField(applicationSecretService, "applicationSecretConfig", new ApplicationSecretConfig(algorithm, mock(ConfigurableEnvironment.class)));
    }

    private void useJdbc() {
        when(repositoriesEnvironment.getProperty(Scope.MANAGEMENT.getRepositoryPropertyKey() + ".type")).thenReturn("jdbc");
    }

    private static SecretExpirationSettings expiration(long seconds) {
        SecretExpirationSettings settings = new SecretExpirationSettings();
        settings.setEnabled(true);
        settings.setExpiryTimeSeconds(seconds);
        return settings;
    }

    private static ClientSecret automationSecret(String key) {
        ClientSecret secret = new ClientSecret();
        secret.setId(key + "-id");
        secret.setName(key);
        secret.setAutomationKey(key);
        secret.setManagedBy(ManagedBy.AUTOMATION_API);
        return secret;
    }

    private static Application emptyAppWithDomain() {
        Application app = new Application();
        app.setDomain(DOMAIN.getId());
        return app;
    }

    private static Application applicationWithSecret(int secretsCount) {
        Application client = emptyAppWithDomain();
        ApplicationSettings applicationSettings = new ApplicationSettings();
        ApplicationOAuthSettings applicationOAuthSettings = new ApplicationOAuthSettings();
        applicationSettings.setOauth(applicationOAuthSettings);
        client.setSettings(applicationSettings);
        List<ApplicationSecretSettings> secretSettings = new ArrayList<>();
        secretSettings.add(ApplicationSecretConfig.buildNoneSecretSettings());
        client.setSecretSettings(secretSettings);
        List<ClientSecret> clientSecrets = new ArrayList<>();
        for (int i = 0; i < secretsCount; i++) {
            clientSecrets.add(generateClientSecret("name" + i, "secret-id" + i, "settings-id"));
        }
        client.setSecrets(clientSecrets);
        return client;
    }

    private static ClientSecret generateClientSecret(String name, String secretId, String settingsId) {
        var clientSecret = new ClientSecret();
        clientSecret.setSecret("secret123");
        clientSecret.setName(name);
        clientSecret.setSettingsId(settingsId);
        clientSecret.setId(secretId);
        clientSecret.setCreatedAt(new Date());
        return clientSecret;
    }
}
