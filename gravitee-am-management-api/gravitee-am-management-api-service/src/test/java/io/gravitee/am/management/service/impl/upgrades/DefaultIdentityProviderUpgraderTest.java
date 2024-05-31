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
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Islem TRIKI (islem.triki at graviteesource.com)
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
public class DefaultIdentityProviderUpgraderTest {


    @Mock
    private IdentityProviderService identityProviderService;

    @Mock
    private IdentityProviderManager identityProviderManager;

    @InjectMocks
    private DefaultIdentityProviderUpgrader defaultIdentityProviderUpgrader;

    private IdentityProvider systemIdentityProvider = createDefaultTestIdp(true);
    private IdentityProvider nonSystemIdentityProvider = createDefaultTestIdp(false);

    @Test
    public void shouldUpdateDefaultIdp(){
        Map<String, Object> cfg = new HashMap<>(Map.of("new-config", "created"));
        when(identityProviderService.findAll()).thenReturn(Flowable.just(systemIdentityProvider));
        when(identityProviderManager.createProviderConfiguration(anyString(), isNull())).thenReturn(cfg);
        when(identityProviderService.update(anyString(), anyString(), any(UpdateIdentityProvider.class), eq(true))).thenReturn(changeIdpConfig(true));

        defaultIdentityProviderUpgrader.upgrade();

        verify(identityProviderService, times(1)).findAll();
        verify(identityProviderService, times(1))
                .update(anyString(),
                        anyString(),
                        argThat(upIdp -> upIdp.getConfiguration().contains("\"new-config\"") && upIdp.getConfiguration().contains("\"created\"") &&
                                // password encoder and linked options are immutable
                                upIdp.getConfiguration().contains("\"existing passwordEncoder\"") && upIdp.getConfiguration().contains("\"existing options\"")),
                        anyBoolean());
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
            systemIdentityProvider.setConfiguration("""
                    {
                      "attribute":"changed-system-config", 
                      "passwordEncoder":"existing passwordEncoder", 
                      "passwordEncoderOptions": {
                        "options":"existing options"
                        }
                    }
                    """);
        } else {
            nonSystemIdentityProvider.setConfiguration("""
                    {
                      "attribute":"changed-non-system-config", 
                      "passwordEncoder":"existing passwordEncoder", 
                      "passwordEncoderOptions": {
                        "options":"existing options"
                        }
                    }
                    """);
        }

        return Single.just(system ? systemIdentityProvider : nonSystemIdentityProvider);
    }

    private IdentityProvider createDefaultTestIdp(boolean system){
        IdentityProvider identityProvider = new IdentityProvider();
        identityProvider.setName("Default test IDP");
        identityProvider.setSystem(system);
        identityProvider.setId("test-idp-id");
        identityProvider.setConfiguration("{\"attribute\":\"existing attribute\", \"passwordEncoder\":\"existing passwordEncoder\", \"passwordEncoderOptions\":{\"options\":\"existing options\"}}");
        identityProvider.setReferenceId("domain-id");
        identityProvider.setExternal(false);
        identityProvider.setPasswordPolicy("password-policy-id");

        return identityProvider;
    }
}
