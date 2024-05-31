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
package io.gravitee.am.management.service.impl.upgrades;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.management.service.impl.upgrades.NonBCryptIterationsRoundsUpgrader.IdpJsonConfiguration;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class IdpJsonConfigurationTest {

    @Test
    public void shouldProcessIdpCfg() {
        ObjectMapper objectMapper = new ObjectMapper();
        IdpJsonConfiguration cfg = new IdpJsonConfiguration();
        cfg.load(objectMapper, "{\"value\": \"aaa\", \"other\": \"value\"}");

        Assertions.assertTrue(cfg.hasValue("value", "aaa"));
        Assertions.assertTrue(cfg.hasValue("other", "value"));
        Assertions.assertFalse(cfg.hasValue("value", "bbb"));
        Assertions.assertFalse(cfg.hasValue("aaa", "value"));

        cfg.removeProperty("aaa");

        Assertions.assertTrue(cfg.hasValue("value", "aaa"));
        Assertions.assertTrue(cfg.hasValue("other", "value"));
        Assertions.assertFalse(cfg.hasValue("value", "bbb"));
        Assertions.assertFalse(cfg.hasValue("aaa", "value"));

        cfg.removeProperty("value");

        Assertions.assertFalse(cfg.hasValue("value", "aaa"));
        Assertions.assertTrue(cfg.hasValue("other", "value"));

        String json = cfg.toJson(objectMapper);
        Assertions.assertEquals("{\"other\":\"value\"}", json);
    }


}
