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
package io.gravitee.am.gateway.handler.root.resources.handler.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.common.audit.EventType;
import io.gravitee.am.common.audit.Status;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.auth.idp.IdentityProviderManager;
import io.gravitee.am.gateway.handler.common.password.PasswordPolicyManager;
import io.gravitee.am.gateway.handler.common.vertx.RxWebTestBase;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.ErrorHandler;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.User;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.PasswordService;
import io.gravitee.am.service.exception.InvalidPasswordException;
import io.vertx.core.http.HttpMethod;
import io.vertx.rxjava3.core.buffer.Buffer;
import io.vertx.rxjava3.ext.web.handler.BodyHandler;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atMostOnce;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class PasswordPolicyRequestParseHandlerTest extends RxWebTestBase {

    @Mock
    private PasswordService passwordValidator;

    @Mock
    private PasswordPolicyManager passwordPolicyManager;

    @Mock
    private IdentityProviderManager identityProviderManager;

    @Mock
    private AuditService auditService;

    private PasswordPolicyRequestParseHandler passwordPolicyRequestParseHandler;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        passwordPolicyRequestParseHandler = new PasswordPolicyRequestParseHandler(passwordValidator, passwordPolicyManager, identityProviderManager, new Domain(), auditService);

        router.route()
                .handler(BodyHandler.create())
                .failureHandler(new ErrorHandler());
    }

    @Test
    public void shouldNotHandle_invalid_password() throws Exception {

        router.route().order(-1).handler(routingContext -> {
            User user = new User();
            user.setId("123");
            user.setUsername("username");
            user.setReferenceId("domainId");
            user.setReferenceType(ReferenceType.DOMAIN);
            routingContext.put(ConstantKeys.USER_CONTEXT_KEY, user);
            routingContext.next();
        });
        router.route("/")
                .handler(passwordPolicyRequestParseHandler)
                .handler(rc -> rc.response().end());

        when(identityProviderManager.getIdentityProvider(anyString())).thenReturn(new IdentityProvider());
        doThrow(InvalidPasswordException.class).when(passwordValidator).validate(anyString(), any(), any());

        testRequest(HttpMethod.POST, "/", req -> {
            Buffer buffer = Buffer.buffer();
            buffer.appendString("password=password");
            req.headers().set("content-length", String.valueOf(buffer.length()));
            req.headers().set("content-type", "application/x-www-form-urlencoded");
            req.write(buffer);
        }, resp -> {
            String location = resp.headers().get("location");
            assertNotNull(location);
            assertTrue(location.contains("warning=invalid_password_value"));
        }, 302, "Found", null);
        
        verify(auditService,atMostOnce()).report(any());
        verify(auditService).report(argThat(builder -> Status.FAILURE.equals(builder.build(new ObjectMapper()).getOutcome().getStatus())));
        verify(auditService).report(argThat(builder -> builder.build(new ObjectMapper()).getType().equals(EventType.USER_PASSWORD_VALIDATION)));
    }

    @Test
    public void shouldHandle() throws Exception {
        router.route("/")
                .handler(passwordPolicyRequestParseHandler)
                .handler(rc -> rc.response().end());

        when(identityProviderManager.getIdentityProvider(anyString())).thenReturn(new IdentityProvider());
        doNothing().when(passwordValidator).validate(anyString(), any(), any());

        testRequest(HttpMethod.POST, "/", req -> {
            Buffer buffer = Buffer.buffer();
            buffer.appendString("password=password");
            req.headers().set("content-length", String.valueOf(buffer.length()));
            req.headers().set("content-type", "application/x-www-form-urlencoded");
            req.write(buffer);
        }, 200, "OK", null);
    }
}
