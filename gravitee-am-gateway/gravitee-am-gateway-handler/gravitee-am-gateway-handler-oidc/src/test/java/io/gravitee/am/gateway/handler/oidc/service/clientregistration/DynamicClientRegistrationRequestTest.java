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
package io.gravitee.am.gateway.handler.oidc.service.clientregistration;

import io.gravitee.am.model.Client;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Optional;

import static org.junit.Assert.*;

/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
public class DynamicClientRegistrationRequestTest {

    Client toPatch;
    DynamicClientRegistrationRequest patcher;

    @Before
    public void setUp() {
        //Build Object to patch
        toPatch = new Client();
        toPatch.setClientName("oldName");
        toPatch.setClientSecret("expectedSecret");
        toPatch.setClientUri("shouldDisappear");
        toPatch.setScopes(Arrays.asList("scopeA","scopeB"));
        toPatch.setAccessTokenValiditySeconds(7200);
        toPatch.setRefreshTokenValiditySeconds(3600);
        toPatch.setResponseTypes(Arrays.asList("old","old2"));
        toPatch.setDefaultMaxAge(1);

        //Build patcher
        patcher = new DynamicClientRegistrationRequest();
        patcher.setClientName(Optional.of("expectedClientName"));
        patcher.setClientUri(Optional.empty());
        patcher.setGrantTypes(Optional.of(Arrays.asList("grant1","grant2")));
        patcher.setResponseTypes(Optional.empty());
        patcher.setScope(Optional.of("scope1 scope2"));
    }

    @Test
    public void testPatch() {
        //Apply patch
        Client result = patcher.patch(toPatch);

        //Checks
        assertNotNull(result);
        assertEquals("Client name should have been replaced","expectedClientName",result.getClientName());
        assertEquals("Client secret should have been kept","expectedSecret", result.getClientSecret());
        assertNull("Client uri should have been erased",result.getClientUri());
        assertEquals("Access token validity should have been kept",7200,result.getAccessTokenValiditySeconds());
        assertEquals("Refresh token validity should have been kept",3600, result.getRefreshTokenValiditySeconds());
        assertEquals("Default Max Age should have been kept",Integer.valueOf(1),result.getDefaultMaxAge());
        assertArrayEquals("Grant types should have been replaced",Arrays.asList("grant1","grant2").toArray(),result.getAuthorizedGrantTypes().toArray());
        assertArrayEquals("Response type should have been replaced by default values",Arrays.asList("code").toArray(),result.getResponseTypes().toArray());
        assertArrayEquals("Scopes should have been replaced",Arrays.asList("scope1","scope2").toArray(), result.getScopes().toArray());
    }

    @Test
    public void testUpdate() {
        //Apply update
        Client result = patcher.update(toPatch);

        //Checks
        assertNotNull(result);
        assertEquals("Client name should have been replaced","expectedClientName",result.getClientName());
        assertEquals("Client secret should have been kept","expectedSecret", result.getClientSecret());
        assertNull("Client uri should have been erased",result.getClientUri());
        assertEquals("Access token validity should have been kept",7200,result.getAccessTokenValiditySeconds());
        assertEquals("Refresh token validity should have been kept",3600, result.getRefreshTokenValiditySeconds());
        assertArrayEquals("Grant types should have been replaced",Arrays.asList("grant1","grant2").toArray(),result.getAuthorizedGrantTypes().toArray());
        assertArrayEquals("Scopes should have been replaced",Arrays.asList("scope1","scope2").toArray(), result.getScopes().toArray());
        assertNull("Response type should be set to null", result.getResponseTypes());
        assertNull("Default max age should be set to null", result.getDefaultMaxAge());
    }
}
