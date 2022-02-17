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

import io.gravitee.am.management.service.IdentityProviderManager;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.service.IdentityProviderService;
import io.gravitee.am.service.model.UpdateIdentityProvider;
import io.reactivex.Flowable;
import io.reactivex.Single;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

/**
 * @author Islem TRIKI (islem.triki at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class DefaultIdentityProviderUpgraderTest {

    @InjectMocks
    private DefaultIdentityProviderUpgrader defaultIdentityProviderUpgrader = new DefaultIdentityProviderUpgrader();

    @Mock
    private IdentityProviderService identityProviderService;

    @Mock
    private IdentityProviderManager identityProviderManager;

    private IdentityProvider systemIdentityProvider = createDefaultTestIdp(true);
    private IdentityProvider nonSystemIdentityProvider = createDefaultTestIdp(false);

    @Test
    public void shouldUpdateDefaultIdp(){

        when(identityProviderService.findAll()).thenReturn(Flowable.just(systemIdentityProvider));
        when(identityProviderManager.createProviderConfiguration(anyString(), isNull())).thenReturn("new-config-created");
        when(identityProviderService.update(anyString(), anyString(), any(UpdateIdentityProvider.class), eq(true))).thenReturn(changeIdpConfig(true));

        defaultIdentityProviderUpgrader.upgrade();

        verify(identityProviderService, times(1)).findAll();
        verify(identityProviderService, times(1))
                .update(anyString(), anyString(), argThat(upIdp -> upIdp.getConfiguration().equals("new-config-created")), anyBoolean());

    }

    @Test
    public void shouldNotUpdateDefaultIdp(){

        when(identityProviderService.findAll()).thenReturn(Flowable.just(nonSystemIdentityProvider));

        defaultIdentityProviderUpgrader.upgrade();

        verify(identityProviderService, times(1)).findAll();
        verify(identityProviderService, never())
                .update(anyString(), anyString(), any(UpdateIdentityProvider.class), anyBoolean());
    }

    private Single<IdentityProvider> changeIdpConfig(boolean system){
        if (system){
            systemIdentityProvider.setConfiguration("changed-system-config");
        } else {
            nonSystemIdentityProvider.setConfiguration("changed-non-system-config");
        }

        return Single.just(system ? systemIdentityProvider : nonSystemIdentityProvider);
    }

    private IdentityProvider createDefaultTestIdp(boolean system){
        IdentityProvider identityProvider = new IdentityProvider();
        identityProvider.setName("Default test IDP");
        identityProvider.setSystem(system);
        identityProvider.setId("test-idp-id");
        identityProvider.setConfiguration("configuration-test-idp");
        identityProvider.setReferenceId("domain-id");
        identityProvider.setExternal(false);

        return identityProvider;
    }
}
