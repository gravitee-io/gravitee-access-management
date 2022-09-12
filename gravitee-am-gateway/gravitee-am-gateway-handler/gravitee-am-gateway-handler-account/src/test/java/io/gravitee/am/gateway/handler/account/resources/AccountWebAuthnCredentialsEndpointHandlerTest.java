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
package io.gravitee.am.gateway.handler.account.resources;

import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.account.resources.util.AccountRoutes;
import io.gravitee.am.gateway.handler.account.services.AccountService;
import io.gravitee.am.gateway.handler.common.vertx.RxWebTestBase;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.ErrorHandler;
import io.gravitee.am.model.User;
import io.reactivex.Completable;
import io.vertx.core.http.HttpMethod;
import io.vertx.reactivex.ext.web.handler.BodyHandler;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class AccountWebAuthnCredentialsEndpointHandlerTest extends RxWebTestBase {

    @Mock
    private AccountService accountService;

    @Mock
    private User user;

    @InjectMocks
    private AccountWebAuthnCredentialsEndpointHandler accountWebAuthnCredentialsEndpointHandler = new AccountWebAuthnCredentialsEndpointHandler(accountService);

    @Override
    public void setUp() throws Exception {
        super.setUp();

        user = mock(User.class);
        when(user.getId()).thenReturn("user-id");

        router.route()
                .handler(ctx -> {
                    ctx.put(ConstantKeys.USER_CONTEXT_KEY, user);
                    ctx.next();
                })
                .handler(BodyHandler.create())
                .failureHandler(new ErrorHandler());
    }

    @Test
    public void shouldRemoveWebAuthnCredentials() throws Exception {
        router.delete(AccountRoutes.WEBAUTHN_CREDENTIALS_BY_ID.getRoute())
                .handler(accountWebAuthnCredentialsEndpointHandler::removeEnrolledWebAuthnCredential);

        when(accountService.removeWebAuthnCredential(eq("user-id"), eq("credential-id"), any())).thenReturn(Completable.complete());

        testRequest(HttpMethod.DELETE, "/api/webauthn/credentials/credential-id",
                null,
                204,
                "No Content", null);
    }
}
