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