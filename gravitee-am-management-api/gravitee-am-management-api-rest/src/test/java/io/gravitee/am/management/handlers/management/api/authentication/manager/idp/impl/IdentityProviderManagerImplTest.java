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
package io.gravitee.am.management.handlers.management.api.authentication.manager.idp.impl;

import io.gravitee.am.common.event.IdentityProviderEvent;
import io.gravitee.am.identityprovider.api.AuthenticationProvider;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.plugins.idp.core.AuthenticationProviderConfiguration;
import io.gravitee.am.plugins.idp.core.IdentityProviderPluginManager;
import io.gravitee.am.service.IdentityProviderService;
import io.gravitee.am.service.PluginLicenseGate;
import io.gravitee.am.service.exception.LicenseFeatureRequiredException;
import io.gravitee.common.event.EventManager;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
class IdentityProviderManagerImplTest {

    @Mock
    private IdentityProviderPluginManager identityProviderPluginManager;

    @Mock
    private IdentityProviderService identityProviderService;

    @Mock
    private EventManager eventManager;

    @Mock
    private io.gravitee.am.management.service.IdentityProviderManager commonIdentityProviderManager;

    @Mock
    private PluginLicenseGate pluginLicenseGate;

    @InjectMocks
    private IdentityProviderManagerImpl cut;

    @BeforeEach
    void setUp() {
        lenient().when(pluginLicenseGate.checkPersisted(any(), any(), any())).thenReturn(Completable.complete());
    }

    @Test
    void shouldInitializeLicensedAuthenticationProvider() throws Exception {
        when(identityProviderService.findAll(ReferenceType.ORGANIZATION)).thenReturn(Flowable.just(orgIdp("idp-1", "http-am-idp")));
        when(identityProviderPluginManager.create(any(AuthenticationProviderConfiguration.class)))
                .thenReturn(mock(AuthenticationProvider.class));

        cut.afterPropertiesSet();

        assertNotNull(cut.get("idp-1"));
        verify(eventManager).subscribeForEvents(cut, IdentityProviderEvent.class);
    }

    @Test
    void shouldSkipUnlicensedAuthenticationProvider() throws Exception {
        when(identityProviderService.findAll(ReferenceType.ORGANIZATION))
                .thenReturn(Flowable.just(orgIdp("idp-1", "oss-am-idp"), orgIdp("idp-2", "ee-am-idp")));
        when(pluginLicenseGate.checkPersisted(any(), any(), eq("oss-am-idp"))).thenReturn(Completable.complete());
        when(pluginLicenseGate.checkPersisted(any(), any(), eq("ee-am-idp"))).thenReturn(Completable.error(new LicenseFeatureRequiredException("feature-x", "ee-am-idp")));
        when(identityProviderPluginManager.create(any(AuthenticationProviderConfiguration.class)))
                .thenReturn(mock(AuthenticationProvider.class));

        cut.afterPropertiesSet();

        assertNotNull(cut.get("idp-1"));
        assertNull(cut.get("idp-2"));
        verify(identityProviderPluginManager, never())
                .create(argThat((AuthenticationProviderConfiguration config) -> "ee-am-idp".equals(config.getType())));
    }

    private static IdentityProvider orgIdp(String id, String type) {
        final IdentityProvider identityProvider = new IdentityProvider();
        identityProvider.setId(id);
        identityProvider.setName(id);
        identityProvider.setType(type);
        identityProvider.setReferenceType(ReferenceType.ORGANIZATION);
        identityProvider.setReferenceId("org-1");
        identityProvider.setConfiguration("{}");
        identityProvider.setUpdatedAt(new Date());
        return identityProvider;
    }
}
