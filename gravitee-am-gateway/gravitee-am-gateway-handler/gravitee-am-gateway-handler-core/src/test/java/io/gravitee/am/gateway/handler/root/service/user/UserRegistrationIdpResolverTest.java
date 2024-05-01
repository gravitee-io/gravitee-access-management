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
package io.gravitee.am.gateway.handler.root.service.user;

import io.gravitee.am.model.Domain;
import io.gravitee.am.model.User;
import io.gravitee.am.model.account.AccountSettings;
import io.gravitee.am.model.oidc.Client;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;

public class UserRegistrationIdpResolverTest {

    @Test
    public void shouldReturnRegistrationIdpFromDomainWhenClientsIsNull() {
        // given
        Domain domain = new Domain();
        domain.setAccountSettings(accountSettings("DEFAULT_DOMAIN_IDP"));

        User user = new User();

        // when
        String idpByUser = UserRegistrationIdpResolver.getRegistrationIdpForUser(domain, null, user);
        String idpByClient = UserRegistrationIdpResolver.getRegistrationIdp(domain, null);

        // then
        Assertions.assertEquals("DEFAULT_DOMAIN_IDP", idpByUser);
        Assertions.assertEquals("DEFAULT_DOMAIN_IDP", idpByClient);
    }

    @Test
    public void shouldReturnRegistrationIdpFromDomainWhenClientsSettingIsEmpty() {
        // given
        Domain domain = new Domain();
        domain.setAccountSettings(accountSettings("DEFAULT_DOMAIN_IDP"));

        Client client = new Client();

        User user = new User();

        // when
        String idpByUser = UserRegistrationIdpResolver.getRegistrationIdpForUser(domain, client, user);
        String idpByClient = UserRegistrationIdpResolver.getRegistrationIdp(domain, client);

        // then
        Assertions.assertEquals("DEFAULT_DOMAIN_IDP", idpByUser);
        Assertions.assertEquals("DEFAULT_DOMAIN_IDP", idpByClient);
    }

    @Test
    public void shouldReturnRegistrationIdpFromClientAccountSettingsWhenItsNotInherited() {
        // given
        Domain domain = new Domain();
        domain.setAccountSettings(accountSettings("DEFAULT_DOMAIN_IDP"));

        Client client = new Client();
        client.setAccountSettings(accountSettings("DEFAULT_CLIENT_IDP", false));

        User user = new User();

        // when
        String idpByUser = UserRegistrationIdpResolver.getRegistrationIdpForUser(domain, client, user);
        String idpByClient = UserRegistrationIdpResolver.getRegistrationIdp(domain, client);

        // then
        Assertions.assertEquals("DEFAULT_CLIENT_IDP", idpByUser);
        Assertions.assertEquals("DEFAULT_CLIENT_IDP", idpByClient);
    }

    @Test
    public void shouldReturnRegistrationIdpFromDomainAccountSettingsWhenClientSettingsIsInherited() {
        // given
        Domain domain = new Domain();
        domain.setAccountSettings(accountSettings("DEFAULT_DOMAIN_IDP"));

        Client client = new Client();
        client.setAccountSettings(accountSettings("DEFAULT_CLIENT_IDP", true));

        User user = new User();

        // when
        String idpByUser = UserRegistrationIdpResolver.getRegistrationIdpForUser(domain, client, user);
        String idpByClient = UserRegistrationIdpResolver.getRegistrationIdp(domain, client);

        // then
        Assertions.assertEquals("DEFAULT_DOMAIN_IDP", idpByUser);
        Assertions.assertEquals("DEFAULT_DOMAIN_IDP", idpByClient);
    }

    @Test
    public void shouldReturnRegistrationIdpFromUserSourceIfNorDomainAndClientHasSuchSettings() {
        // given
        Domain domain = new Domain();
        domain.setId("ID");
        Client client = new Client();
        User user = new User();
        user.setSource("USER_IDP");

        // when
        String idpByUser = UserRegistrationIdpResolver.getRegistrationIdpForUser(domain, client, user);

        // then
        Assertions.assertEquals("USER_IDP", idpByUser);
    }

    @Test
    public void should_return_default_registration_idp_when_such_not_found_for_domain_nor_client_nor_user() {
        // given
        Domain domain = new Domain();
        domain.setId("ID");
        Client client = new Client();
        User user = new User();

        // when
        String idpByUser = UserRegistrationIdpResolver.getRegistrationIdpForUser(domain, client, user);
        String idpByClient = UserRegistrationIdpResolver.getRegistrationIdp(domain, client);

        // then
        Assertions.assertEquals("default-idp-ID", idpByUser);
        Assertions.assertEquals("default-idp-ID", idpByClient);
    }

    private AccountSettings accountSettings(String idpForRegistration) {
        return accountSettings(idpForRegistration, true);
    }

    private AccountSettings accountSettings(String idpForRegistration, boolean inherited) {
        AccountSettings accountSettings = new AccountSettings();
        accountSettings.setDefaultIdentityProviderForRegistration(idpForRegistration);
        accountSettings.setInherited(inherited);
        return accountSettings;
    }
}