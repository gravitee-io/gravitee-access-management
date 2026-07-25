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
package io.gravitee.am.service.reporter;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.vertx.core.Context;
import io.vertx.rxjava3.core.Vertx;
import lombok.CustomLog;

import java.util.concurrent.TimeUnit;

/**
 * Shutdown for the audit reporter verticle, shared by the gateway and management reporter managers so
 * the two cannot drift apart.
 *
 * @author GraviteeSource Team
 */
@CustomLog
public final class ReporterTeardown {

    /**
     * Upper bound on the whole teardown. Each reporter already bounds its own flush — the
     * Elasticsearch one waits {@code shutdownFlushTimeout} seconds by default — so this is the
     * backstop for the sum of them plus the undeploy, not the first line of defence.
     */
    private static final long TIMEOUT_SECONDS = 30;

    private ReporterTeardown() {
    }

    /**
     * Undeploys the reporter verticle, then stops every reporter, and waits for both.
     * <p>
     * The wait is deliberate. Stopping a reporter is where its buffered records are flushed, so
     * returning while that is still pending lets a node exit mid-flush and lose up to a batch of
     * audits per node on every rolling restart. It is bounded so an unresponsive backend cannot hang
     * the restart instead, and skipped entirely on an event loop thread, because blocking the loop
     * the flush itself needs would deadlock rather than protect anything.
     *
     * @param stopReporters stops each reporter; expected to absorb and log a reporter that fails
     */
    public static void undeployAndStopReporters(Vertx vertx, String deploymentId, Runnable stopReporters) {
        Completable teardown = vertx.rxUndeploy(deploymentId)
                .doOnError(error -> log.error("Unable to undeploy the audit reporter verticle {}. Reporters will " +
                        "still be stopped, but the verticle may have been left running.", deploymentId, error))
                .onErrorComplete()
                .andThen(Completable.fromAction(stopReporters::run))
                // so the timeout below actually bounds the teardown: run on the caller's thread and a
                // reporter that never returns would sit inside blockingAwait rather than be timed out
                .subscribeOn(Schedulers.io());

        if (Context.isOnEventLoopThread()) {
            log.warn("Stopping audit reporters from an event loop thread, so their flush cannot be awaited and will " +
                    "finish in the background.");
            teardown.subscribe();
            return;
        }

        if (!teardown.blockingAwait(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            log.error("Audit reporters did not stop within {}s. Anything still buffered will be lost when the node exits.",
                    TIMEOUT_SECONDS);
        }
    }
}
