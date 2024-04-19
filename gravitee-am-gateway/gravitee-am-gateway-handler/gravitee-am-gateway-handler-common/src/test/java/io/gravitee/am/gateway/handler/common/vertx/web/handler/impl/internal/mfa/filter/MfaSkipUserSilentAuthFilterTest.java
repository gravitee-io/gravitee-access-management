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
package io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal.mfa.filter;

import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal.mfa.MfaFilterContext;
import io.vertx.rxjava3.ext.auth.User;
import io.vertx.rxjava3.ext.web.RoutingContext;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import org.mockito.Mockito;

public class MfaSkipUserSilentAuthFilterTest {

    @Test
    public void shouldSkipMfaWhenUserIsSilentAuthenticated(){
        // given
        RoutingContext routingContext = mockRoutingContext();
        Mockito.when(routingContext.get(ConstantKeys.SILENT_AUTH_CONTEXT_KEY)).thenReturn(true);

        MfaFilterContext ctx = new MfaFilterContext(routingContext, null, null);
        MfaSkipUserSilentAuthFilter filter = new MfaSkipUserSilentAuthFilter(ctx);

        // expect
        Assertions.assertTrue(filter.get());
    }

    @Test
    public void shouldNotSkipMfaWhenUserIsNotSilentAuth(){
        // given
        RoutingContext routingContext = mockRoutingContext();
        Mockito.when(routingContext.get(ConstantKeys.SILENT_AUTH_CONTEXT_KEY)).thenReturn(false);

        MfaFilterContext ctx = new MfaFilterContext(routingContext, null, null);
        MfaSkipUserSilentAuthFilter filter = new MfaSkipUserSilentAuthFilter(ctx);

        // expect
        Assertions.assertFalse(filter.get());
    }

    @Test
    public void shouldNotSkipMfaWhenRequestContextDoesntHaveSilentAuth(){
        // given
        RoutingContext routingContext = mockRoutingContext();

        MfaFilterContext ctx = new MfaFilterContext(routingContext, null, null);
        MfaSkipUserSilentAuthFilter filter = new MfaSkipUserSilentAuthFilter(ctx);

        // expect
        Assertions.assertFalse(filter.get());
    }

    private RoutingContext mockRoutingContext(){
        RoutingContext routingContext = Mockito.mock(RoutingContext.class);
        User user = Mockito.mock(User.class);
        Mockito.when(routingContext.user()).thenReturn(user);
        Mockito.when(user.getDelegate()).thenReturn(Mockito.mock(io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User.class));
        return routingContext;
    }

}