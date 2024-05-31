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
package io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal.mfa.utils;

import io.gravitee.am.model.*;
import io.gravitee.am.model.oidc.Client;
import io.vertx.rxjava3.ext.web.RoutingContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class MfaUtilsTest {

    @Mock
    RoutingContext ctx;

    @Mock
    Client client;

    @Test
    void canSkipShouldBeFalseWhenEnrollIsDeactivated() {
        // given
        MFASettings settings = new MFASettings();
        settings.setEnroll(new EnrollSettings());
        settings.setChallenge(new ChallengeSettings());
        settings.getEnroll().setActive(false);
        settings.getEnroll().setType(MfaEnrollType.OPTIONAL);
        settings.getEnroll().setForceEnrollment(false);

        settings.getChallenge().setType(MfaChallengeType.REQUIRED);

        Mockito.when(client.getMfaSettings()).thenReturn(settings);

        // when
        boolean canSkip = MfaUtils.isCanSkip(ctx, client);

        // then
        assertFalse(canSkip);
    }

    @Test
    void getMfaStepUp(){
        Mockito.when(client.getMfaSettings()).thenReturn(null);
        StepUpAuthenticationSettings mfaStepUp = MfaUtils.getMfaStepUp(client);
        assertNotNull(mfaStepUp);
    }


}
