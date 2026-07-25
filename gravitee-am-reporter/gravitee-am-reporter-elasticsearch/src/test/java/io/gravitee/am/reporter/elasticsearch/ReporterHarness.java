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

import io.gravitee.am.reporter.elasticsearch.audit.ElasticsearchAuditReporter;
import io.vertx.core.Vertx;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;

/**
 * Starts a real {@link ElasticsearchAuditReporter} against the shared test container, wired the same
 * way the plugin container wires it in production: the reporter bean pulls in its own Elasticsearch
 * client from the reporter configuration.
 *
 * @author GraviteeSource Team
 */
public final class ReporterHarness implements AutoCloseable {

    private final AnnotationConfigApplicationContext context;
    private final ElasticsearchAuditReporter reporter;
    private final ElasticsearchReporterConfiguration configuration;

    private ReporterHarness(AnnotationConfigApplicationContext context,
                            ElasticsearchAuditReporter reporter,
                            ElasticsearchReporterConfiguration configuration) {
        this.context = context;
        this.reporter = reporter;
        this.configuration = configuration;
    }

    /**
     * Configuration pointing at the shared container, with a small batch and short flush so tests do
     * not wait on the production five second window.
     */
    public static ElasticsearchReporterConfiguration configurationFor(String index) {
        return configurationFor(index, ElasticsearchTestContainer.endpoint());
    }

    public static ElasticsearchReporterConfiguration configurationFor(String index, String endpoint) {
        ElasticsearchReporterConfiguration configuration = new ElasticsearchReporterConfiguration();
        configuration.setEndpoints(java.util.List.of(endpoint));
        configuration.setIndex(index);
        configuration.setBulkActions(10);
        configuration.setFlushInterval(1L);
        return configuration;
    }

    public static ReporterHarness start(ElasticsearchReporterConfiguration configuration) throws Exception {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.registerBean(PropertySourcesPlaceholderConfigurer.class);
        context.registerBean(ElasticsearchReporterConfiguration.class, () -> configuration);
        context.registerBean(Vertx.class, () -> Vertx.vertx());
        context.register(ElasticsearchAuditReporter.class);
        try {
            context.refresh();
        } catch (RuntimeException startupFailure) {
            context.close();
            throw startupFailure;
        }

        ElasticsearchAuditReporter reporter = context.getBean(ElasticsearchAuditReporter.class);
        reporter.start();
        return new ReporterHarness(context, reporter, configuration);
    }

    public ElasticsearchAuditReporter reporter() {
        return reporter;
    }

    public ElasticsearchReporterConfiguration configuration() {
        return configuration;
    }

    /** Stops the reporter through its lifecycle, as the plugin container would. */
    public void stopReporter() throws Exception {
        reporter.stop();
    }

    @Override
    public void close() {
        try {
            reporter.stop();
        } catch (Exception e) {
            // teardown only: a reporter that already failed to start has nothing to drain
        }
        context.close();
    }
}
