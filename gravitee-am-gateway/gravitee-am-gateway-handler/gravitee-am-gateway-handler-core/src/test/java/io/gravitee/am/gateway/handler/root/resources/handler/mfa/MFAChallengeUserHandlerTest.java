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
package io.gravitee.am.gateway.handler.root.resources.handler.mfa;

import io.gravitee.am.common.exception.oauth2.InvalidTokenException;
import io.gravitee.am.gateway.handler.root.resources.handler.dummies.SpyRoutingContext;
import io.gravitee.am.gateway.handler.root.service.user.UserService;
import io.gravitee.am.gateway.handler.root.service.user.model.UserToken;
import io.gravitee.am.model.oidc.Client;
import io.reactivex.rxjava3.core.Maybe;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.ext.auth.User;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import static io.gravitee.am.common.utils.ConstantKeys.TOKEN_PARAM_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.USER_CONTEXT_KEY;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class MFAChallengeUserHandlerTest {

    private SpyRoutingContext spyRoutingContext;

    private UserService userService;

    private MFAChallengeUserHandler handler;


    @Before
    public void setUp() {
        userService = spy(UserService.class);
        handler = new MFAChallengeUserHandler(userService);
        spyRoutingContext = spy(new SpyRoutingContext());
        doNothing().when(spyRoutingContext).next();
    }

    @Test
    public void mustDoNext_userAlreadySignedIn() {
        spyRoutingContext.setUser(User.create(new JsonObject()));
        handler.handle(spyRoutingContext);
        Assert.assertNull(spyRoutingContext.get(USER_CONTEXT_KEY));
        verify(spyRoutingContext, times(1)).next();
        verify(userService, times(0)).verifyToken(anyString());
    }

    @Test
    public void mustDoNext_noToken() {
        handler.handle(spyRoutingContext);
        Assert.assertNull(spyRoutingContext.get(USER_CONTEXT_KEY));
        verify(spyRoutingContext, times(1)).next();
        verify(userService, times(0)).verifyToken(anyString());
    }

    @Test
    public void mustDoNext_validToken() {
        io.gravitee.am.model.User endUser = new io.gravitee.am.model.User();
        Client client = new Client();
        UserToken userToken = new UserToken(endUser, client);
        spyRoutingContext.request().params().set(TOKEN_PARAM_KEY, "token");
        doReturn(Maybe.just(userToken)).when(userService).verifyToken("token");
        handler.handle(spyRoutingContext);
        Assert.assertNotNull(spyRoutingContext.get(USER_CONTEXT_KEY));
        verify(spyRoutingContext, times(1)).next();
        verify(userService, times(1)).verifyToken(anyString());
    }

    @Test
    public void mustDoNext_invalidToken() {
        spyRoutingContext.request().params().set(TOKEN_PARAM_KEY, "token");
        doReturn(Maybe.error(new InvalidTokenException())).when(userService).verifyToken("token");
        handler.handle(spyRoutingContext);
        Assert.assertNull(spyRoutingContext.get(USER_CONTEXT_KEY));
        verify(spyRoutingContext, times(1)).next();
        verify(userService, times(1)).verifyToken(anyString());
    }

    @Test
    public void mustDoNext_noUserToken() {
        spyRoutingContext.request().params().set(TOKEN_PARAM_KEY, "token");
        doReturn(Maybe.empty()).when(userService).verifyToken("token");
        handler.handle(spyRoutingContext);
        Assert.assertNull(spyRoutingContext.get(USER_CONTEXT_KEY));
        verify(spyRoutingContext, times(1)).next();
        verify(userService, times(1)).verifyToken(anyString());
    }
}
