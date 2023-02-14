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
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import io.vertx.rxjava3.core.http.HttpServerRequest;
import io.vertx.rxjava3.ext.web.RoutingContext;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.HashMap;
import java.util.Map;

import static io.gravitee.am.common.utils.ConstantKeys.PARAM_CONTEXT_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.USER_CONTEXT_KEY;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
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
        defineDefaultRequestMock();
        given(routingContext.get(eq(USER_CONTEXT_KEY))).willReturn(user);
        var data = ThymeleafDataHelper.generateData(routingContext, null, null);
        assertThat(data.get(USER_CONTEXT_KEY), instanceOf(UserProperties.class));
    }

    @Test
    public void shouldGetAuthenticatedUser() {
        defineDefaultRequestMock();
        given(routingContext.get(eq(USER_CONTEXT_KEY))).willReturn(null);
        io.vertx.rxjava3.ext.auth.User authenticatedUser = mock(io.vertx.rxjava3.ext.auth.User.class);
        given(routingContext.user()).willReturn(authenticatedUser);
        io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User delegateUser = mock(io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User.class);
        given(authenticatedUser.getDelegate()).willReturn(delegateUser);
        given(delegateUser.getUser()).willReturn(user);


        var data = ThymeleafDataHelper.generateData(routingContext, null, null);
        assertThat(data.get(USER_CONTEXT_KEY), instanceOf(UserProperties.class));
    }

    private void defineDefaultRequestMock() {
        final HttpServerRequest serverRequest = mock(HttpServerRequest.class);
        final io.vertx.core.http.HttpServerRequest delegatedServerRequest = mock(io.vertx.core.http.HttpServerRequest.class);
        given(delegatedServerRequest.method()).willReturn(HttpMethod.POST);
        given(delegatedServerRequest.params()).willReturn(MultiMap.caseInsensitiveMultiMap());
        given(serverRequest.getDelegate()).willReturn(delegatedServerRequest);
        given(routingContext.request()).willReturn(serverRequest);
    }

    @Test
    public void shouldProvideRequestParameters() {
        final HttpServerRequest serverRequest = mock(HttpServerRequest.class);
        final io.vertx.core.http.HttpServerRequest delegatedServerRequest = mock(io.vertx.core.http.HttpServerRequest.class);
        given(delegatedServerRequest.method()).willReturn(HttpMethod.POST);
        given(delegatedServerRequest.params()).willReturn(MultiMap.caseInsensitiveMultiMap()
                .add("param1", "value")
                .add("param2", "value2-1")
                .add("param2", "value2-2"));
        given(serverRequest.getDelegate()).willReturn(delegatedServerRequest);
        given(routingContext.request()).willReturn(serverRequest);

        given(routingContext.get(eq(USER_CONTEXT_KEY))).willReturn(null);
        io.vertx.rxjava3.ext.auth.User authenticatedUser = mock(io.vertx.rxjava3.ext.auth.User.class);
        given(routingContext.user()).willReturn(authenticatedUser);
        io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User delegateUser = mock(io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User.class);
        given(authenticatedUser.getDelegate()).willReturn(delegateUser);
        given(delegateUser.getUser()).willReturn(user);

        var data = ThymeleafDataHelper.generateData(routingContext, null, null);
        assertThat(data.get(USER_CONTEXT_KEY), instanceOf(UserProperties.class));
        assertThat(data.get(PARAM_CONTEXT_KEY), instanceOf(Map.class));
        assertTrue(((Map<String, Object>)data.get(PARAM_CONTEXT_KEY)).containsKey("param1"));
        assertTrue(((Map<String, Object>)data.get(PARAM_CONTEXT_KEY)).containsKey("param2"));
        assertEquals(((Map<String, Object>)data.get(PARAM_CONTEXT_KEY)).get("param2"), "value2-1");
    }

    @Test
    public void shouldProvideRequestParameters_NoOverride() {
        final HttpServerRequest serverRequest = mock(HttpServerRequest.class);
        final io.vertx.core.http.HttpServerRequest delegatedServerRequest = mock(io.vertx.core.http.HttpServerRequest.class);
        given(delegatedServerRequest.method()).willReturn(HttpMethod.POST);
        given(delegatedServerRequest.params()).willReturn(MultiMap.caseInsensitiveMultiMap()
                .add("param1", "value")
                .add("param2", "value2-1")
                .add("param2", "value2-2"));
        given(serverRequest.getDelegate()).willReturn(delegatedServerRequest);
        given(routingContext.request()).willReturn(serverRequest);

        final var dataFromContext = new HashMap<String, Object>();
        final var params = new HashMap<>();
        params.put("param2", "original");
        dataFromContext.put(PARAM_CONTEXT_KEY, params);
        given(routingContext.data()).willReturn(dataFromContext);

        given(routingContext.get(eq(USER_CONTEXT_KEY))).willReturn(null);
        io.vertx.rxjava3.ext.auth.User authenticatedUser = mock(io.vertx.rxjava3.ext.auth.User.class);
        given(routingContext.user()).willReturn(authenticatedUser);
        io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User delegateUser = mock(io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User.class);
        given(authenticatedUser.getDelegate()).willReturn(delegateUser);
        given(delegateUser.getUser()).willReturn(user);

        var data = ThymeleafDataHelper.generateData(routingContext, null, null);
        assertThat(data.get(USER_CONTEXT_KEY), instanceOf(UserProperties.class));
        assertThat(data.get(PARAM_CONTEXT_KEY), instanceOf(Map.class));
        assertTrue(((Map<String, Object>)data.get(PARAM_CONTEXT_KEY)).containsKey("param1"));
        assertTrue(((Map<String, Object>)data.get(PARAM_CONTEXT_KEY)).containsKey("param2"));
        assertEquals(((Map<String, Object>)data.get(PARAM_CONTEXT_KEY)).get("param2"), "original");
    }
}
