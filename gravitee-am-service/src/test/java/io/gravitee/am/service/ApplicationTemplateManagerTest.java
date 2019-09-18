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

import io.gravitee.am.model.Application;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.application.ApplicationOAuthSettings;
import io.gravitee.am.model.application.ApplicationSettings;
import io.reactivex.Maybe;
import io.reactivex.Single;
import io.reactivex.observers.TestObserver;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ApplicationTemplateManagerTest {

   /* @Test
    public void create_generateUuidAsClientId() {
        when(domainService.findById(DOMAIN)).thenReturn(Maybe.just(new Domain()));
        when(domainService.reload(eq(DOMAIN), any())).thenReturn(Single.just(new Domain()));
        when(scopeService.validateScope(DOMAIN,null)).thenReturn(Single.just(true));
        when(applicationRepository.findByDomainAndClientId(anyString(), anyString())).thenReturn(Maybe.empty());
        when(applicationRepository.create(any(Application.class))).thenReturn(Single.just(new Application()));

        Application toCreate = new Application();
        toCreate.setDomain(DOMAIN);
        ApplicationSettings settings = new ApplicationSettings();
        ApplicationOAuthSettings oAuthSettings = new ApplicationOAuthSettings();
        oAuthSettings.setRedirectUris(Arrays.asList("https://callback"));
        settings.setOauth(oAuthSettings);
        toCreate.setSettings(settings);
        TestObserver testObserver = applicationService.create(toCreate).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        ArgumentCaptor<Application> captor = ArgumentCaptor.forClass(Application.class);
        verify(applicationRepository, times(1)).create(captor.capture());
        Assert.assertTrue("client_id must be generated",captor.getValue().getSettings().getOauth().getClientId()!=null);
        Assert.assertTrue("client_secret must be generated",captor.getValue().getSettings().getOauth().getClientSecret()!=null);
    }*/
}
