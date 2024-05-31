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
import io.vertx.rxjava3.ext.web.RoutingContext;
import io.vertx.rxjava3.ext.web.Session;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Map;

import static io.gravitee.am.common.utils.ConstantKeys.MFA_CHALLENGE_COMPLETED_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.PASSWORDLESS_AUTH_ACTION_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.RISK_ASSESSMENT_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.USER_CONTEXT_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.WEBAUTHN_CREDENTIAL_ID_CONTEXT_KEY;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.mockito.BDDMockito.given;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */

@RunWith(MockitoJUnitRunner.class)
public class RoutingContextUtilsTest {
    @Mock
    private RoutingContext routingContext;
    @Mock
    private Session session;
    @Test
    public void shouldGetUserFromContext() {
        given(routingContext.get(USER_CONTEXT_KEY)).willReturn(new User());
        given(routingContext.data()).willReturn(Map.of("context-data-key1", "context-data-key1-value"));
        var data = RoutingContextUtils.getEvaluableAttributes(routingContext);
        assertThat(data.get(USER_CONTEXT_KEY), instanceOf(User.class));
    }
    @Test
    public void shouldGetSessionInformation() {
        given(session.get(RISK_ASSESSMENT_KEY)).willReturn(RISK_ASSESSMENT_KEY+"value");
        given(session.get(MFA_CHALLENGE_COMPLETED_KEY)).willReturn(MFA_CHALLENGE_COMPLETED_KEY+"value");
        given(session.get(WEBAUTHN_CREDENTIAL_ID_CONTEXT_KEY)).willReturn(WEBAUTHN_CREDENTIAL_ID_CONTEXT_KEY+"value");
        given(session.get(PASSWORDLESS_AUTH_ACTION_KEY)).willReturn(PASSWORDLESS_AUTH_ACTION_KEY+"value");

        given(routingContext.session()).willReturn(session);

        var data = RoutingContextUtils.getEvaluableAttributes(routingContext);
        assertEquals(data.get(RISK_ASSESSMENT_KEY),RISK_ASSESSMENT_KEY+"value");
        assertEquals(data.get(MFA_CHALLENGE_COMPLETED_KEY),MFA_CHALLENGE_COMPLETED_KEY+"value");
        assertEquals(data.get(WEBAUTHN_CREDENTIAL_ID_CONTEXT_KEY),WEBAUTHN_CREDENTIAL_ID_CONTEXT_KEY+"value");
        assertEquals(data.get(PASSWORDLESS_AUTH_ACTION_KEY),PASSWORDLESS_AUTH_ACTION_KEY+"value");
    }
}
