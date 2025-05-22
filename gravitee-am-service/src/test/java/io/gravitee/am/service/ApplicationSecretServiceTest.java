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

import io.gravitee.am.common.oidc.ClientAuthenticationMethod;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.model.Application;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.application.ApplicationOAuthSettings;
import io.gravitee.am.model.application.ApplicationSecretSettings;
import io.gravitee.am.model.application.ApplicationSettings;
import io.gravitee.am.model.application.ClientSecret;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.service.exception.ClientSecretDeleteException;
import io.gravitee.am.service.exception.ClientSecretNotFoundException;
import io.gravitee.am.service.exception.TooManyClientSecretsException;
import io.gravitee.am.service.impl.ApplicationSecretServiceImpl;
import io.gravitee.am.service.impl.SecretService;
import io.gravitee.am.service.model.NewClientSecret;
import io.gravitee.am.service.spring.application.ApplicationSecretConfig;
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

    private final static Domain DOMAIN = new Domain("domain1");

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
