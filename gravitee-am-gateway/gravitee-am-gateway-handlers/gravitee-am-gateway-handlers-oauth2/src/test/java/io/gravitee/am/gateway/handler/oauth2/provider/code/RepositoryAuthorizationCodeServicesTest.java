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
package io.gravitee.am.gateway.handler.oauth2.provider.code;

import io.gravitee.am.gateway.handler.oauth2.provider.RepositoryProviderUtils;
import io.gravitee.am.repository.oauth2.api.AuthorizationCodeRepository;
import io.gravitee.am.repository.oauth2.model.OAuth2Authentication;
import io.gravitee.am.repository.oauth2.model.request.OAuth2Request;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.security.oauth2.common.exceptions.InvalidGrantException;

import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.*;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class RepositoryAuthorizationCodeServicesTest {

    @InjectMocks
    private RepositoryAuthorizationCodeServices authorizationCodeServices = new RepositoryAuthorizationCodeServices();

    @Mock
    private AuthorizationCodeRepository authorizationCodeRepository;

    @Mock
    private OAuth2Authentication oAuth2Authentication;

    @Mock
    private OAuth2Request oAuth2Request;

    @Test
    public void shouldStore() {
        // prepare OAuth2AuthorizationCode
        final String codeId = "test-code";

        // prepare OAuth2Authentication
        final String clientId = "test-client";
        when(oAuth2Request.getClientId()).thenReturn(clientId);
        when(oAuth2Authentication.getOAuth2Request()).thenReturn(oAuth2Request);

        // Run
        authorizationCodeServices.store(codeId, RepositoryProviderUtils.convert(oAuth2Authentication));

        // Verify
        verify(authorizationCodeRepository, times(1)).store(any());
    }

    @Test
    public void shouldRemove() {
        // prepare OAuth2AuthorizationCode
        final String codeId = "test-code";

        // prepare OAuth2Authentication
        final String clientId = "test-client";
        when(oAuth2Request.getClientId()).thenReturn(clientId);
        when(oAuth2Authentication.getOAuth2Request()).thenReturn(oAuth2Request);
        when(authorizationCodeRepository.remove(codeId)).thenReturn(Optional.ofNullable(oAuth2Authentication));

        // Run
        final org.springframework.security.oauth2.provider.OAuth2Authentication oAuth2Authentication =
                authorizationCodeServices.remove(codeId);

        // Verify
        verify(authorizationCodeRepository, times(1)).remove(codeId);
        assertEquals(clientId, oAuth2Authentication.getOAuth2Request().getClientId());
    }

    @Test(expected = InvalidGrantException.class)
    public void shouldNotConsumeNonExistingCode() {
        // prepare OAuth2Authentication
        final String clientId = "test-client";
        when(oAuth2Request.getClientId()).thenReturn(clientId);
        when(oAuth2Authentication.getOAuth2Request()).thenReturn(oAuth2Request);

        // Run
        String code = authorizationCodeServices.createAuthorizationCode(RepositoryProviderUtils.convert(oAuth2Authentication));
        assertNotNull(code);
        when(authorizationCodeRepository.remove(code)).thenReturn(Optional.ofNullable(null));
        authorizationCodeServices.consumeAuthorizationCode(code);
    }
}
