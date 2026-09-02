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
package io.gravitee.am.management.service.telemetry;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.reactivex.rxjava3.core.Completable;
import io.vertx.rxjava3.ext.web.client.WebClient;
import java.util.concurrent.TimeUnit;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;

/**
 * Sends one report to the collector.
 * <p>
 * The collector answers 202 with no body, so nothing is parsed back. A failure completes with an
 * error that the callers log at DEBUG and then drop: telemetry must never retry, never back up and
 * never fill an air-gapped customer's log.
 *
 * @author GraviteeSource Team
 */
@CustomLog
@RequiredArgsConstructor
public class TelemetryPublisher {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final TelemetrySettings settings;

    public Completable send(String url, Object payload) {
        final String body;
        try {
            body = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            return Completable.error(e);
        }
        return webClient
            .postAbs(url)
            .putHeader("Content-Type", "application/json")
            .timeout(settings.timeout())
            .rxSendBuffer(io.vertx.core.buffer.Buffer.buffer(body))
            .flatMapCompletable(response -> {
                if (response.statusCode() == 202) {
                    log.debug("Telemetry report accepted by {}", url);
                    return Completable.complete();
                }
                return Completable.error(new TelemetryRejectedException(url, response.statusCode()));
            })
            .timeout(settings.timeout(), TimeUnit.MILLISECONDS);
    }

    /**
     * A response other than 202. The status code alone identifies the reason, because the collector
     * returns no body.
     */
    public static class TelemetryRejectedException extends RuntimeException {

        public TelemetryRejectedException(String url, int statusCode) {
            super("The collector answered " + statusCode + " for " + url);
        }
    }
}
