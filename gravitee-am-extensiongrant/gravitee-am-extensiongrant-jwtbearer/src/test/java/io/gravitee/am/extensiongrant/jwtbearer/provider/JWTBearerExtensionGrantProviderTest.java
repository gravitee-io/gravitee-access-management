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
package io.gravitee.am.extensiongrant.jwtbearer.provider;

import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.extensiongrant.jwtbearer.JWTBearerExtensionGrantConfiguration;
import io.gravitee.am.identityprovider.api.User;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static junit.framework.Assert.assertTrue;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static org.mockito.Mockito.when;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class JWTBearerExtensionGrantProviderTest {

    private static final Pattern SSH_PUB_KEY = Pattern.compile("ssh-(rsa|dsa) ([A-Za-z0-9/+]+=*)( .*)?");

    @InjectMocks
    private JWTBearerExtensionGrantProvider jwtBearerExtensionGrantProvider = new JWTBearerExtensionGrantProvider();

    @Mock
    private JWTBearerExtensionGrantConfiguration jwtBearerTokenGranterConfiguration;

    @Test
    public void testParseKey() {
        final String key = "ssh-rsa AAAAE2VjZHNhLXNoYTItbmlzdHAyNTY=";
        final String key2 = "ssh-rsa AAAAE2VjZHNhLXNoYTItbmlzdHAyNTY= test@test.com";
        final String key3 = "ssh";
        final String key4 = "ssh-rsa";
        assertTrue(SSH_PUB_KEY.matcher(key).matches());
        assertTrue(SSH_PUB_KEY.matcher(key2).matches());
        assertFalse(SSH_PUB_KEY.matcher(key3).matches());
        assertFalse(SSH_PUB_KEY.matcher(key4).matches());
    }

    @Test
    public void testCreateUser_withClaimsMapper() {
        List<Map<String, String>> claimsMapper = new ArrayList<>();
        Map<String, String> claimMapper1 = new HashMap<>();
        claimMapper1.put("assertion_claim", "username");
        claimMapper1.put("token_claim", "username");

        Map<String, String> claimMapper2 = new HashMap<>();
        claimMapper2.put("assertion_claim", "email");
        claimMapper2.put("token_claim", "email");

        claimsMapper.add(claimMapper1);
        claimsMapper.add(claimMapper2);

        when(jwtBearerTokenGranterConfiguration.getClaimsMapper()).thenReturn(claimsMapper);

        Map<String, Object> assertionClaims = new HashMap<>();
        assertionClaims.put("username", "test_username");
        assertionClaims.put("email", "test_email");

        User user = jwtBearerExtensionGrantProvider.createUser(new JWT(assertionClaims));

        assertEquals(3, user.getAdditionalInformation().values().size());
        assertEquals("test_username", user.getAdditionalInformation().get("username"));
        assertEquals("test_email", user.getAdditionalInformation().get("email"));

    }
}
