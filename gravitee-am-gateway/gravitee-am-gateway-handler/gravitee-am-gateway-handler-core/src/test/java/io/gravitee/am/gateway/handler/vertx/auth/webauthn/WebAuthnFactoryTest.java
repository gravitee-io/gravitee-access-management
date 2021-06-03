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
package io.gravitee.am.gateway.handler.vertx.auth.webauthn;

import io.gravitee.am.gateway.handler.vertx.auth.webauthn.store.RepositoryCredentialStore;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.login.WebAuthnSettings;
import io.vertx.ext.auth.webauthn.RelyingParty;
import io.vertx.reactivex.core.Vertx;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class WebAuthnFactoryTest {

    @InjectMocks
    private WebAuthnFactory webAuthnFactory = new WebAuthnFactory();

    @Mock
    private Vertx vertx;

    @Mock
    private Domain domain;

    @Mock
    private RepositoryCredentialStore credentialStore;

    @Before
    public void init() {
        when(vertx.getDelegate()).thenReturn(Vertx.vertx().getDelegate());
    }

    @Test
    public void testDefault() {
        when(domain.getWebAuthnSettings()).thenReturn(null);
        WebAuthn webAuthn = webAuthnFactory.getObject();
        RelyingParty relyingParty = webAuthnFactory.getRelyingParty();
        Assert.assertNotNull(webAuthn);
        Assert.assertNotNull(relyingParty);
        Assert.assertEquals("Gravitee.io Access Management", relyingParty.getName());
        Assert.assertEquals("localhost", relyingParty.getId());
    }

    @Test
    public void testCustom_emptySettings() {
        when(domain.getWebAuthnSettings()).thenReturn(new WebAuthnSettings());
        WebAuthn webAuthn = webAuthnFactory.getObject();
        RelyingParty relyingParty = webAuthnFactory.getRelyingParty();
        Assert.assertNotNull(webAuthn);
        Assert.assertNotNull(relyingParty);
        Assert.assertEquals("Gravitee.io Access Management", relyingParty.getName());
        Assert.assertEquals("localhost", relyingParty.getId());
    }

    @Test
    public void testCustom_partialSettings() {
        WebAuthnSettings webAuthnSettings = mock(WebAuthnSettings.class);
        when(webAuthnSettings.getRelyingPartyName()).thenReturn("Custom RP name");
        when(webAuthnSettings.getOrigin()).thenReturn("https://auth.mycompany.com:8443");
        when(domain.getWebAuthnSettings()).thenReturn(webAuthnSettings);
        WebAuthn webAuthn = webAuthnFactory.getObject();
        RelyingParty relyingParty = webAuthnFactory.getRelyingParty();
        Assert.assertNotNull(webAuthn);
        Assert.assertNotNull(relyingParty);
        Assert.assertEquals("Custom RP name", relyingParty.getName());
        Assert.assertEquals("auth.mycompany.com", relyingParty.getId());
    }

    @Test
    public void testCustom_fullSettings() {
        WebAuthnSettings webAuthnSettings = mock(WebAuthnSettings.class);
        when(webAuthnSettings.getRelyingPartyName()).thenReturn("Custom RP name");
        when(webAuthnSettings.getRelyingPartyId()).thenReturn("Custom RP ID");
        when(domain.getWebAuthnSettings()).thenReturn(webAuthnSettings);
        WebAuthn webAuthn = webAuthnFactory.getObject();
        RelyingParty relyingParty = webAuthnFactory.getRelyingParty();
        Assert.assertNotNull(webAuthn);
        Assert.assertNotNull(relyingParty);
        Assert.assertEquals("Custom RP name", relyingParty.getName());
        Assert.assertEquals("Custom RP ID", relyingParty.getId());
    }
}
