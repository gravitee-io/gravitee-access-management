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
package io.gravitee.am.gateway.handler.root.resources.endpoint.mfa;

import io.gravitee.am.gateway.handler.common.utils.HashUtil;
import io.gravitee.am.gateway.handler.root.resources.handler.dummies.MockHttpServerRequest;
import io.gravitee.am.service.exception.EnrollmentChannelValidationException;
import io.vertx.rxjava3.core.http.HttpServerRequest;
import io.vertx.rxjava3.core.http.HttpServerResponse;
import io.vertx.rxjava3.ext.web.RoutingContext;
import io.vertx.rxjava3.ext.web.Session;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import static io.gravitee.am.common.utils.ConstantKeys.ERROR_HASH;
import static io.gravitee.am.common.utils.ConstantKeys.MFA_ENROLL_VALIDATION_FAILED;
import static io.gravitee.am.gateway.handler.common.utils.HashUtil.generateSHA256;
import static io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest.CONTEXT_PATH;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;

@RunWith(MockitoJUnitRunner.class)
public class MFAEnrollFailureHandlerTest {

    private MFAEnrollFailureHandler handler = new MFAEnrollFailureHandler();

    @Mock
    RoutingContext ctx;

    @Mock
    HttpServerResponse response;

    @Mock
    HttpServerRequest request;

    @Mock
    Session session;

    @Before
    public void setUp() throws Exception {
        Mockito.when(ctx.response()).thenReturn(response);
        Mockito.when(ctx.request()).thenReturn(request);
        Mockito.when(ctx.session()).thenReturn(session);
        Mockito.when(ctx.get(CONTEXT_PATH)).thenReturn("test-domain");
    }

    @Test
    public void should_put_error_message_from_exception(){
        // given
        Mockito.when(ctx.failure()).thenReturn(new EnrollmentChannelValidationException("errorMessage"));
        Mockito.when(request.uri()).thenReturn("https://am.com/mfa/enroll");

        // when
        handler.doHandle(ctx);

        // verify
        Mockito.verify(response).putHeader(eq("Location"), eq("test-domain/mfa/enroll?error=mfa_enroll_failed&error_code=enrollment_channel_invalid&error_description=errorMessage"));
    }

    @Test
    public void should_put_default_error_message_then_ex_is_missing(){
        // given
        Mockito.when(request.uri()).thenReturn("https://am.com/mfa/enroll");

        // when
        handler.doHandle(ctx);

        // verify
        Mockito.verify(response).putHeader(eq("Location"), eq("test-domain/mfa/enroll?error=mfa_enroll_failed&error_code=enrollment_channel_invalid&error_description=MFA+Enrollment+failed+for+unexpected+reason"));
    }

    @Test
    public void should_put_error_hash_to_session(){
        // given
        Mockito.when(ctx.failure()).thenReturn(new EnrollmentChannelValidationException("errorMessage"));
        Mockito.when(request.uri()).thenReturn("https://am.com/mfa/enroll");

        // when
        handler.doHandle(ctx);

        // verify
        String expectedHash = generateSHA256(MFA_ENROLL_VALIDATION_FAILED + "$" + "errorMessage");
        Mockito.verify(session).put(eq(ERROR_HASH), eq(expectedHash));
    }

}