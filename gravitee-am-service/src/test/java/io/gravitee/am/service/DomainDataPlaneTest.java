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
package io.gravitee.am.service;

import io.gravitee.am.dataplane.api.DataPlane;
import io.gravitee.am.dataplane.api.DataPlaneDescription;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.login.WebAuthnSettings;
import org.junit.Test;

import javax.xml.crypto.Data;

import static org.junit.jupiter.api.Assertions.*;

public class DomainDataPlaneTest {

    @Test
    public void if_webauthn_settings_is_empty_should_return_gateway_url_from_dataplane() {
        Domain domain = new Domain();
        DataPlaneDescription desc = new DataPlaneDescription("id", "name", "jdbc", "base", "http://gravitee.io");
        DomainDataPlane domainDataPlane = new DomainDataPlane(domain, desc);
        String webAuthnOrigin = domainDataPlane.getWebAuthnOrigin();

        assertEquals("http://gravitee.io", webAuthnOrigin);
    }

    @Test
    public void should_return_webauthn_origin_if_its_present() {
        Domain domain = new Domain();
        WebAuthnSettings webAuthnSettings = new WebAuthnSettings();
        webAuthnSettings.setOrigin("http://gravitee2.io");
        domain.setWebAuthnSettings(webAuthnSettings);
        DataPlaneDescription desc = new DataPlaneDescription("id", "name", "jdbc", "base", "http://gravitee.io");
        DomainDataPlane domainDataPlane = new DomainDataPlane(domain, desc);
        String webAuthnOrigin = domainDataPlane.getWebAuthnOrigin();

        assertEquals("http://gravitee2.io", webAuthnOrigin);
    }

}