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

import io.gravitee.am.extensiongrant.jwtbearer.JWTBearerExtensionGrantConfiguration;
import io.gravitee.am.identityprovider.api.User;
import io.jsonwebtoken.Claims;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.*;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.Mockito.when;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class JWTBearerExtensionGrantProviderTest {

    @InjectMocks
    private JWTBearerExtensionGrantProvider jwtBearerExtensionGrantProvider = new JWTBearerExtensionGrantProvider();

    @Mock
    private JWTBearerExtensionGrantConfiguration jwtBearerTokenGranterConfiguration;

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
        assertionClaims.put("sub", "test");
        assertionClaims.put("username", "test_username");
        assertionClaims.put("email", "test_email");

        Claims claims = createClaims(assertionClaims);


        User user = jwtBearerExtensionGrantProvider.createUser(claims);

        assertEquals(3, user.getAdditionalInformation().values().size());
        assertEquals("test_username", user.getAdditionalInformation().get("username"));
        assertEquals("test_email", user.getAdditionalInformation().get("email"));

    }

    private Claims createClaims(Map<String, Object> claims) {
        return new Claims() {
            @Override
            public int size() {
                return claims.size();
            }

            @Override
            public boolean isEmpty() {
                return claims.isEmpty();
            }

            @Override
            public boolean containsKey(Object key) {
                return claims.containsKey(key);
            }

            @Override
            public boolean containsValue(Object value) {
                return claims.containsValue(value);
            }

            @Override
            public Object get(Object key) {
                return claims.get(key);
            }

            @Override
            public Object put(String key, Object value) {
                return claims.put(key, value);
            }

            @Override
            public Object remove(Object key) {
                return claims.remove(key);
            }

            @Override
            public void putAll(Map<? extends String, ?> m) {
                claims.putAll(m);
            }

            @Override
            public void clear() {
                claims.clear();
            }

            @Override
            public Set<String> keySet() {
                return claims.keySet();
            }

            @Override
            public Collection<Object> values() {
                return claims.values();
            }

            @Override
            public Set<Entry<String, Object>> entrySet() {
                return claims.entrySet();
            }

            @Override
            public boolean equals(Object o) {
                return claims.equals(o);
            }

            @Override
            public int hashCode() {
                return claims.hashCode();
            }

            @Override
            public String getIssuer() {
                return null;
            }

            @Override
            public Claims setIssuer(String iss) {
                return null;
            }

            @Override
            public String getSubject() {
                return (String) claims.get("sub");
            }

            @Override
            public Claims setSubject(String sub) {
                return null;
            }

            @Override
            public String getAudience() {
                return null;
            }

            @Override
            public Claims setAudience(String aud) {
                return null;
            }

            @Override
            public Date getExpiration() {
                return null;
            }

            @Override
            public Claims setExpiration(Date exp) {
                return null;
            }

            @Override
            public Date getNotBefore() {
                return null;
            }

            @Override
            public Claims setNotBefore(Date nbf) {
                return null;
            }

            @Override
            public Date getIssuedAt() {
                return null;
            }

            @Override
            public Claims setIssuedAt(Date iat) {
                return null;
            }

            @Override
            public String getId() {
                return null;
            }

            @Override
            public Claims setId(String jti) {
                return null;
            }

            @Override
            public <T> T get(String claimName, Class<T> requiredType) {
                return null;
            }
        };
    }
}
