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
package io.gravitee.am.model;

import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.model.oidc.JWKSet;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.*;

/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
public class ClientTest {

    @Test
    public void testClone() throws CloneNotSupportedException {

        Map<String, Object> customs = new HashMap<>();
        customs.put("one", "one");
        customs.put("two", "two");

        Client from = new Client();
        from.setClientName("original");
        from.setRedirectUris(Stream.of("http://host/callback", "http://host/login").collect(Collectors.toList()));
        from.setJwks(new JWKSet());

        Client to = from.clone();
        //client name
        assertTrue("same name", from.getClientName().equals(to.getClientName()));

        //redirect uris
        assertTrue("same redirect uris size", to.getRedirectUris() != null && to.getRedirectUris().size() == from.getRedirectUris().size());
        assertTrue("same redirect uris values", to.getRedirectUris().containsAll(from.getRedirectUris()));
        assertFalse("not same object reference", from.getRedirectUris() == to.getRedirectUris());
        //customs
        //assertTrue("same customs information values",to.getIdTokenCustomClaims().);
        assertFalse("not same object reference", from.getRedirectUris() == to.getRedirectUris());
    }

    @Test
    public void testGetFactors_based_on_ApplicationFactorSetting() throws CloneNotSupportedException {
        final var client = new Client();
        final var factorSettings = new FactorSettings();
        final var appSettings = new ApplicationFactorSettings();
        appSettings.setId(UUID.randomUUID().toString());
        final var appSettings2 = new ApplicationFactorSettings();
        appSettings2.setId(UUID.randomUUID().toString());
        factorSettings.setApplicationFactors(List.of(appSettings, appSettings2));
        client.setFactorSettings(factorSettings);
        client.setFactors(Set.of(UUID.randomUUID().toString()));

        Assertions.assertTrue(client.getFactors().containsAll(List.of(appSettings2.getId(), appSettings.getId())));
    }

    @Test
    public void testGetFactors_fallback_on_deprecated_if_factorSettings_is_missing() throws CloneNotSupportedException {
        final var client = new Client();
        final var factorSettings = new FactorSettings();
        client.setFactorSettings(factorSettings);
        final var id = UUID.randomUUID().toString();
        client.setFactors(Set.of(id));

        Assertions.assertTrue(client.getFactors().containsAll(List.of(id)));
    }
}
