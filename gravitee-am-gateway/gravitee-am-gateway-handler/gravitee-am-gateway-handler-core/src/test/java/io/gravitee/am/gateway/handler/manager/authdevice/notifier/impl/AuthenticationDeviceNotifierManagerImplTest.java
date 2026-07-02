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
package io.gravitee.am.gateway.handler.manager.authdevice.notifier.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.model.AuthenticationDeviceNotifier;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class AuthenticationDeviceNotifierManagerImplTest {

    @InjectMocks
    private AuthenticationDeviceNotifierManagerImpl cut = new AuthenticationDeviceNotifierManagerImpl();

    private static final String NOTIFIER_ID = "n1";

    @Before
    public void setUp() {
        AuthenticationDeviceNotifier entity = new AuthenticationDeviceNotifier();
        entity.setId(NOTIFIER_ID);
        entity.setConfiguration("{\"identityProviderId\":\"idp-1\"}");
        cut.getDeviceNotifiers().put(NOTIFIER_ID, entity);
    }

    @Test
    public void returns_notifier_entity_by_id() throws Exception {
        AuthenticationDeviceNotifier result = cut.getAuthDeviceNotifier(NOTIFIER_ID);
        String idpId = new ObjectMapper()
                .readTree(result.getConfiguration())
                .get("identityProviderId")
                .asText();
        assertEquals("idp-1", idpId);
    }

    @Test
    public void returns_null_for_unknown_id() {
        assertNull(cut.getAuthDeviceNotifier("unknown-id"));
    }

}
