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
package io.gravitee.am.gateway.handler.common.service.polling;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

class PollingWindowTest {

    @Test
    void shouldOpenWindowWithRetentionAddedToTtl() {
        Instant before = Instant.now().truncatedTo(ChronoUnit.MILLIS);

        PollingWindow window = PollingWindow.open(60, 900);

        assertThat(window.createdAt().toInstant()).isAfterOrEqualTo(before);
        assertThat(window.expireAt().toInstant()).isEqualTo(window.createdAt().toInstant().plusSeconds(60 + 900));
    }
}
