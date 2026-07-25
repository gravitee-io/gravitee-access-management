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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.reactivex.rxjava3.core.Completable;
import io.vertx.rxjava3.core.Vertx;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * A node that cannot undeploy its reporter verticle used to say nothing at all, because the terminal
 * error went to a bare {@code subscribe()}. The failure is a diagnosis someone will eventually go
 * looking for, so it has to reach the log.
 *
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
class ReporterTeardownTest {

    private static final String DEPLOYMENT_ID = "deployment-1";

    @Mock
    private Vertx vertx;

    private ListAppender<ILoggingEvent> logs;

    @BeforeEach
    void captureLogs() {
        logs = new ListAppender<>();
        logs.setContext((LoggerContext) LoggerFactory.getILoggerFactory());
        logs.start();
        rootLogger().addAppender(logs);
    }

    @AfterEach
    void releaseLogs() {
        rootLogger().detachAppender(logs);
        logs.stop();
    }

    @Test
    void logsAFailedUndeployWithItsCause() {
        when(vertx.rxUndeploy(DEPLOYMENT_ID))
                .thenReturn(Completable.error(new IllegalStateException("verticle is wedged")));

        ReporterTeardown.undeployAndStopReporters(vertx, DEPLOYMENT_ID, () -> { });

        assertThat(loggedFailures())
                .anySatisfy(logged -> assertThat(logged)
                        .contains(DEPLOYMENT_ID)
                        .contains("verticle is wedged"));
    }

    @Test
    void stopsTheReportersEvenWhenTheUndeployFails() {
        when(vertx.rxUndeploy(DEPLOYMENT_ID))
                .thenReturn(Completable.error(new IllegalStateException("verticle is wedged")));
        AtomicBoolean stopped = new AtomicBoolean();

        ReporterTeardown.undeployAndStopReporters(vertx, DEPLOYMENT_ID, () -> stopped.set(true));

        assertThat(stopped)
                .describedAs("a failed undeploy must not strand buffered audits in reporters that were never stopped")
                .isTrue();
    }

    @Test
    void returnsOnlyOnceTheReportersHaveStopped() {
        when(vertx.rxUndeploy(DEPLOYMENT_ID)).thenReturn(Completable.complete());
        AtomicBoolean stopped = new AtomicBoolean();

        ReporterTeardown.undeployAndStopReporters(vertx, DEPLOYMENT_ID, () -> {
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            stopped.set(true);
        });

        // the whole point of awaiting: a reporter's stop() is where its buffer is flushed, so a caller
        // that returns first lets the node exit mid-flush
        assertThat(stopped).isTrue();
    }

    /** Full text including causes, since the actionable detail lives on the wrapped exception. */
    private List<String> loggedFailures() {
        return logs.list.stream()
                .filter(event -> event.getLevel().isGreaterOrEqual(Level.ERROR))
                .map(event -> {
                    StringWriter text = new StringWriter();
                    text.append(event.getFormattedMessage());
                    if (event.getThrowableProxy() instanceof ch.qos.logback.classic.spi.ThrowableProxy proxy) {
                        proxy.getThrowable().printStackTrace(new PrintWriter(text));
                    }
                    return text.toString();
                })
                .toList();
    }

    private static ch.qos.logback.classic.Logger rootLogger() {
        return (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
    }
}
