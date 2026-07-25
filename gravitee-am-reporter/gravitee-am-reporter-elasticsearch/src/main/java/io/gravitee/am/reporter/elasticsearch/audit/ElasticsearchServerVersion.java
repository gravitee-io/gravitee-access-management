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

import io.gravitee.elasticsearch.version.ElasticsearchInfo;
import io.gravitee.elasticsearch.version.Version;

/**
 * Which server the reporter is talking to, and whether it is one we support.
 * <p>
 * The reporter has a single document type, a single composable template and no data streams or
 * ingest pipelines, so unlike APIM it does not need per-version bean factories and template
 * directories. What it does need is a real detection point, so a per-version branch can be added
 * later without restructuring — see {@link AuditIndexTemplate#bodyFor}.
 *
 * @author GraviteeSource Team
 */
public record ElasticsearchServerVersion(Distribution distribution, int major, String number) {

    public enum Distribution {
        ELASTICSEARCH("Elasticsearch", 7),
        OPENSEARCH("OpenSearch", 1);

        private final String label;
        private final int oldestSupportedMajor;

        Distribution(String label, int oldestSupportedMajor) {
            this.label = label;
            this.oldestSupportedMajor = oldestSupportedMajor;
        }

        public String label() {
            return label;
        }

        public int oldestSupportedMajor() {
            return oldestSupportedMajor;
        }
    }

    /**
     * Reads the distribution and major from a cluster info response.
     *
     * @throws IllegalStateException when the response carries no usable version, which means we do
     *                               not know what we are talking to and must not proceed
     */
    public static ElasticsearchServerVersion detect(ElasticsearchInfo info) {
        Version version = info == null ? null : info.getVersion();
        if (version == null || version.getNumber() == null || version.getNumber().isBlank()) {
            throw new IllegalStateException(
                    "Unable to determine the Elasticsearch server version: the cluster info response carried no version. " +
                            "Check that the configured endpoint is an Elasticsearch or OpenSearch cluster.");
        }
        Distribution distribution = version.isOpenSearch() ? Distribution.OPENSEARCH : Distribution.ELASTICSEARCH;
        return new ElasticsearchServerVersion(distribution, version.getMajorVersion(), version.getNumber());
    }

    public boolean isSupported() {
        return major >= distribution.oldestSupportedMajor();
    }

    /**
     * Deliberately points at the platform's published compatibility matrix rather than restating a
     * list here, so the two cannot drift apart.
     */
    public String unsupportedMessage() {
        return "%s %s is older than the oldest supported major (%d). Audits would be written to a server this reporter has never been validated against. See the Gravitee platform compatibility matrix for the supported Elasticsearch and OpenSearch versions."
                .formatted(distribution.label(), number, distribution.oldestSupportedMajor());
    }

    @Override
    public String toString() {
        return distribution.label() + " " + number;
    }
}
