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
package io.gravitee.am.gateway.handler.oidc.service.utils;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
@RunWith(JUnit4.class)
public class JWAlgorithmUtilsTest {

    @Test
    public void isValidUserinfoSigningAlg_nok() {
        assertFalse("should not be supported", JWAlgorithmUtils.isValidUserinfoSigningAlg("unknown"));
    }

    @Test
    public void isValidUserinfoSigningAlg_ok() {
        assertTrue("should be supported", JWAlgorithmUtils.isValidUserinfoSigningAlg("RS512"));
    }

    @Test
    public void supportedUserinfoSigningAlg() {
        assertTrue("should have at least RS256", JWAlgorithmUtils.getSupportedUserinfoSigningAlg().contains("RS256"));
    }

    @Test
    public void isValidIdTokenSigningAlg_nok() {
        assertFalse("should not be supported", JWAlgorithmUtils.isValidIdTokenSigningAlg("unknown"));
    }

    @Test
    public void isValidIdTokenSigningAlg_ok() {
        assertTrue("should be supported", JWAlgorithmUtils.isValidIdTokenSigningAlg("HS512"));
    }

    @Test
    public void supportedIdTokenSigningAlg() {
        assertTrue("should have at least RS256", JWAlgorithmUtils.getSupportedIdTokenSigningAlg().contains("RS256"));
    }

    @Test
    public void isValidIdTokenResponseAlg_nok() {
        assertFalse("should not be supported", JWAlgorithmUtils.isValidIdTokenResponseAlg("unknown"));
    }

    @Test
    public void isValidIdTokenResponseAlg_ok() {
        assertTrue("should be supported", JWAlgorithmUtils.isValidIdTokenResponseAlg("RSA-OAEP-256"));
    }

    @Test
    public void supportedIdTokenResponseAlg() {
        assertTrue("should have at least RSA-OAEP-256", JWAlgorithmUtils.getSupportedIdTokenResponseAlg().contains("RSA-OAEP-256"));
    }


    @Test
    public void isValidIdTokenResponseEnc_nok() {
        assertFalse("should not be supported", JWAlgorithmUtils.isValidIdTokenResponseEnc("unknown"));
    }

    @Test
    public void isValidIdTokenResponseEnc_ok() {
        assertTrue("should be supported", JWAlgorithmUtils.isValidIdTokenResponseEnc("A128CBC-HS256"));
    }

    @Test
    public void supportedIdTokenResponseEnc() {
        assertTrue("should have at least A128CBC-HS256", JWAlgorithmUtils.getSupportedIdTokenResponseEnc().contains("A128CBC-HS256"));
    }

    @Test
    public void defaultIdTokenResponseEnc() {
        assertTrue("should be A128CBC-HS256", "A128CBC-HS256".equals(JWAlgorithmUtils.getDefaultIdTokenResponseEnc()));
    }
}
