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
package io.gravitee.am.service.model;

import io.gravitee.am.model.Client;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;


/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
@RunWith(JUnit4.class)
public class PatchClientTest {

    @Test
    public void testPatch() {
        //Build Object to patch
        Client toPatch = new Client();
        toPatch.setClientName("oldName");
        toPatch.setClientSecret("expectedSecret");
        toPatch.setCertificate("shouldDisappear");
        toPatch.setAccessTokenValiditySeconds(7200);
        toPatch.setRefreshTokenValiditySeconds(3600);

        //Build patcher
        PatchClient patcher = new PatchClient();
        patcher.setClientName(Optional.of("expectedClientName"));
        patcher.setCertificate(Optional.empty());
        patcher.setAccessTokenValiditySeconds(Optional.of(14400));
        patcher.setRefreshTokenValiditySeconds(Optional.empty());

        //Apply patch
        Client result = patcher.patch(toPatch);

        //Checks
        assertNotNull(result);
        assertEquals("Client name should have been replaced","expectedClientName",result.getClientName());
        assertEquals("Client secret should have been kept","expectedSecret", result.getClientSecret());
        assertNull("Certificate should have been erased",result.getCertificate());
        assertEquals("Access token validity should have been replaced",14400,result.getAccessTokenValiditySeconds());
        assertEquals("Refresh token validity should have been removed",0, result.getRefreshTokenValiditySeconds());
    }
}
