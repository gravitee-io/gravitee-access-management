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

import io.gravitee.am.reporter.api.ReporterConfiguration;
import io.gravitee.secrets.api.annotation.Secret;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * @author GraviteeSource Team
 */
@Getter
@Setter
public class ElasticsearchReporterConfiguration implements ReporterConfiguration {

    private List<String> endpoints = new ArrayList<>();
    private String index = "gravitee-audit";
    private String username;
    @Secret
    private String password;
    private long requestTimeout = 30000L;

    private String sslKeystoreType;
    private String sslKeystorePath;
    @Secret
    private String sslKeystorePassword;
    private List<String> sslPemCerts = new ArrayList<>();
    private List<String> sslPemKeys = new ArrayList<>();

    private Integer bulkActions = 1000;
    private Long flushInterval = 5L;

    /**
     * Upper bound on the backlog of pending bulk batches held in memory while Elasticsearch is slow
     * or unreachable. Once reached the oldest pending batch is dropped, so the node stays healthy
     * instead of growing until it runs out of memory. The audit bound is this times {@code bulkActions}.
     */
    private Integer maxPendingBatches = 50;

    /** Upper bound on bulk requests in flight at once. */
    private Integer maxConcurrentRequests = 5;

    /** How many times a batch is retried before it is dropped and counted. */
    private Integer retryAttempts = 6;

    /** First retry delay, in seconds; doubles per attempt up to {@link #retryMaxInterval}. */
    private Long retryInitialInterval = 3L;

    /** Ceiling on the retry delay, in seconds. */
    private Long retryMaxInterval = 30L;

    /**
     * How long a stopping reporter waits for its buffered audits to be acknowledged, in seconds.
     * Bounded so a sick cluster cannot hang a rolling restart.
     */
    private Long shutdownFlushTimeout = 10L;
}
