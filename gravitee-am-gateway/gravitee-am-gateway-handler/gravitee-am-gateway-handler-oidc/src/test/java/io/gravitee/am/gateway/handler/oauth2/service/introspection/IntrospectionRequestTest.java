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
package io.gravitee.am.gateway.handler.oauth2.service.introspection;

import io.gravitee.am.common.oauth2.TokenTypeHint;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;

public class IntrospectionRequestTest {

    @Test
    public void should_create_introspection_request(){
        IntrospectionRequest request = IntrospectionRequest.builder().token("token").tokenTypeHint(TokenTypeHint.ACCESS_TOKEN).build();
        Assertions.assertEquals("token", request.getToken());
        Assertions.assertEquals(TokenTypeHint.ACCESS_TOKEN, request.getTokenTypeHint().get());
    }

    @Test
    public void should_create_introspection_request_without_hint_if_its_unknown(){
        IntrospectionRequest request = IntrospectionRequest.builder().token("token").tokenTypeHint("unknown").build();
        Assertions.assertEquals("token", request.getToken());
        Assertions.assertTrue(request.getTokenTypeHint().isEmpty());
    }

    @Test
    public void should_create_introspection_request_without_hint(){
        IntrospectionRequest request = IntrospectionRequest.builder().token("token").build();
        Assertions.assertEquals("token", request.getToken());
        Assertions.assertTrue(request.getTokenTypeHint().isEmpty());
    }

    @Test
    public void should_throw_ex_if_token_is_missing(){
        Assertions.assertThrows(NullPointerException.class, () -> IntrospectionRequest.builder().build());
    }

}
