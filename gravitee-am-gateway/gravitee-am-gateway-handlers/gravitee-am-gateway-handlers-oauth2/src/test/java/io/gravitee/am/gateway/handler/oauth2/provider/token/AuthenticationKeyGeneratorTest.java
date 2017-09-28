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
package io.gravitee.am.gateway.handler.oauth2.provider.token;

import io.gravitee.am.gateway.handler.oauth2.provider.client.DelegateClientDetails;
import io.gravitee.am.gateway.handler.oauth2.provider.client.DomainBasedClientDetailsService;
import io.gravitee.am.model.Client;
import io.gravitee.am.model.Domain;
import io.gravitee.am.repository.oauth2.model.OAuth2Authentication;
import io.gravitee.am.repository.oauth2.model.request.OAuth2Request;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import static org.mockito.Mockito.when;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class AuthenticationKeyGeneratorTest {

    @InjectMocks
    private AuthenticationKeyGenerator authenticationKeyGenerator = new DefaultAuthenticationKeyGenerator();

    @Mock
    private OAuth2Authentication oAuth2Authentication;

    @Mock
    private OAuth2Request oAuth2Request;

    @Mock
    private DomainBasedClientDetailsService clientDetailsService;

    @Mock
    private DelegateClientDetails clientDetails;

    @Mock
    private Client client;

    @Mock
    private Domain domain;

    @Test
    public void shouldExtractTheSameKeyForTheSameAuthentication() {
        when(domain.getId()).thenReturn("domain-test");
        when(oAuth2Request.getClientId()).thenReturn("client-test");
        when(oAuth2Authentication.getOAuth2Request()).thenReturn(oAuth2Request);
        when(oAuth2Authentication.isClientOnly()).thenReturn(false);
        when(oAuth2Authentication.getName()).thenReturn("user-test");
        when(client.isGenerateNewTokenPerRequest()).thenReturn(false);
        when(clientDetails.getClient()).thenReturn(client);
        when(clientDetailsService.loadClientByClientId(oAuth2Request.getClientId())).thenReturn(clientDetails);

        String key1 = authenticationKeyGenerator.extractKey(oAuth2Authentication);
        String key2 = authenticationKeyGenerator.extractKey(oAuth2Authentication);

        Assert.assertEquals(key1, key2);
    }

    @Test
    public void shouldNotExtractTheSameKeyForTheSameAuthentication() {
        when(domain.getId()).thenReturn("domain-test");
        when(oAuth2Request.getClientId()).thenReturn("client-test");
        when(oAuth2Authentication.getOAuth2Request()).thenReturn(oAuth2Request);
        when(oAuth2Authentication.isClientOnly()).thenReturn(false);
        when(oAuth2Authentication.getName()).thenReturn("user-test");
        when(client.isGenerateNewTokenPerRequest()).thenReturn(true);
        when(clientDetails.getClient()).thenReturn(client);
        when(clientDetailsService.loadClientByClientId(oAuth2Request.getClientId())).thenReturn(clientDetails);

        String key1 = authenticationKeyGenerator.extractKey(oAuth2Authentication);
        String key2 = authenticationKeyGenerator.extractKey(oAuth2Authentication);

        Assert.assertNotEquals(key1, key2);
    }

}
