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
package io.gravitee.am.repository.management.api;

import io.gravitee.am.model.Application;
import io.gravitee.am.model.ChallengeSettings;
import io.gravitee.am.model.EnrollSettings;
import io.gravitee.am.model.MFASettings;
import io.gravitee.am.model.MfaChallengeType;
import io.gravitee.am.model.MfaEnrollType;
import io.gravitee.am.model.PostLoginAction;
import io.gravitee.am.model.RememberDeviceSettings;
import io.gravitee.am.model.StepUpAuthenticationSettings;
import io.gravitee.am.model.account.AccountSettings;
import io.gravitee.am.model.application.ApplicationOAuthSettings;
import io.gravitee.am.model.application.ApplicationScopeSettings;
import io.gravitee.am.model.application.ApplicationSecretSettings;
import io.gravitee.am.model.application.ApplicationSettings;
import io.gravitee.am.model.application.ApplicationType;
import io.gravitee.am.model.application.ClientSecret;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.model.idp.ApplicationIdentityProvider;
import io.gravitee.am.model.login.LoginSettings;
import io.gravitee.am.repository.management.AbstractManagementTest;
import io.reactivex.rxjava3.observers.TestObserver;
import io.reactivex.rxjava3.subscribers.TestSubscriber;
import org.junit.Test;
import org.mockito.internal.util.collections.Sets;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toCollection;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ApplicationRepositoryTest extends AbstractManagementTest {

    @Autowired
    private ApplicationRepository applicationRepository;

    @Test
    public void testFindByDomain() {
        // create application
        Application application = new Application();
        application.setName("testApp");
        application.setDomain("testDomain");
        applicationRepository.create(application).blockingGet();

        // fetch applications
        TestObserver<Page<Application>> testObserver = applicationRepository.findByDomain("testDomain", 0, Integer.MAX_VALUE).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(applicationPage -> applicationPage.getData().size() == 1);
    }

    @Test
    public void testFindByDomainAndClaim() {
        // create application
        Application application = new Application();
        application.setName("testApp");
        application.setDomain("testDomain");
        ApplicationSettings settings = new ApplicationSettings();
        application.setSettings(settings);
        ApplicationOAuthSettings oauth = new ApplicationOAuthSettings();
        settings.setOauth(oauth);
        oauth.setGrantTypes(Arrays.asList("test-grant"));
        applicationRepository.create(application).blockingGet();

        // fetch applications
        TestSubscriber<Application> testSubscriber = applicationRepository.findByDomainAndExtensionGrant("testDomain", "test-grant").test();
        testSubscriber.awaitDone(10, TimeUnit.SECONDS);

        testSubscriber.assertComplete();
        testSubscriber.assertNoErrors();
        testSubscriber.assertValueCount(1);
    }


    @Test
    public void testFindByDomainAndClientId() {
        // create application
        Application application = new Application();
        application.setName("testApp");
        application.setDomain("testDomain");
        ApplicationSettings settings = new ApplicationSettings();
        application.setSettings(settings);
        ApplicationOAuthSettings oauth = new ApplicationOAuthSettings();
        settings.setOauth(oauth);
        oauth.setClientId("clientId1");
        oauth.setGrantTypes(Arrays.asList("test-grant"));
        Application createdApplication = applicationRepository.create(application).blockingGet();

        // fetch applications
        TestObserver<Application> testObserver = applicationRepository.findByDomainAndClientId("testDomain", "clientId1").test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(app -> app.getId().equalsIgnoreCase(createdApplication.getId()));
    }

    @Test
    public void testFindByDomainPagination() {
        // create app 1
        Application app = new Application();
        app.setName("testClientId");
        app.setDomain("testDomainPagination");
        applicationRepository.create(app).blockingGet();

        // create app 2
        Application app2 = new Application();
        app2.setName("testClientId2");
        app2.setDomain("testDomainPagination");
        applicationRepository.create(app2).blockingGet();

        TestObserver<Page<Application>> testObserver = applicationRepository.findByDomain("testDomainPagination", 1, 1).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(pageApplications -> pageApplications.getTotalCount() == 2 && pageApplications.getData().size() == 1);
    }

    @Test
    public void testFindByDomainAndApplicationIdsPagination() {
        // create app 1
        Application app = new Application();
        app.setName("testClientId");
        app.setDomain("testDomainPaginationAndAppIds");
        app = applicationRepository.create(app).blockingGet();

        // create app 2
        Application app2 = new Application();
        app2.setName("testClientId2");
        app2.setDomain("testDomainPaginationAndAppIds");
        app2 = applicationRepository.create(app2).blockingGet();
        TestObserver<Page<Application>> testObserver = applicationRepository.findByDomain("testDomainPaginationAndAppIds", List.of(app2.getId()), 0, 1).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(pageApplications -> pageApplications.getTotalCount() == 1 && pageApplications.getData().size() == 1);
    }

    @Test
    public void testFindById() {
        // create app
        Application app = buildApplication();
        Application appCreated = applicationRepository.create(app).blockingGet();

        // fetch app
        TestObserver<Application> testObserver = applicationRepository.findById(appCreated.getId()).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        assertEqualsTo(app, testObserver);
    }

    @Test
    public void testFindByIdentity() {
        // create app
        Application app = buildApplication();
        Application appCreated = applicationRepository.create(app).blockingGet();

        // fetch app
        final String next = appCreated.getIdentityProviders().stream().map(ApplicationIdentityProvider::getIdentity).iterator().next();
        TestSubscriber<Application> testSubscriber = applicationRepository.findByIdentityProvider(next).test();
        testSubscriber.awaitDone(10, TimeUnit.SECONDS);

        testSubscriber.assertComplete();
        testSubscriber.assertNoErrors();
        testSubscriber.assertValueCount(1);
        testSubscriber.assertValue(s -> s.getId().equals(appCreated.getId()));
    }

    private void assertEqualsTo(Application app, TestObserver<Application> testObserver) {
        testObserver.assertValue(a -> a.getName().equals(app.getName()));
        testObserver.assertValue(a -> a.getType().equals(app.getType()));
        testObserver.assertValue(a -> a.isEnabled() == app.isEnabled());
        testObserver.assertValue(a -> a.isTemplate() == app.isTemplate());
        testObserver.assertValue(a -> a.getFactors().containsAll(app.getFactors()));
        testObserver.assertValue(a -> a.getCertificate().equals(app.getCertificate()));
        testObserver.assertValue(a -> a.getDescription().equals(app.getDescription()));
        testObserver.assertValue(a -> a.getIdentityProviders() != null);
        testObserver.assertValue(a -> a.getIdentityProviders().size() == 2);
        testObserver.assertValue(a -> a.getSettings() != null);
        testObserver.assertValue(a -> a.getSettings().getOauth() != null);
        testObserver.assertValue(a -> a.getSettings().getOauth().getGrantTypes().containsAll(Arrays.asList("authorization_code")));
        testObserver.assertValue(a -> a.getSettings().getOauth().getBackchannelAuthRequestSignAlg().equals("test"));
        testObserver.assertValue(a -> a.getSettings().getOauth().isBackchannelUserCodeParameter());
        testObserver.assertValue(a -> a.getSettings().getOauth().getBackchannelClientNotificationEndpoint().equals("ciba_endpoint"));
        testObserver.assertValue(a -> a.getSettings().getOauth().getBackchannelTokenDeliveryMode().equals("poll"));
        testObserver.assertValue(a -> a.getSettings().getOauth().getScopeSettings().size() == 1);
        testObserver.assertValue(a -> a.getSettings().getOauth().getScopeSettings().get(0).isDefaultScope());
        testObserver.assertValue(a -> a.getSettings().getOauth().getScopeSettings().get(0).getScopeApproval() == 42);
        testObserver.assertValue(a -> a.getSettings().getOauth().getScopeSettings().get(0).getScope().equals("scopename"));
        testObserver.assertValue(a -> a.getMetadata() != null);
        testObserver.assertValue(a -> a.getMetadata().containsKey("key1"));
        testObserver.assertValue(a -> a.getSettings() != null && a.getSettings().getAccount() != null );
        testObserver.assertValue(a -> a.getSettings().getAccount().isResetPasswordInvalidateTokens());
        testObserver.assertValue(a -> a.getSecretSettings().size() == 1
                && a.getSecretSettings().get(0).getAlgorithm().equals("BCrypt")
                && a.getSecretSettings().get(0).getProperties().containsKey("rounds")
                && Integer.valueOf(10).equals(a.getSecretSettings().get(0).getProperties().get("rounds")) );
        testObserver.assertValue(a -> a.getSecrets().size() == 1
                && a.getSecrets().get(0).getSecret().equals("secret value")
                && a.getSecrets().get(0).getName().equals("secret name")
                && a.getSecrets().get(0).getSettingsId().equals("settingId")
                && a.getSecrets().get(0).getCreatedAt() != null
                && a.getSecrets().get(0).getId() != null);
    }

    private static Application buildApplication() {
        String random = UUID.randomUUID().toString();
        Application app = new Application();
        app.setType(ApplicationType.NATIVE);
        app.setCertificate("cert" + random);
        app.setDescription("desc" + random);
        app.setDomain("domain" + random);
        app.setName("name" + random);
        app.setTemplate(true);
        app.setEnabled(true);
        app.setFactors(Sets.newSet("fact1" + random, "fact2" + random));
        app.setSettings(buildApplicationSettings());
        app.setIdentityProviders(getProviderSettings());
        HashMap<String, Object> metadata = new HashMap<>();
        metadata.put("key1", "value1");
        app.setMetadata(metadata);
        app.setCreatedAt(new Date());
        app.setUpdatedAt(new Date());
        app.setSecretSettings(List.of(buildApplicationSecretSettings()));
        app.setSecrets(List.of(buildClientSecret()));
        return app;
    }

    private static TreeSet<ApplicationIdentityProvider> getProviderSettings() {
         return Stream.of(getIdentityProviderSettings(), getIdentityProviderSettings())
                 .collect(toCollection(TreeSet::new));
    }

    private static ApplicationIdentityProvider getIdentityProviderSettings() {
        final ApplicationIdentityProvider applicationIdentityProvider = new ApplicationIdentityProvider();
        applicationIdentityProvider.setIdentity(UUID.randomUUID().toString());
        applicationIdentityProvider.setSelectionRule(UUID.randomUUID().toString());
        applicationIdentityProvider.setPriority(new Random().nextInt());
        return applicationIdentityProvider;
    }

    private static ApplicationSettings buildApplicationSettings() {
        ApplicationSettings settings = new ApplicationSettings();
        settings.setLogin(new LoginSettings());
        ApplicationOAuthSettings oauth = new ApplicationOAuthSettings();
        oauth.setGrantTypes(Collections.singletonList("authorization_code"));
        settings.setOauth(oauth);
        oauth.setBackchannelAuthRequestSignAlg("test");
        oauth.setBackchannelUserCodeParameter(true);
        oauth.setBackchannelTokenDeliveryMode("poll");
        oauth.setBackchannelClientNotificationEndpoint("ciba_endpoint");
        ApplicationScopeSettings scopeSettings = new ApplicationScopeSettings();
        scopeSettings.setScope("scopename");
        scopeSettings.setDefaultScope(true);
        scopeSettings.setScopeApproval(42);
        oauth.setScopeSettings(List.of(scopeSettings));

        final AccountSettings account = new AccountSettings();
        account.setResetPasswordInvalidateTokens(true);
        settings.setAccount(account);

        return settings;
    }

    private static ApplicationSecretSettings buildApplicationSecretSettings() {
        var settings = new ApplicationSecretSettings();
        settings.setId(UUID.randomUUID().toString());
        settings.setAlgorithm("BCrypt");
        settings.setProperties(Map.of("rounds", 10));
        return settings;
    }


    private static ClientSecret buildClientSecret() {
        var secret = new ClientSecret();
        secret.setId(UUID.randomUUID().toString());
        secret.setName("secret name");
        secret.setSecret("secret value");
        secret.setCreatedAt(new Date());
        secret.setSettingsId("settingId");
        return secret;
    }

    @Test
    public void testNotFoundById() {
        var observer = applicationRepository.findById("test").test();

        observer.awaitDone(5, TimeUnit.SECONDS);
        observer.assertComplete();
        observer.assertNoValues();
        observer.assertNoErrors();
    }

    @Test
    public void testCreate() {
        Application application = new Application();
        application.setName("testClientId");

        TestObserver<Application> testObserver = applicationRepository.create(application).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(a -> a.getName().equals(application.getName()));
    }

    @Test
    public void testUpdate() {
        // create app
        Application app = buildApplication();
        Application appCreated = applicationRepository.create(app).blockingGet();

        // update app
        Application updatedApp = buildApplication();
        updatedApp.setId(appCreated.getId());

        TestObserver<Application> testObserver = applicationRepository.update(updatedApp).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        assertEqualsTo(updatedApp, testObserver);
    }

    @Test
    public void testDelete() {
        // create app
        Application app = new Application();
        app.setName("testClientId");
        Application appCreated = applicationRepository.create(app).blockingGet();

        // fetch app
        TestObserver<Application> testObserver = applicationRepository.findById(appCreated.getId()).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(a -> a.getName().equals(app.getName()));

        // delete app
        TestObserver testObserver1 = applicationRepository.delete(appCreated.getId()).test();
        testObserver1.awaitDone(10, TimeUnit.SECONDS);

        // fetch app
        testObserver = applicationRepository.findById(appCreated.getId()).test();

        testObserver.awaitDone(5, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoValues();
        testObserver.assertNoErrors();
    }

    @Test
    public void testSearch_strict() {
        final String domain = "domain";
        // create app
        Application app = new Application();
        app.setDomain(domain);
        app.setName("clientId");
        applicationRepository.create(app).blockingGet();

        Application app2 = new Application();
        app2.setDomain(domain);
        app2.setName("clientId2");
        applicationRepository.create(app2).blockingGet();

        // fetch user
        TestObserver<Page<Application>> testObserver = applicationRepository.search(domain, "clientId", 0, Integer.MAX_VALUE).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(apps -> apps.getData().size() == 1);
        testObserver.assertValue(apps -> apps.getTotalCount() == 1);
        testObserver.assertValue(apps -> apps.getData().iterator().next().getName().equals(app.getName()));

    }

    @Test
    public void testSearch_wildcard() {
        final String domain = "domain";
        // create app
        Application app = new Application();
        app.setDomain(domain);
        app.setName("clientId");
        applicationRepository.create(app).blockingGet();

        Application app2 = new Application();
        app2.setDomain(domain);
        app2.setName("clientId2");
        applicationRepository.create(app2).blockingGet();

        Application app3 = new Application();
        app3.setDomain(domain);
        app3.setName("test");
        applicationRepository.create(app3).blockingGet();

        Application app4 = new Application();
        app4.setDomain(domain);
        app4.setName("test");
        ApplicationSettings settings = new ApplicationSettings();
        app4.setSettings(settings);
        ApplicationOAuthSettings oauth = new ApplicationOAuthSettings();
        settings.setOauth(oauth);
        oauth.setClientId("clientId4");
        applicationRepository.create(app4).blockingGet();

        // fetch apps
        TestObserver<Page<Application>> testObserver = applicationRepository.search(domain, "clientId*", 0, Integer.MAX_VALUE).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(apps -> apps.getData().size() == 3);
        testObserver.assertValue(apps -> apps.getTotalCount() == 3);
    }

    @Test
    public void testSearchApplicationIds_wildcard() {
        final String domain = "domainWithApss";
        // create app
        Application app = new Application();
        app.setDomain(domain);
        app.setName("clientId");
        app = applicationRepository.create(app).blockingGet();

        Application app2 = new Application();
        app2.setDomain(domain);
        app2.setName("clientId2");
        app2 = applicationRepository.create(app2).blockingGet();

        Application app3 = new Application();
        app3.setDomain(domain);
        app3.setName("test");
        app3 = applicationRepository.create(app3).blockingGet();

        Application app4 = new Application();
        app4.setDomain(domain);
        app4.setName("test");
        ApplicationSettings settings = new ApplicationSettings();
        app4.setSettings(settings);
        ApplicationOAuthSettings oauth = new ApplicationOAuthSettings();
        settings.setOauth(oauth);
        oauth.setClientId("clientId4");
        app4 = applicationRepository.create(app4).blockingGet();

        // fetch apps
        TestObserver<Page<Application>> testObserver = applicationRepository.search(domain, List.of(app.getId(), app2.getId(), app4.getId()), "clientId*", 0, 2).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(apps -> apps.getData().size() == 2);
        testObserver.assertValue(apps -> apps.getTotalCount() == 3);
    }

    @Test
    public void testUpdateMfaSettings() {
        // create app
        var app = buildApplication();
        var appCreated = applicationRepository.create(app).blockingGet();

        var enrollSettings = new EnrollSettings();
        enrollSettings.setActive(true);
        enrollSettings.setEnrollmentRule("rule-enrollment");
        enrollSettings.setType(MfaEnrollType.REQUIRED);

        var challengeSettings = new ChallengeSettings();
        challengeSettings.setActive(true);
        challengeSettings.setChallengeRule("rule-challenge");
        challengeSettings.setType(MfaChallengeType.REQUIRED);

        var stepUpAuthenticationSettings = new StepUpAuthenticationSettings();
        stepUpAuthenticationSettings.setActive(true);
        stepUpAuthenticationSettings.setStepUpAuthenticationRule("step-up-rule");

        var rememberDeviceSettings = new RememberDeviceSettings();
        rememberDeviceSettings.setActive(true);
        rememberDeviceSettings.setDeviceIdentifierId("device-id");
        rememberDeviceSettings.setSkipChallengeWhenRememberDevice(true);
        rememberDeviceSettings.setExpirationTimeSeconds(100000L);

        var mfaSettings = new MFASettings();
        mfaSettings.setEnroll(enrollSettings);
        mfaSettings.setChallenge(challengeSettings);
        mfaSettings.setStepUpAuthentication(stepUpAuthenticationSettings);
        mfaSettings.setLoginRule("login-rule");
        mfaSettings.setRememberDevice(rememberDeviceSettings);
        mfaSettings.setAdaptiveAuthenticationRule("adaptive-rule");

        var applicationSettings = app.getSettings();
        applicationSettings.setMfa(mfaSettings);

        // update app
        var updatedApp = buildApplication();
        updatedApp.setId(appCreated.getId());
        updatedApp.setSettings(applicationSettings);

        TestObserver<Application> testObserver = applicationRepository.update(updatedApp).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        assertEqualsTo(updatedApp, testObserver);
    }

    @Test
    public void testCreateWithPostLoginAction() {
        // create app with PostLoginAction
        Application app = buildApplication();
        PostLoginAction postLoginAction = buildPostLoginAction();
        app.getSettings().setPostLoginAction(postLoginAction);

        TestObserver<Application> testObserver = applicationRepository.create(app).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(createdApp -> {
            ApplicationSettings settings = createdApp.getSettings();
            return settings != null &&
                    settings.getPostLoginAction() != null &&
                    settings.getPostLoginAction().isEnabled() == postLoginAction.isEnabled() &&
                    settings.getPostLoginAction().getUrl().equals(postLoginAction.getUrl()) &&
                    settings.getPostLoginAction().getResponsePublicKey().equals(postLoginAction.getResponsePublicKey()) &&
                    settings.getPostLoginAction().getCertificateId().equals(postLoginAction.getCertificateId());
        });
    }

    @Test
    public void testUpdatePostLoginAction() {
        // create app without PostLoginAction
        Application app = buildApplication();
        Application appCreated = applicationRepository.create(app).blockingGet();

        // update app with PostLoginAction
        PostLoginAction postLoginAction = buildPostLoginAction();
        appCreated.getSettings().setPostLoginAction(postLoginAction);

        TestObserver<Application> testObserver = applicationRepository.update(appCreated).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(updatedApp -> {
            ApplicationSettings settings = updatedApp.getSettings();
            return settings != null &&
                    settings.getPostLoginAction() != null &&
                    settings.getPostLoginAction().isEnabled() == postLoginAction.isEnabled() &&
                    settings.getPostLoginAction().getUrl().equals(postLoginAction.getUrl()) &&
                    settings.getPostLoginAction().getResponsePublicKey().equals(postLoginAction.getResponsePublicKey()) &&
                    settings.getPostLoginAction().getCertificateId().equals(postLoginAction.getCertificateId()) &&
                    settings.getPostLoginAction().getTimeout() == postLoginAction.getTimeout() &&
                    settings.getPostLoginAction().getResponseTokenParam().equals(postLoginAction.getResponseTokenParam()) &&
                    settings.getPostLoginAction().getSuccessClaim().equals(postLoginAction.getSuccessClaim()) &&
                    settings.getPostLoginAction().getSuccessValue().equals(postLoginAction.getSuccessValue()) &&
                    settings.getPostLoginAction().getErrorClaim().equals(postLoginAction.getErrorClaim()) &&
                    settings.getPostLoginAction().getDataClaim().equals(postLoginAction.getDataClaim());
        });
    }

    @Test
    public void testUpdatePostLoginActionModifyValues() {
        // create app with PostLoginAction
        Application app = buildApplication();
        PostLoginAction postLoginAction = buildPostLoginAction();
        app.getSettings().setPostLoginAction(postLoginAction);
        Application appCreated = applicationRepository.create(app).blockingGet();

        // update PostLoginAction with new values
        PostLoginAction updatedPostLoginAction = new PostLoginAction();
        updatedPostLoginAction.setEnabled(false);
        updatedPostLoginAction.setUrl("https://updated.example.com/callback");
        updatedPostLoginAction.setCertificateId("updatedCert123");
        updatedPostLoginAction.setTimeout(60000);
        updatedPostLoginAction.setResponsePublicKey("-----BEGIN CERTIFICATE-----\nUPDATED_CERT_CONTENT\n-----END CERTIFICATE-----");
        updatedPostLoginAction.setResponseTokenParam("updatedToken");
        updatedPostLoginAction.setSuccessClaim("updatedStatus");
        updatedPostLoginAction.setSuccessValue("updatedOK");
        updatedPostLoginAction.setErrorClaim("updatedError");
        updatedPostLoginAction.setDataClaim("updatedData");

        appCreated.getSettings().setPostLoginAction(updatedPostLoginAction);

        TestObserver<Application> testObserver = applicationRepository.update(appCreated).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(updatedApp -> {
            PostLoginAction pla = updatedApp.getSettings().getPostLoginAction();
            return pla != null &&
                    !pla.isEnabled() &&
                    pla.getUrl().equals("https://updated.example.com/callback") &&
                    pla.getCertificateId().equals("updatedCert123") &&
                    pla.getTimeout() == 60000 &&
                    pla.getResponsePublicKey().equals("-----BEGIN CERTIFICATE-----\nUPDATED_CERT_CONTENT\n-----END CERTIFICATE-----") &&
                    pla.getResponseTokenParam().equals("updatedToken") &&
                    pla.getSuccessClaim().equals("updatedStatus") &&
                    pla.getSuccessValue().equals("updatedOK") &&
                    pla.getErrorClaim().equals("updatedError") &&
                    pla.getDataClaim().equals("updatedData");
        });
    }

    @Test
    public void testUpdateRemovePostLoginAction() {
        // create app with PostLoginAction
        Application app = buildApplication();
        PostLoginAction postLoginAction = buildPostLoginAction();
        app.getSettings().setPostLoginAction(postLoginAction);
        Application appCreated = applicationRepository.create(app).blockingGet();

        // verify PostLoginAction is present
        TestObserver<Application> verifyObserver = applicationRepository.findById(appCreated.getId()).test();
        verifyObserver.awaitDone(10, TimeUnit.SECONDS);
        verifyObserver.assertValue(a -> a.getSettings().getPostLoginAction() != null);

        // update app to remove PostLoginAction
        appCreated.getSettings().setPostLoginAction(null);

        TestObserver<Application> testObserver = applicationRepository.update(appCreated).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(updatedApp -> updatedApp.getSettings().getPostLoginAction() == null);
    }

    private static PostLoginAction buildPostLoginAction() {
        PostLoginAction postLoginAction = new PostLoginAction();
        postLoginAction.setEnabled(true);
        postLoginAction.setInherited(false);
        postLoginAction.setUrl("https://example.com/post-login");
        postLoginAction.setCertificateId("cert123");
        postLoginAction.setTimeout(30000);
        postLoginAction.setResponsePublicKey("-----BEGIN CERTIFICATE-----\nTEST_CERT_CONTENT\n-----END CERTIFICATE-----");
        postLoginAction.setResponseTokenParam("responseToken");
        postLoginAction.setSuccessClaim("status");
        postLoginAction.setSuccessValue("success");
        postLoginAction.setErrorClaim("error");
        postLoginAction.setDataClaim("data");
        return postLoginAction;
    }

}
