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
package io.gravitee.am.gateway.handler.ciba.service.request;

import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import io.gravitee.am.authdevice.notifier.api.AuthenticationDeviceNotifierProvider;
import io.gravitee.am.authdevice.notifier.api.model.NotifierCapability;
import io.gravitee.am.common.exception.oauth2.InvalidRequestException;
import io.gravitee.am.gateway.handler.common.jwt.SubjectManager;
import io.gravitee.am.gateway.handler.common.user.UserGatewayService;
import io.gravitee.am.gateway.handler.common.protectedresource.ProtectedResourceManager;
import io.gravitee.am.gateway.handler.manager.authdevice.notifier.AuthenticationDeviceNotifierManager;
import io.gravitee.am.gateway.handler.oauth2.service.scope.ScopeManager;
import io.gravitee.am.gateway.handler.oidc.service.jwk.JWKService;
import io.gravitee.am.gateway.handler.oidc.service.jws.JWSService;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.User;
import io.gravitee.am.model.application.ApplicationScopeSettings;
import io.gravitee.am.model.oidc.CIBASettings;
import io.gravitee.am.model.oidc.CIBASettingNotifier;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.model.oidc.OIDCSettings;
import io.gravitee.am.repository.management.api.search.FilterCriteria;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class CibaAuthenticationRequestResolverTest {

    @Mock private JWSService jwsService;
    @Mock private JWKService jwkService;
    @Mock private UserGatewayService userService;
    @Mock private SubjectManager subjectManager;
    @Mock private ScopeManager scopeManager;
    @Mock private ProtectedResourceManager protectedResourceManager;
    @Mock private AuthenticationDeviceNotifierManager deviceNotifierManager;
    @Mock private AuthenticationDeviceNotifierProvider notifierProvider;

    private Domain domain;
    private CIBASettings cibaSettings;
    private CibaAuthenticationRequestResolver resolver;

    @Before
    public void init() {
        this.cibaSettings = new CIBASettings();
        final OIDCSettings oidc = new OIDCSettings();
        oidc.setCibaSettings(this.cibaSettings);
        this.domain = new Domain();
        this.domain.setOidc(oidc);

        this.resolver = new CibaAuthenticationRequestResolver(domain, jwsService, jwkService, userService, subjectManager, deviceNotifierManager);
        this.resolver.setManagers(scopeManager, protectedResourceManager);
    }

    /** Wire the domain with a device-notifier id and stub the manager to return the mock provider. */
    private void stubNotifierWithCapabilities(Set<NotifierCapability> caps) {
        CIBASettingNotifier notifier = new CIBASettingNotifier();
        notifier.setId("notifier-1");
        cibaSettings.setDeviceNotifiers(List.of(notifier));
        when(deviceNotifierManager.getAuthDeviceNotifierProvider(eq("notifier-1"))).thenReturn(notifierProvider);
        when(notifierProvider.capabilities()).thenReturn(caps);
    }

    private CibaAuthenticationRequest requestWithLoginHint(String hint) {
        CibaAuthenticationRequest req = new CibaAuthenticationRequest();
        req.setLoginHint(hint);
        req.setScopes(Set.of("openid"));
        return req;
    }

    // The inherited resolveAuthorizedScopes(...) rejects a requested scope the client
    // does not grant, so the client must declare "openid" (mirrors TokenRequestResolverTest).
    private Client clientWithOpenid() {
        Client client = new Client();
        ApplicationScopeSettings openid = new ApplicationScopeSettings();
        openid.setScope("openid");
        client.setScopeSettings(List.of(openid));
        return client;
    }

    @Test
    public void resolvesExistingUser_whenHintMatchesOne() {
        User existing = new User();
        existing.setId("user-123");
        when(userService.findByCriteria(any(FilterCriteria.class))).thenReturn(Single.just(List.of(existing)));

        TestObserver<CibaAuthenticationRequest> obs = resolver.resolve(requestWithLoginHint("alice@acme.fr"), clientWithOpenid()).test();
        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertNoErrors();
        obs.assertValue(r -> "user-123".equals(r.getSubject()) && r.getUser() == existing);
    }

    @Test
    public void rejectsUnknownHint_whenFederationDisabled() {
        // no notifier configured → capability absent → federation disabled
        when(userService.findByCriteria(any(FilterCriteria.class))).thenReturn(Single.just(List.of()));

        TestObserver<CibaAuthenticationRequest> obs = resolver.resolve(requestWithLoginHint("ghost@acme.fr"), clientWithOpenid()).test();
        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertError(InvalidRequestException.class);
    }

    @Test
    public void carriesHintAndSetsNoSubject_whenFederationEnabled() {
        stubNotifierWithCapabilities(Set.of(NotifierCapability.FEDERATED_HINT_RESOLUTION));
        when(userService.findByCriteria(any(FilterCriteria.class))).thenReturn(Single.just(List.of()));

        TestObserver<CibaAuthenticationRequest> obs = resolver.resolve(requestWithLoginHint("auth0|abc"), clientWithOpenid()).test();
        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertNoErrors();
        obs.assertValue(r ->
                r.getSubject() == null
                && r.getUser() == null
                && "auth0|abc".equals(r.getLoginHint()));
    }

    @Test
    public void rejectsMultipleMatchingUsers_evenWhenFederationEnabled() {
        stubNotifierWithCapabilities(Set.of(NotifierCapability.FEDERATED_HINT_RESOLUTION));
        User u1 = new User();
        u1.setId("a");
        User u2 = new User();
        u2.setId("b");
        when(userService.findByCriteria(any(FilterCriteria.class))).thenReturn(Single.just(List.of(u1, u2)));

        TestObserver<CibaAuthenticationRequest> obs = resolver.resolve(requestWithLoginHint("shared@acme.fr"), clientWithOpenid()).test();
        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertError(InvalidRequestException.class);
    }

    @Test
    public void issSubJsonHint_withFederation_carriesVerbatim_withoutLocalLookup() {
        stubNotifierWithCapabilities(Set.of(NotifierCapability.FEDERATED_HINT_RESOLUTION));
        // NOTE: findByCriteria intentionally NOT stubbed — the guard must skip the local lookup.
        final String hint = "{\"format\":\"iss_sub\",\"iss\":\"https://op/\",\"sub\":\"auth0|9\"}";

        TestObserver<CibaAuthenticationRequest> obs = resolver.resolve(requestWithLoginHint(hint), clientWithOpenid()).test();
        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertNoErrors();
        obs.assertValue(r ->
                r.getSubject() == null      // no subject minted at request time
                && r.getUser() == null
                && hint.equals(r.getLoginHint())); // hint carried verbatim
        verify(userService, never()).findByCriteria(any()); // guard must skip the injection-prone local lookup
    }

    @Test
    public void issSubJsonHint_withoutFederation_rejectsCleanly_withoutLocalLookup() {
        // no notifier configured → capability absent → federation disabled
        final String hint = "{\"format\":\"iss_sub\",\"iss\":\"https://op/\",\"sub\":\"auth0|9\"}";

        TestObserver<CibaAuthenticationRequest> obs = resolver.resolve(requestWithLoginHint(hint), clientWithOpenid()).test();
        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertError(InvalidRequestException.class);
        verify(userService, never()).findByCriteria(any());
    }

    private CibaAuthenticationRequest requestWithLoginHintToken(String email) throws Exception {
        CibaAuthenticationRequest req = new CibaAuthenticationRequest();
        req.setScopes(Set.of("openid"));
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .claim("sub_id", Map.of("format", "email", "email", email))
                .build();
        req.setLoginHintToken(new PlainJWT(claims).serialize());
        return req;
    }

    @Test
    public void carriesHintToken_andSetsNoSubject_whenFederationEnabled() throws Exception {
        // The lookup is mocked, so this path runs despite the known stock bug "BUG-1".
        stubNotifierWithCapabilities(Set.of(NotifierCapability.FEDERATED_HINT_RESOLUTION));
        when(userService.findByCriteria(any(FilterCriteria.class))).thenReturn(Single.just(List.of()));

        CibaAuthenticationRequest req = requestWithLoginHintToken("ghost@acme.fr");
        final String originalToken = req.getLoginHintToken();
        TestObserver<CibaAuthenticationRequest> obs = resolver.resolve(req, clientWithOpenid()).test();
        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertNoErrors();
        obs.assertValue(r ->
                r.getSubject() == null
                && r.getUser() == null
                && originalToken.equals(r.getLoginHintToken()));
    }

    @Test
    public void rejectsLoginHintToken_whenFederationDisabled() throws Exception {
        // no notifier configured → capability absent → federation disabled
        when(userService.findByCriteria(any(FilterCriteria.class))).thenReturn(Single.just(List.of()));

        TestObserver<CibaAuthenticationRequest> obs = resolver.resolve(requestWithLoginHintToken("ghost@acme.fr"), clientWithOpenid()).test();
        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertError(InvalidRequestException.class);
    }

    // ── Task 7: new capability-routing tests ──────────────────────────────────

    @Test
    public void federatedHintResolution_enabledWhenCapabilityPresent() {
        stubNotifierWithCapabilities(Set.of(NotifierCapability.FEDERATED_HINT_RESOLUTION));
        when(userService.findByCriteria(any(FilterCriteria.class))).thenReturn(Single.just(List.of()));

        TestObserver<CibaAuthenticationRequest> obs = resolver.resolve(requestWithLoginHint("federated@idp.example"), clientWithOpenid()).test();
        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertNoErrors();
        obs.assertValue(r -> r.getSubject() == null && r.getUser() == null
                && "federated@idp.example".equals(r.getLoginHint()));
    }

    @Test
    public void federatedHintResolution_disabledWhenNoNotifierConfigured() {
        // cibaSettings has no deviceNotifiers → capability absent
        when(userService.findByCriteria(any(FilterCriteria.class))).thenReturn(Single.just(List.of()));

        TestObserver<CibaAuthenticationRequest> obs = resolver.resolve(requestWithLoginHint("federated@idp.example"), clientWithOpenid()).test();
        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertError(InvalidRequestException.class);
    }

    @Test
    public void federatedHintResolution_disabledWhenCapabilityAbsent() {
        // notifier exists but advertises no capabilities (empty set)
        stubNotifierWithCapabilities(Set.of());
        when(userService.findByCriteria(any(FilterCriteria.class))).thenReturn(Single.just(List.of()));

        TestObserver<CibaAuthenticationRequest> obs = resolver.resolve(requestWithLoginHint("federated@idp.example"), clientWithOpenid()).test();
        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertError(InvalidRequestException.class);
    }
}
