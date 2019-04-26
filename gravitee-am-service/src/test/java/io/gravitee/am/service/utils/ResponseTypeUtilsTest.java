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
package io.gravitee.am.service.utils;

import io.gravitee.am.model.Client;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Arrays;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
@RunWith(JUnit4.class)
public class ResponseTypeUtilsTest {

    @Test
    public void test_code_token_id_token() {
        boolean isValid = ResponseTypeUtils.isValidResponseType(Arrays.asList(
                "code", "token", "id_token",
                "id_token token", "code token", "code id_token","code id_token token")
        );
        assertTrue("Were expecting to be true",isValid);
    }

    @Test
    public void test_unknown_response_type() {
        boolean isValid = ResponseTypeUtils.isValidResponseType(Arrays.asList("code token id_token"));
        assertFalse("Were expecting to be false",isValid);
    }

    @Test
    public void test_empty_response_type() {
        boolean isValid = ResponseTypeUtils.isValidResponseType(Arrays.asList());
        assertFalse("Were expecting to be false",isValid);
    }

    @Test
    public void applyDefaultResponseType() {
        Client client = new Client();
        client.setAuthorizedGrantTypes(Arrays.asList("authorization_code","implicit"));
        client = ResponseTypeUtils.applyDefaultResponseType(client);

        assertTrue("should contain code & token response type",client.getResponseTypes().containsAll(Arrays.asList("code","token")));
    }

    @Test
    public void isImplicit() {
        assertFalse(ResponseTypeUtils.isImplicitFlow(null));
        assertFalse(ResponseTypeUtils.isImplicitFlow("code"));
        assertFalse(ResponseTypeUtils.isImplicitFlow("code id_token token"));

        assertTrue(ResponseTypeUtils.isImplicitFlow("token"));
        assertTrue(ResponseTypeUtils.isImplicitFlow("id_token"));
        assertTrue(ResponseTypeUtils.isImplicitFlow("id_token token"));
    }

    @Test
    public void isHybrid() {
        assertFalse(ResponseTypeUtils.isHybridFlow(null));
        assertFalse(ResponseTypeUtils.isHybridFlow("code"));
        assertFalse(ResponseTypeUtils.isHybridFlow("token"));
        assertFalse(ResponseTypeUtils.isHybridFlow("id_token"));
        assertFalse(ResponseTypeUtils.isHybridFlow("id_token token"));

        assertTrue(ResponseTypeUtils.isHybridFlow("code token"));
        assertTrue(ResponseTypeUtils.isHybridFlow("code id_token"));
        assertTrue(ResponseTypeUtils.isHybridFlow("code id_token token"));
    }

    @Test
    public void requireNonce() {
        assertFalse(ResponseTypeUtils.requireNonce(null));
        assertFalse(ResponseTypeUtils.requireNonce("code"));
        assertFalse(ResponseTypeUtils.requireNonce("token"));
        assertFalse(ResponseTypeUtils.requireNonce("token id_token"));

        assertTrue(ResponseTypeUtils.requireNonce("code token"));
        assertTrue(ResponseTypeUtils.requireNonce("code id_token"));
        assertTrue(ResponseTypeUtils.requireNonce("code id_token token"));
        assertTrue(ResponseTypeUtils.requireNonce("id_token"));
        assertTrue(ResponseTypeUtils.requireNonce("id_token token"));
    }
}
