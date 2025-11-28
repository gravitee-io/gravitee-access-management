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
package io.gravitee.am.gateway.handler.common.oauth2;

import io.gravitee.am.common.jwt.JWT;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@RunWith(MockitoJUnitRunner.class)
public class IntrospectionTokenFacadeTest {


    @Test
    public void should_introspect_access_token_by_dedicated_service_component(){
        // given
        IntrospectionTokenService accessService = mock(IntrospectionTokenService.class);
        Mockito.when(accessService.introspect("accessToken", false, null)).thenReturn(Maybe.just(new JWT(Map.of("jti", "accessId"))));
        IntrospectionTokenFacade facade = new IntrospectionTokenFacade(accessService, mock(IntrospectionTokenService.class));

        // when
        facade.introspectAccessToken("accessToken").test()
                .assertValue(jwt -> jwt.getJti().equals("accessId"));
    }

    @Test
    public void should_introspect_refresh_token_by_dedicated_service_component(){
        // given
        IntrospectionTokenService refreshService = mock(IntrospectionTokenService.class);
        Mockito.when(refreshService.introspect("refreshToken", false, null)).thenReturn(Maybe.just(new JWT(Map.of("jti", "refreshId"))));
        IntrospectionTokenFacade facade = new IntrospectionTokenFacade(mock(IntrospectionTokenService.class), refreshService);

        // when
        facade.introspectRefreshToken("refreshToken").test()
                .assertValue(jwt -> jwt.getJti().equals("refreshId"));
    }
}