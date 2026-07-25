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
package io.gravitee.am.reporter.elasticsearch;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Endpoints are logged whenever the cluster is unreachable, which is repeatedly during an outage.
 * Nothing stops an operator putting credentials in the URL, so they must not survive into the log.
 *
 * @author GraviteeSource Team
 */
class LoggableEndpointsTest {

    @Test
    void leavesAnOrdinaryEndpointAlone() {
        assertThat(LoggableEndpoints.redact(List.of("http://localhost:9200")))
                .containsExactly("http://localhost:9200");
    }

    @Test
    void removesUsernameAndPasswordFromTheUrl() {
        assertThat(LoggableEndpoints.redact(List.of("https://elastic:s3cr3t@es.example.com:9243")))
                .containsExactly("https://***@es.example.com:9243");
    }

    @Test
    void removesAUsernameEvenWithoutAPassword() {
        assertThat(LoggableEndpoints.redact(List.of("https://elastic@es.example.com")))
                .containsExactly("https://***@es.example.com");
    }

    @Test
    void redactsEveryEndpointInTheList() {
        assertThat(LoggableEndpoints.redact(List.of(
                "http://one:pw@a.example.com:9200",
                "http://b.example.com:9200",
                "https://two:pw@c.example.com:9200")))
                .containsExactly(
                        "http://***@a.example.com:9200",
                        "http://b.example.com:9200",
                        "https://***@c.example.com:9200");
    }

    @Test
    void keepsAPathAndQueryIntact() {
        assertThat(LoggableEndpoints.redact(List.of("https://user:pw@es.example.com:9243/prefix")))
                .containsExactly("https://***@es.example.com:9243/prefix");
    }

    @Test
    void doesNotMistakeAPathSegmentForCredentials() {
        assertThat(LoggableEndpoints.redact(List.of("http://es.example.com:9200/some@path")))
                .containsExactly("http://es.example.com:9200/some@path");
    }

    @Test
    void survivesNullAndMalformedEntries() {
        assertThat(LoggableEndpoints.redact(Arrays.asList(null, "not a url", "")))
                .containsExactly(null, "not a url", "");
    }

    @Test
    void survivesANullList() {
        assertThat(LoggableEndpoints.redact(null)).isEmpty();
    }
}
