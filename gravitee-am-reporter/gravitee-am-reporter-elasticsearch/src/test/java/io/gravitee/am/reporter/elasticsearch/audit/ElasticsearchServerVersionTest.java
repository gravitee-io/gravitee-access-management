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
package io.gravitee.am.reporter.elasticsearch.audit;

import io.gravitee.am.reporter.elasticsearch.audit.ElasticsearchServerVersion.Distribution;
import io.gravitee.elasticsearch.version.ElasticsearchInfo;
import io.gravitee.elasticsearch.version.Version;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Dispatch coverage for every claimed variant, from a stubbed cluster info response. Only three of
 * the six have been exercised against a real server; the rest are covered here at dispatch level.
 *
 * @author GraviteeSource Team
 */
class ElasticsearchServerVersionTest {

    @ParameterizedTest
    @CsvSource({
            "7.17.28,      , ELASTICSEARCH, 7",
            "8.19.19,      , ELASTICSEARCH, 8",
            "9.3.0,        , ELASTICSEARCH, 9",
            "1.3.20, opensearch, OPENSEARCH,   1",
            "2.19.6, opensearch, OPENSEARCH,   2",
            "3.0.0,  opensearch, OPENSEARCH,   3"
    })
    void detectsEverySupportedVariant(String number, String distribution, Distribution expected, int expectedMajor) {
        ElasticsearchServerVersion version = ElasticsearchServerVersion.detect(info(number, distribution));

        assertThat(version.distribution()).isEqualTo(expected);
        assertThat(version.major()).isEqualTo(expectedMajor);
        assertThat(version.number()).isEqualTo(number);
        assertThat(version.isSupported()).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
            "7.17.28,      ",
            "8.19.19,      ",
            "9.3.0,        ",
            "1.3.20, opensearch",
            "2.19.6, opensearch",
            "3.0.0,  opensearch"
    })
    void everySupportedVariantTakesTheSameTemplate(String number, String distribution) {
        ElasticsearchServerVersion version = ElasticsearchServerVersion.detect(info(number, distribution));

        assertThat(AuditIndexTemplate.bodyFor(version, "gravitee-audit"))
                .isEqualTo(AuditIndexTemplate.body("gravitee-audit"));
    }

    @Test
    void rejectsAnElasticsearchOlderThanTheSupportedRange() {
        ElasticsearchServerVersion version = ElasticsearchServerVersion.detect(info("6.8.23", null));

        assertThat(version.isSupported()).isFalse();
        assertThat(version.unsupportedMessage())
                .contains("Elasticsearch 6.8.23")
                .contains("compatibility matrix");
    }

    @Test
    void failsWhenTheServerReportsNoVersion() {
        assertThatThrownBy(() -> ElasticsearchServerVersion.detect(new ElasticsearchInfo()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unable to determine the Elasticsearch server version");
    }

    @Test
    void failsWhenThereIsNoClusterInfoAtAll() {
        assertThatThrownBy(() -> ElasticsearchServerVersion.detect(null))
                .isInstanceOf(IllegalStateException.class);
    }

    private static ElasticsearchInfo info(String number, String distribution) {
        Version version = new Version();
        version.setNumber(number);
        version.setDistribution(distribution);
        ElasticsearchInfo info = new ElasticsearchInfo();
        info.setVersion(version);
        return info;
    }
}
