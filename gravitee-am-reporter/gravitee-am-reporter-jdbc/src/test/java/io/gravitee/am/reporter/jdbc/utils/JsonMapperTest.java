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
package io.gravitee.am.reporter.jdbc.utils;

import io.gravitee.am.reporter.jdbc.JdbcReporterConfiguration;
import org.junit.Test;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.Assert.assertNotNull;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class JsonMapperTest {

    @Test
    public void testDeserializeConfig() throws Exception {
        JdbcReporterConfiguration config = new ObjectMapper().readValue("{\"host\":\"localhost\",\"port\":5432," +
                "\"database\":\"some\",\"driver\":\"postgresql\",\"username\":\"postgres\",\"password\":\"password\"," +
                "\"tableSuffix\":\"testdomain\",\"initialSize\":10,\"maxSize\":10,\"maxIdleTime\":180000,\"bulkActions\":1000,\"flushInterval\":5}",
                JdbcReporterConfiguration.class);

        assertNotNull(config);
    }
}
