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
package io.gravitee.am.gateway.handler.uma.resources.handler;

import io.gravitee.am.gateway.handler.common.vertx.web.auth.handler.OAuth2AuthHandler;
import io.gravitee.am.gateway.handler.uma.exception.UMAProtectionApiForbiddenException;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.uma.UMASettings;
import io.vertx.reactivex.ext.web.RoutingContext;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.mockito.Mockito.*;

/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class ResourceRegistrationAccessHandlerTest {

    @Mock
    private Domain domain;

    @Mock
    private OAuth2AuthHandler oAuth2AuthHandler;

    @Mock
    private RoutingContext context;

    @InjectMocks
    private UMAProtectionApiAccessHandler handler = new UMAProtectionApiAccessHandler(domain, oAuth2AuthHandler);

    ArgumentCaptor<Throwable> exceptionCaptor = ArgumentCaptor.forClass(Throwable.class);

    @Test
    public void testWhenUmaNotYetSetOnDomain() {
        handler.handle(context);

        verify(context, times(1)).fail(exceptionCaptor.capture());
        Assert.assertTrue("Should return a forbidden exception", exceptionCaptor.getValue() instanceof UMAProtectionApiForbiddenException);
    }

    @Test
    public void testUmaDisabled() {
        when(domain.getUma()).thenReturn(new UMASettings());
        handler.handle(context);

        verify(context, times(1)).fail(exceptionCaptor.capture());
        Assert.assertTrue("Should return a forbidden exception", exceptionCaptor.getValue() instanceof UMAProtectionApiForbiddenException);
    }

    @Test
    public void testUmaEnabled() {
        when(domain.getUma()).thenReturn(new UMASettings().setEnabled(true));
        handler.handle(context);
        verify(this.oAuth2AuthHandler, times(1)).handle(context);
    }
}
