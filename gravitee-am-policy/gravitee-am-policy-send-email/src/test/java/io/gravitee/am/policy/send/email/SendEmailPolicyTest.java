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
package io.gravitee.am.policy.send.email;

import io.gravitee.am.gateway.handler.common.email.EmailService;
import io.gravitee.am.policy.send.email.configuration.SendEmailPolicyConfiguration;
import io.gravitee.gateway.api.ExecutionContext;
import io.gravitee.gateway.api.Request;
import io.gravitee.gateway.api.Response;
import io.gravitee.policy.api.PolicyChain;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class SendEmailPolicyTest {

    @Mock
    private ExecutionContext executionContext;

    @Mock
    private Request request;

    @Mock
    private Response response;

    @Mock
    private PolicyChain policyChain;

    @Mock
    private EmailService emailService;

    @Mock
    private SendEmailPolicyConfiguration configuration;

    @Before
    public void init() {
        when(executionContext.getComponent(EmailService.class)).thenReturn(emailService);
        lenient().when(configuration.getTemplate()).thenReturn("template");
        lenient().when(configuration.getSubject()).thenReturn("subject");
        lenient().when(configuration.getFrom()).thenReturn("from");
        lenient().when(configuration.getFromName()).thenReturn("fromName");
        lenient().when(configuration.getTo()).thenReturn("to");
        lenient().when(configuration.getContent()).thenReturn("content");
    }

    @Test
    public void shouldSendEmail() {
        doNothing().when(emailService).send(any());
        new SendEmailPolicy(configuration).onRequest(request, response, executionContext, policyChain);
        verify(policyChain, never()).failWith(any());
        verify(policyChain).doNext(any(), any());
    }

    @Test
    public void shouldIgnoreError() {
        doThrow(new RuntimeException("technical exception")).when(emailService).send(any());
        new SendEmailPolicy(configuration).onRequest(request, response, executionContext, policyChain);
        verify(policyChain, never()).failWith(any());
        verify(policyChain).doNext(any(), any());
    }
}
