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
package io.gravitee.am.gateway.handler.oauth2.service.consent;

import io.gravitee.am.gateway.handler.oauth2.service.consent.impl.UserConsentServiceImpl;
import io.gravitee.am.gateway.handler.oauth2.service.scope.ScopeManager;
import io.gravitee.am.gateway.handler.oauth2.service.scope.ScopeService;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.User;
import io.gravitee.am.model.application.ApplicationScopeSettings;
import io.gravitee.am.model.oauth2.Scope;
import io.gravitee.am.model.oauth2.ScopeApproval;
import io.gravitee.am.model.oauth2.ScopeApproval.ApprovalStatus;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.ScopeApprovalService;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import javassist.NotFoundException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static io.gravitee.am.model.oauth2.ScopeApproval.ApprovalStatus.APPROVED;
import static io.gravitee.am.model.oauth2.ScopeApproval.ApprovalStatus.DENIED;
import static java.lang.System.currentTimeMillis;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class UserConsentServiceTest {

    @Mock
    private ScopeApprovalService scopeApprovalService;

    @Mock
    private ScopeService scopeService;

    @Mock
    private ScopeManager scopeManager;

    @Mock
    private Domain domain;

    @InjectMocks
    private UserConsentService userConsentService = new UserConsentServiceImpl(-1);

    @Test
    public void must_fail_check_consent() {
        when(scopeApprovalService.findByDomainAndUserAndClient(any(), any(), any()))
                .thenReturn(Flowable.error(new NotFoundException("Cannot find approvals")));

        final Client client = new Client();
        client.setClientId(UUID.randomUUID().toString());

        final User user = new User();
        user.setId(UUID.randomUUID().toString());

        var testObserver = userConsentService.checkConsent(client, user).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertError(NotFoundException.class);
    }

    @Test
    public void must_succeed_check_consent() {
        final Client client = new Client();
        client.setClientId(UUID.randomUUID().toString());

        final User user = new User();
        user.setId(UUID.randomUUID().toString());

        final Flowable<ScopeApproval> scopeApprovals = Flowable.just(
                getScopeApproval(user, client, "openid", APPROVED, new Date(currentTimeMillis() + 10000L)),
                getScopeApproval(user, client, "email", APPROVED, new Date(currentTimeMillis() + 10000L)),
                getScopeApproval(user, client, "browser", APPROVED, new Date(currentTimeMillis() - 10000L)),
                getScopeApproval(user, client, "phone", DENIED, new Date(currentTimeMillis() + 10000L))
        );
        when(scopeApprovalService.findByDomainAndUserAndClient(any(), any(), any())).thenReturn(scopeApprovals);


        var testObserver = userConsentService.checkConsent(client, user).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver
                .assertComplete()
                .assertValue(set -> set.size() == 2);
    }

    @Test
    public void must_save_consent() {
        final Client client = new Client();
        client.setClientId(UUID.randomUUID().toString());

        client.setScopeSettings(
                List.of(
                        getScopeSettings("openid", true, 10000),
                        getScopeSettings("email", false, -1),
                        getScopeSettings("browser", false, null),
                        getScopeSettings("phone", false, null)
                )
        );

        final User user = new User();
        user.setId(UUID.randomUUID().toString());

        var scopeApprovals = List.of(
                getScopeApproval(user, client, "openid", APPROVED, new Date(currentTimeMillis() + 10000L)),
                getScopeApproval(user, client, "email", APPROVED, new Date(currentTimeMillis() + 10000L)),
                getScopeApproval(user, client, "browser", APPROVED, new Date(currentTimeMillis() - 10000L)),
                getScopeApproval(user, client, "phone", DENIED, new Date(currentTimeMillis() + 10000L))
        );

        when(scopeManager.isParameterizedScope("openid")).thenReturn(false);
        when(scopeManager.isParameterizedScope("email")).thenReturn(true);
        when(scopeManager.isParameterizedScope("browser")).thenReturn(true);
        when(scopeManager.isParameterizedScope("phone")).thenReturn(true);

        when(scopeApprovalService.saveConsent(any(), any(), any(), any())).thenReturn(Single.just(scopeApprovals));

        var testObserver = userConsentService.saveConsent(client, scopeApprovals, new DefaultUser(user)).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver
                .assertComplete()
                .assertValue(set -> set.size() == 4);
    }

    private ApplicationScopeSettings getScopeSettings(String scope, boolean isDefault, Integer scopeApproval) {
        var settings = new ApplicationScopeSettings(scope);
        settings.setDefaultScope(isDefault);
        settings.setScopeApproval(scopeApproval);
        return settings;
    }

    @Test
    public void must_get_consent_info() {
        when(scopeService.getAll()).thenReturn(Single.just(Set.of(
                new Scope("openid"),
                new Scope("email"),
                new Scope("browser"),
                new Scope("phone")
        )));

        final Set<String> consent = Set.of("openid", "email");
        var observer = userConsentService.getConsentInformation(consent).test();

        observer.assertComplete()
                .assertValue(set -> set.size() == 2 && set.stream().map(Scope::getKey).allMatch(consent::contains));
    }

    private ScopeApproval getScopeApproval(User user, Client client, String phone, ApprovalStatus denied, Date expires) {
        var approval = new ScopeApproval(UUID.randomUUID().toString(), user.getFullId(), client.getClientId(), domain.getName(), phone, denied);
        approval.setExpiresAt(expires);
        return approval;
    }
}
