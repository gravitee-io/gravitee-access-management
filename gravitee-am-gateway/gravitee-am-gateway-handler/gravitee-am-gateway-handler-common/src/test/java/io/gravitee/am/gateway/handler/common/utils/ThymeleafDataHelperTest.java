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
package io.gravitee.am.gateway.handler.common.utils;

import io.gravitee.am.model.User;
import io.gravitee.am.model.safe.UserProperties;
import io.vertx.reactivex.ext.web.RoutingContext;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static io.gravitee.am.common.utils.ConstantKeys.USER_CONTEXT_KEY;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@RunWith(MockitoJUnitRunner.class)
public class ThymeleafDataHelperTest {

    @Mock
    private RoutingContext routingContext;
    @Mock
    private User user;

    @Test
    public void shouldGetUserFromContext() {
        given(routingContext.get(eq(USER_CONTEXT_KEY))).willReturn(user);
        var data = ThymeleafDataHelper.generateData(routingContext, null, null);
        assertThat(data.get(USER_CONTEXT_KEY), instanceOf(UserProperties.class));
    }

    @Test
    public void shouldGetAuthenticatedUser() {
        given(routingContext.get(eq(USER_CONTEXT_KEY))).willReturn(null);
        io.vertx.reactivex.ext.auth.User authenticatedUser = mock(io.vertx.reactivex.ext.auth.User.class);
        given(routingContext.user()).willReturn(authenticatedUser);
        io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User delegateUser = mock(io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User.class);
        given(authenticatedUser.getDelegate()).willReturn(delegateUser);
        given(delegateUser.getUser()).willReturn(user);


        var data = ThymeleafDataHelper.generateData(routingContext, null, null);
        assertThat(data.get(USER_CONTEXT_KEY), instanceOf(UserProperties.class));
    }
}
