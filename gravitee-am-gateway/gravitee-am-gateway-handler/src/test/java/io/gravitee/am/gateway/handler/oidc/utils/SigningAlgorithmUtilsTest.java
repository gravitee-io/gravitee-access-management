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
package io.gravitee.am.gateway.handler.oidc.utils;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static org.junit.Assert.*;

/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
@RunWith(JUnit4.class)
public class SigningAlgorithmUtilsTest {

    @Test
    public void isValidUserinfoSigningAlg_nok() {
        assertFalse("should not be supported", SigningAlgorithmUtils.isValidUserinfoSigningAlg("unknown"));
    }

    @Test
    public void isValidUserinfoSigningAlg_ok() {
        assertTrue("should be supported", SigningAlgorithmUtils.isValidUserinfoSigningAlg("RS512"));
    }

    @Test
    public void supportedUserinfoSigningAlg() {
        assertTrue("should have at least RS256", SigningAlgorithmUtils.getSupportedUserinfoSigningAlg().contains("RS256"));
    }

    @Test
    public void isValidIdTokenSigningAlg_nok() {
        assertFalse("should not be supported", SigningAlgorithmUtils.isValidIdTokenSigningAlg("unknown"));
    }

    @Test
    public void isValidIdTokenSigningAlg_ok() {
        assertTrue("should be supported", SigningAlgorithmUtils.isValidIdTokenSigningAlg("HS512"));
    }

    @Test
    public void supportedIdTokenSigningAlg() {
        assertTrue("should have at least RS256", SigningAlgorithmUtils.getSupportedIdTokenSigningAlg().contains("RS256"));
    }
}
