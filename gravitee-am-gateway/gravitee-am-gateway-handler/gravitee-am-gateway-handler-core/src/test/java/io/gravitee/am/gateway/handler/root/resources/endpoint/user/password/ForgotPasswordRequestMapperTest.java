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
package io.gravitee.am.gateway.handler.root.resources.endpoint.user.password;

import io.gravitee.am.gateway.handler.root.service.user.model.ForgotPasswordParameters;
import io.gravitee.am.model.account.AccountSettings;
import io.gravitee.am.model.account.FormField;
import io.vertx.rxjava3.core.http.HttpServerRequest;
import io.vertx.rxjava3.ext.web.RoutingContext;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ForgotPasswordRequestMapperTest {

    @Mock
    private RoutingContext context;

    @Mock
    private HttpServerRequest request;

    @Test
    public void shouldMapCustomFormFields() {
        when(context.request()).thenReturn(request);
        when(request.getParam("employeeId")).thenReturn("E-42");

        AccountSettings settings = new AccountSettings();
        settings.setResetPasswordCustomForm(true);
        FormField field = new FormField();
        field.setKey("employeeId");
        settings.setResetPasswordCustomFormFields(List.of(field));

        ForgotPasswordParameters parameters = ForgotPasswordRequestMapper.toParameters(context, settings);

        assertEquals(Map.of("employeeId", "E-42"), parameters.getLookupValues());
    }

    @Test
    public void shouldMapDefaultEmailField() {
        when(context.request()).thenReturn(request);
        when(request.getParam("email")).thenReturn("user@test.com");

        ForgotPasswordParameters parameters = ForgotPasswordRequestMapper.toParameters(context, new AccountSettings());

        assertEquals("user@test.com", parameters.getEmail());
    }
}
