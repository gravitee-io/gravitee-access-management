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

import org.testcontainers.elasticsearch.ElasticsearchContainer;

/**
 * A single Elasticsearch container shared by every test class in the module.
 * <p>
 * The image defaults to a minor the platform currently supports and can be overridden without
 * touching test code, e.g. {@code mvn test -Delasticsearch.image=docker.elastic.co/elasticsearch/elasticsearch:7.17.28}.
 *
 * @author GraviteeSource Team
 */
public final class ElasticsearchTestContainer {

    public static final String DEFAULT_IMAGE = "docker.elastic.co/elasticsearch/elasticsearch:8.19.19";

    private static ElasticsearchContainer container;

    private ElasticsearchTestContainer() {
    }

    public static synchronized ElasticsearchContainer getInstance() {
        if (container == null) {
            container = new ElasticsearchContainer(System.getProperty("elasticsearch.image", DEFAULT_IMAGE))
                    // the shared Gravitee client cannot verify server certificates, so tests run over plain HTTP
                    .withEnv("xpack.security.enabled", "false");
            container.start();
        }
        return container;
    }

    public static String endpoint() {
        return "http://" + getInstance().getHttpHostAddress();
    }
}
