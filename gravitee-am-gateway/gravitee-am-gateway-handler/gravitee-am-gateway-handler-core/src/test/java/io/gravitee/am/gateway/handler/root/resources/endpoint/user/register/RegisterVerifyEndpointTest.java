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
package io.gravitee.am.gateway.handler.root.resources.endpoint.user.register;

import io.gravitee.am.gateway.handler.root.resources.handler.dummies.SpyRoutingContext;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.login.LoginSettings;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.buffer.impl.BufferImpl;
import io.vertx.core.http.HttpMethod;
import io.vertx.rxjava3.core.buffer.Buffer;
import io.vertx.rxjava3.ext.web.common.template.TemplateEngine;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static io.gravitee.am.common.utils.ConstantKeys.ERROR_DESCRIPTION_PARAM_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.ERROR_PARAM_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.LOGIN_ACTION_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.REGISTRATION_VERIFY_SUCCESS;
import static io.gravitee.am.common.utils.ConstantKeys.SUCCESS_PARAM_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.TOKEN_PARAM_KEY;
import static io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest.CONTEXT_PATH;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
public class RegisterVerifyEndpointTest {

    @Mock
    private TemplateEngine templateEngine;

    @Mock
    private Domain domain;

    private RegisterVerifyEndpoint registerVerifyEndpoint;
    private SpyRoutingContext context;

    @BeforeEach
    public void setup() {
        registerVerifyEndpoint = new RegisterVerifyEndpoint(domain, templateEngine);
        context = new SpyRoutingContext();
        context.setMethod(HttpMethod.GET);
        context.put(CONTEXT_PATH, "");
        context.put(TOKEN_PARAM_KEY, "someToken");
        context.request().params().add(TOKEN_PARAM_KEY, "someToken");
    }

    @Test
    @DisplayName("Must return 405 due to wrong method")
    public void must_fail_due_to_wrong_method() {
        context.setMethod(HttpMethod.POST);

        registerVerifyEndpoint.handle(context);

        assertTrue(context.failed());
        assertEquals(405, context.statusCode());
    }

    @Test
    @DisplayName("Must render page with success")
    public void must_render_page_with_success() {
        when(templateEngine.render(anyMap(), anyString())).thenReturn(Single.just(new Buffer(BufferImpl.buffer())));
        registerVerifyEndpoint.handle(context);

        Awaitility.await().until(() -> context.ended());
        assertNull(context.get(ERROR_PARAM_KEY));
        assertNull(context.request().params().get(ERROR_PARAM_KEY));

        assertEquals(REGISTRATION_VERIFY_SUCCESS, context.get(SUCCESS_PARAM_KEY));
        assertEquals("/login", context.get(LOGIN_ACTION_KEY));
    }

    @Test
    @DisplayName("Must render page with success with identifier-first login action")
    public void must_render_page_with_success_with_login_action_id_first_login() {
        final LoginSettings loginSettings = new LoginSettings();
        loginSettings.setIdentifierFirstEnabled(true);
        when(domain.getLoginSettings()).thenReturn(loginSettings);
        when(templateEngine.render(anyMap(), anyString())).thenReturn(Single.just(new Buffer(BufferImpl.buffer())));

        registerVerifyEndpoint.handle(context);

        Awaitility.await().until(() -> context.ended());
        assertNull(context.get(ERROR_PARAM_KEY));
        assertNull(context.request().params().get(ERROR_PARAM_KEY));

        assertEquals(REGISTRATION_VERIFY_SUCCESS, context.get(SUCCESS_PARAM_KEY));
        assertEquals("/login/identifier", context.get(LOGIN_ACTION_KEY));
    }

    @Test
    @DisplayName("Must render page with error")
    public void must_render_page_with_error() {
        when(templateEngine.render(anyMap(), anyString())).thenReturn(Single.just(new Buffer(BufferImpl.buffer())));
        context.put(ERROR_PARAM_KEY, "invalid_token");
        context.put(ERROR_DESCRIPTION_PARAM_KEY, "there was an error");

        registerVerifyEndpoint.handle(context);

        Awaitility.await().until(() -> context.ended());
        assertNull(context.get(SUCCESS_PARAM_KEY));
        assertEquals("invalid_token", context.get(ERROR_PARAM_KEY));
        assertEquals("there was an error", context.get(ERROR_DESCRIPTION_PARAM_KEY));
        assertEquals("/login", context.get(LOGIN_ACTION_KEY));
    }

    @Test
    @DisplayName("Must render page with error from request params")
    public void must_render_page_with_error_from_request_params() {
        when(templateEngine.render(anyMap(), anyString())).thenReturn(Single.just(new Buffer(BufferImpl.buffer())));
        context.request().params().add(ERROR_PARAM_KEY, "invalid_token");
        context.request().params().add(ERROR_DESCRIPTION_PARAM_KEY, "there was an error");

        registerVerifyEndpoint.handle(context);

        Awaitility.await().until(() -> context.ended());
        assertNull(context.get(SUCCESS_PARAM_KEY));
        assertEquals("invalid_token", context.get(ERROR_PARAM_KEY));
        assertEquals("there was an error", context.request().params().get(ERROR_DESCRIPTION_PARAM_KEY));
        assertEquals("/login", context.get(LOGIN_ACTION_KEY));
    }

    @Test
    @DisplayName("Must not render page error with template")
    public void must_not_render_page_with_success_template_error() {
        when(templateEngine.render(anyMap(), anyString())).thenReturn(Single.error(new IllegalArgumentException("cannot render page")));
        registerVerifyEndpoint.handle(context);

        Awaitility.await().until(() -> context.failed());
    }
}
