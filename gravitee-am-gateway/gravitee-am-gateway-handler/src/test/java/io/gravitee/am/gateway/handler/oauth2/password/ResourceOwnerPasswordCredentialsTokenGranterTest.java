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
package io.gravitee.am.gateway.handler.oauth2.password;

import io.gravitee.am.gateway.handler.auth.UserAuthenticationManager;
import io.gravitee.am.gateway.handler.oauth2.request.TokenRequest;
import io.gravitee.am.gateway.handler.oauth2.token.AccessToken;
import io.gravitee.am.identityprovider.api.Authentication;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.common.util.LinkedMultiValueMap;
import io.reactivex.Single;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class ResourceOwnerPasswordCredentialsTokenGranterTest {

    @InjectMocks
    private ResourceOwnerPasswordCredentialsTokenGranter granter;

    @Mock
    private UserAuthenticationManager userAuthenticationManager;

    @Mock
    private TokenRequest tokenRequest;

    @Before
    public void setUp() {
        granter.setUserAuthenticationManager(userAuthenticationManager);
    }

    @Test
    public void shouldGenerateAnAccessToken() {
        LinkedMultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.set(ResourceOwnerPasswordCredentialsTokenGranter.USERNAME_PARAMETER, "my-username");
        parameters.set(ResourceOwnerPasswordCredentialsTokenGranter.PASSWORD_PARAMETER, "my-password");

        when(tokenRequest.getClientId()).thenReturn("my-client-id");
        when(tokenRequest.getRequestParameters()).thenReturn(parameters);


        when(userAuthenticationManager.authenticate(eq(tokenRequest.getClientId()), any(Authentication.class))).thenReturn(
                Single.just(new DefaultUser("my-username")));

        Single<AccessToken> accessToken = granter.grant(tokenRequest);

    }
}
