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
package io.gravitee.am.management.service.impl.upgrades;

import io.gravitee.am.management.service.impl.upgrades.OpenIDScopeUpgrader;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.oauth2.Scope;
import io.gravitee.am.service.DomainService;
import io.gravitee.am.service.ScopeService;
import io.gravitee.am.service.model.NewSystemScope;
import io.gravitee.am.service.model.UpdateSystemScope;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.HashSet;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class OpenIDScopeUpgraderTest {

    @InjectMocks
    private OpenIDScopeUpgrader openIDScopeUpgrader = new OpenIDScopeUpgrader();

    @Mock
    private DomainService domainService;

    @Mock
    private ScopeService scopeService;

    @Mock
    private Domain domain;

    private static final String DOMAIN_ID = "domainId";

    @Before
    public void setUp() {
        when(domain.getId()).thenReturn(DOMAIN_ID);
        when(domainService.findAll()).thenReturn(Single.just(new HashSet<>(Arrays.asList(domain))));
    }

    @Test
    public void shouldCreateSystemScope() {
        when(scopeService.findByDomainAndKey(eq(DOMAIN_ID), anyString())).thenReturn(Maybe.empty());
        when(scopeService.create(anyString(),any(NewSystemScope.class))).thenReturn(Single.just(new Scope()));

        assertTrue(openIDScopeUpgrader.upgrade());
        verify(scopeService, times(io.gravitee.am.common.oidc.Scope.values().length)).create(anyString(), any(NewSystemScope.class));
    }

    @Test
    public void shouldUpdateSystemScope() {
        Scope openId = new Scope();
        openId.setId("1");
        openId.setSystem(false);//expect to be updated because not set as system
        openId.setKey("openid");

        Scope phone = new Scope();
        phone.setId("2");
        phone.setSystem(true);
        phone.setKey("phone");
        phone.setDiscovery(false);//expect to be updated because not same discovery value

        Scope email = new Scope();
        email.setId("3");
        email.setSystem(true);//expect not to be updated
        email.setKey("email");
        email.setDiscovery(true);

        when(scopeService.findByDomainAndKey(eq(DOMAIN_ID), anyString())).thenReturn(Maybe.empty());
        when(scopeService.findByDomainAndKey(DOMAIN_ID, "openid")).thenReturn(Maybe.just(openId));
        when(scopeService.findByDomainAndKey(DOMAIN_ID, "phone")).thenReturn(Maybe.just(phone));
        when(scopeService.findByDomainAndKey(DOMAIN_ID, "email")).thenReturn(Maybe.just(email));
        when(scopeService.create(anyString(),any(NewSystemScope.class))).thenReturn(Single.just(new Scope()));
        when(scopeService.update(anyString(), anyString(), any(UpdateSystemScope.class))).thenReturn(Single.just(new Scope()));

        assertTrue(openIDScopeUpgrader.upgrade());
        verify(scopeService, times(io.gravitee.am.common.oidc.Scope.values().length-3)).create(anyString(), any(NewSystemScope.class));
        verify(scopeService, times(2)).update(anyString(), anyString(), any(UpdateSystemScope.class));
    }
}
