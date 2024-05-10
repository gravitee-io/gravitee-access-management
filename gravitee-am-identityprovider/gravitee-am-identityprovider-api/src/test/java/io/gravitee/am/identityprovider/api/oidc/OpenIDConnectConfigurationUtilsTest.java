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
package io.gravitee.am.identityprovider.api.oidc;

import org.junit.Test;
import org.junit.jupiter.api.Assertions;

import java.util.Optional;

public class OpenIDConnectConfigurationUtilsTest {

    @Test
    public void shouldExtractCertId() {
        String cfg = "{\"clientId\":\"aaa\",\"clientSecret\":\"aaa\",\"clientAuthenticationMethod\":\"tls_client_auth\",\"clientAuthenticationCertificate\":\"32d89a07-c7a9-48c4-989a-07c7a9b8c4ef\",\"wellKnownUri\":\"https://localhost:9092/test/oidc/.well-known/openid-configuration\",\"responseType\":\"code\",\"encodeRedirectUri\":false,\"useIdTokenForUserInfo\":false,\"signature\":\"RSA_RS256\",\"publicKeyResolver\":\"GIVEN_KEY\",\"connectTimeout\":10000,\"idleTimeout\":10000,\"maxPoolSize\":200,\"storeOriginalTokens\":false}";
        Optional<String> certId = OpenIDConnectConfigurationUtils.extractCertificateId(cfg);
        Assertions.assertEquals("32d89a07-c7a9-48c4-989a-07c7a9b8c4ef", certId.get());
    }

    @Test
    public void shouldReturnEmptyListIfConfigurationIsEmpty() {
        Assertions.assertTrue(OpenIDConnectConfigurationUtils.extractCertificateId("").isEmpty());
    }

    @Test
    public void shouldReturnEmptyListIfConfigurationIsNull() {
        Assertions.assertTrue(OpenIDConnectConfigurationUtils.extractCertificateId(null).isEmpty());
    }

    @Test
    public void shouldReturnEmptyListIfConfigurationIsNotJson() {
        Assertions.assertTrue(OpenIDConnectConfigurationUtils.extractCertificateId("xzcxz").isEmpty());
    }

    @Test
    public void whenCertIdIsNotPresentReturnOptionalEmpty() {
        String cfg = "{\"uri\":\"mongodb://localhost:27017/?connectTimeoutMS=1000&socketTimeoutMS=1000\",\"host\":\"localhost\",\"port\":\"27017\",\"enableCredentials\":false,\"database\":\"gravitee-am\",\"usersCollection\":\"idp_users_addc3d25-7f4f-430f-9c3d-257f4ff30fa3\",\"findUserByUsernameQuery\":\"{username: ?}\",\"findUserByEmailQuery\":\"{email: ?}\",\"usernameField\":\"username\",\"passwordField\":\"password\",\"passwordEncoder\":\"BCrypt\",\"passwordEncoderOptions\":{\"rounds\":10}}";
        Optional<String> certId = OpenIDConnectConfigurationUtils.extractCertificateId(cfg);
        Assertions.assertTrue(certId.isEmpty());
    }
}