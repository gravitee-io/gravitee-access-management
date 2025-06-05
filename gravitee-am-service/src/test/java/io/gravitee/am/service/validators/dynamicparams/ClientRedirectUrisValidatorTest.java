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
package io.gravitee.am.service.validators.dynamicparams;

import org.junit.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ClientRedirectUrisValidatorTest {


    ClientRedirectUrisValidator validator = new ClientRedirectUrisValidator();

    @Test
    public void should_invalidated_redirect_uris_with_same_hostname_and_path(){
        List<String> redirectUris = List.of("https://test.com/test", "https://test.com/test?test=test");
        assertFalse(validator.validateRedirectUris(redirectUris));
    }

    @Test
    public void should_invalidated_redirect_uris_with_same_hostname_and_path_2(){
        List<String> redirectUris = List.of("https://test.com", "https://test.com?test=test");
        assertFalse(validator.validateRedirectUris(redirectUris));
    }

    @Test
    public void should_validated_redirect_uris_with_diff_hostname_and_path(){
        List<String> redirectUris = List.of("https://test.com/test2", "https://test.com/test?test=test");
        assertTrue(validator.validateRedirectUris(redirectUris));
    }

    @Test
    public void should_validated_single_redirect_uri(){
        List<String> redirectUris = List.of("https://test.com/test2");
        assertTrue(validator.validateRedirectUris(redirectUris));
    }

    @Test
    public void should_validated_empty_redirect_uri(){
        List<String> redirectUris = List.of();
        assertTrue(validator.validateRedirectUris(redirectUris));
    }

}