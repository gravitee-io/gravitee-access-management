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
package io.gravitee.am.gateway.handler.root.resources.handler.webauthn;

import io.gravitee.am.model.Credential;
import org.junit.Before;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Date;
import java.util.function.Supplier;

@RunWith(MockitoJUnitRunner.class)
public class WebAuthnAuthenticatorIntegrityTest {

    @Mock
    Supplier<Date> timestamp;

    private static final Date NOW = new Date();

    @Before
    public void setUp() throws Exception {
        Mockito.when(timestamp.get()).thenReturn(NOW);
    }

    @Test
    public void shouldNotUpdateLastCheckedAtWhenAuthIntegrityIsTurnedOff(){
        Credential credential = new Credential();
        WebAuthnAuthenticatorIntegrity integrity = new WebAuthnAuthenticatorIntegrity(false, 3600, timestamp);

        integrity.updateLastCheckedDate(credential);

        Assertions.assertNull(credential.getLastCheckedAt());
    }

    @Test
    public void shouldUpdateLastCheckedAtWhenItsNullAndAuthIntegrityIsTurnedOn(){
        Credential credential = new Credential();
        WebAuthnAuthenticatorIntegrity integrity = new WebAuthnAuthenticatorIntegrity(true, 0, timestamp);

        integrity.updateLastCheckedDate(credential);

        Assertions.assertEquals(NOW, credential.getLastCheckedAt());
    }

    @Test
    public void shouldNotUpdateLastCheckedAtWhenItsBeforeTheMaxAgeAndAuthIntegrityIsTurnedOn(){
        int maxAgeSeconds = 3600;
        Date oneMinuteAgo = Date.from(NOW.toInstant().minusSeconds(60));

        Credential credential = new Credential();
        credential.setLastCheckedAt(oneMinuteAgo);

        WebAuthnAuthenticatorIntegrity integrity = new WebAuthnAuthenticatorIntegrity(true, maxAgeSeconds, timestamp);

        integrity.updateLastCheckedDate(credential);

        Assertions.assertEquals(oneMinuteAgo, credential.getLastCheckedAt());
    }

    @Test
    public void shouldUpdateLastCheckedAtWhenItsAfterTheMaxAgeAndAuthIntegrityIsTurnedOn(){
        int maxAgeSeconds = 3600; // one hour
        Date twoHoursAgo = Date.from(NOW.toInstant().minusSeconds(2 * 3600));

        Credential credential = new Credential();
        credential.setLastCheckedAt(twoHoursAgo);

        WebAuthnAuthenticatorIntegrity integrity = new WebAuthnAuthenticatorIntegrity(true, maxAgeSeconds, timestamp);

        integrity.updateLastCheckedDate(credential);

        Assertions.assertEquals(NOW, credential.getLastCheckedAt());
    }
}