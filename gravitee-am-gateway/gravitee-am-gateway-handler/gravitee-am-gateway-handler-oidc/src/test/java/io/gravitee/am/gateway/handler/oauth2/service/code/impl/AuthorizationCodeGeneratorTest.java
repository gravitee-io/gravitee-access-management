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
package io.gravitee.am.gateway.handler.oauth2.service.code.impl;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class AuthorizationCodeGeneratorTest {


    @Test
    void shouldReplaceAllDoubleDashes(){
        Assertions.assertEquals("asdaSDgdh2341", new AuthorizationCodeGenerator(() -> "asdaSDgdh2341").generate());
        Assertions.assertEquals("asdaSDgd-h2341", new AuthorizationCodeGenerator(() -> "asdaSDgd-h2341").generate());
        Assertions.assertEquals("asdaSDgdh-A2341", new AuthorizationCodeGenerator(() -> "asdaSDgdh--2341").generate());
        Assertions.assertEquals("asdaSDgdh-A-2341", new AuthorizationCodeGenerator(() -> "asdaSDgdh---2341").generate());
        Assertions.assertEquals("asdaSDgdh-A2-A341", new AuthorizationCodeGenerator(() -> "asdaSDgdh--2--341").generate());
    }

    @Test
    void testOfDefaultRandomizer(){
        Assertions.assertNotNull(new AuthorizationCodeGenerator().generate());
    }
}