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
package io.gravitee.am.gateway.handler.oauth2.service.token.impl;

import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.common.oauth2.TokenTypeHint;
import io.gravitee.am.gateway.handler.common.oauth2.IntrospectionTokenFacade;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.jupiter.api.Assertions.*;

@RunWith(MockitoJUnitRunner.class)
public class TokenServiceImplTest {

    @Mock
    IntrospectionTokenFacade introspectionTokenFacade;

    @InjectMocks
    TokenServiceImpl tokenService;

    @Test
    public void when_access_token_is_not_found_should_be_returned_refresh_token() {
        JWT jwt = new JWT();
        jwt.setJti("id");
        Mockito.when(introspectionTokenFacade.introspectAccessToken(Mockito.any())).thenReturn(Maybe.empty());
        Mockito.when(introspectionTokenFacade.introspectRefreshToken(Mockito.any())).thenReturn(Maybe.just(jwt));
        tokenService.introspect("token").test()
                .assertValue(token -> token.getValue().equals("id"));
    }

    @Test
    public void when_access_token_is_found_should_be_returned() {
        JWT jwt = new JWT();
        jwt.setJti("id");
        Mockito.when(introspectionTokenFacade.introspectAccessToken(Mockito.any())).thenReturn(Maybe.just(jwt));
        Mockito.when(introspectionTokenFacade.introspectRefreshToken(Mockito.any())).thenReturn(Maybe.empty());
        tokenService.introspect("token").test()
                .assertValue(token -> token.getValue().equals("id"));

    }

    @Test
    public void when_none_token_is_found_should_return_empty() {
        Mockito.when(introspectionTokenFacade.introspectAccessToken(Mockito.any())).thenReturn(Maybe.empty());
        Mockito.when(introspectionTokenFacade.introspectRefreshToken(Mockito.any())).thenReturn(Maybe.empty());
        tokenService.introspect("token").test()
                .assertComplete()
                .assertNoValues();

    }

    @Test
    public void when_hint_is_access_token_and_access_token_is_found_should_be_returned() {
        JWT jwt = new JWT();
        jwt.setJti("id");
        Mockito.when(introspectionTokenFacade.introspectAccessToken(Mockito.any())).thenReturn(Maybe.just(jwt));
        Mockito.when(introspectionTokenFacade.introspectRefreshToken(Mockito.any())).thenReturn(Maybe.empty());
        tokenService.introspect("token", TokenTypeHint.ACCESS_TOKEN).test()
                .assertValue(token -> token.getValue().equals("id"));
    }

    @Test
    public void when_hint_is_access_token_and_access_token_is_not_found_should_be_return_refresh_token() {
        JWT jwt = new JWT();
        jwt.setJti("id");
        Mockito.when(introspectionTokenFacade.introspectAccessToken(Mockito.any())).thenReturn(Maybe.empty());
        Mockito.when(introspectionTokenFacade.introspectRefreshToken(Mockito.any())).thenReturn(Maybe.just(jwt));
        tokenService.introspect("token", TokenTypeHint.ACCESS_TOKEN).test()
                .assertValue(token -> token instanceof RefreshToken && token.getValue().equals("id"));
    }

    @Test
    public void when_hint_is_access_token_and_access_and_refresh_tokens_are_not_found_should_return_empty() {
        JWT jwt = new JWT();
        jwt.setJti("id");
        Mockito.when(introspectionTokenFacade.introspectAccessToken(Mockito.any())).thenReturn(Maybe.empty());
        Mockito.when(introspectionTokenFacade.introspectRefreshToken(Mockito.any())).thenReturn(Maybe.empty());
        tokenService.introspect("token", TokenTypeHint.ACCESS_TOKEN).test()
                .assertComplete()
                .assertNoValues();
    }

    @Test
    public void when_hint_is_refresh_token_and_refresh_token_is_found_should_be_returned() {
        JWT jwt = new JWT();
        jwt.setJti("id");
        Mockito.when(introspectionTokenFacade.introspectAccessToken(Mockito.any())).thenReturn(Maybe.empty());
        Mockito.when(introspectionTokenFacade.introspectRefreshToken(Mockito.any())).thenReturn(Maybe.just(jwt));
        tokenService.introspect("token", TokenTypeHint.REFRESH_TOKEN).test()
                .assertValue(token -> token.getValue().equals("id"));
    }

    @Test
    public void when_hint_is_refresh_token_and_refresh_token_is_not_found_should_be_return_access_token() {
        JWT jwt = new JWT();
        jwt.setJti("id");
        Mockito.when(introspectionTokenFacade.introspectAccessToken(Mockito.any())).thenReturn(Maybe.just(jwt));
        Mockito.when(introspectionTokenFacade.introspectRefreshToken(Mockito.any())).thenReturn(Maybe.empty());
        tokenService.introspect("token", TokenTypeHint.REFRESH_TOKEN).test()
                .assertValue(token -> token instanceof AccessToken && token.getValue().equals("id"));
    }

    @Test
    public void when_hint_is_refresh_token_and_access_and_refresh_tokens_are_not_found_should_return_empty() {
        JWT jwt = new JWT();
        jwt.setJti("id");
        Mockito.when(introspectionTokenFacade.introspectAccessToken(Mockito.any())).thenReturn(Maybe.empty());
        Mockito.when(introspectionTokenFacade.introspectRefreshToken(Mockito.any())).thenReturn(Maybe.empty());
        tokenService.introspect("token", TokenTypeHint.REFRESH_TOKEN).test()
                .assertComplete()
                .assertNoValues();
    }

}