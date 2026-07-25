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
package io.gravitee.am.management.handlers.management.api.resources.organizations.environments.domains;

import com.fasterxml.jackson.databind.JsonNode;
import io.gravitee.am.management.handlers.management.api.JerseySpringTest;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.Reporter;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

/**
 * {@code readSource} is resolved per request rather than persisted, so the single-reporter GET has to
 * do the same work as the list endpoint. Fetching one reporter used to report it as not serving reads
 * while the list said it was, which is worse than incomplete: a caller scripting a cutover reads the
 * field as a confident no.
 *
 * @author GraviteeSource Team
 */
class ReporterResourceTest extends JerseySpringTest {

    private static final String DOMAIN_ID = "domain-id";

    @Test
    void reportsTheSameReadSourceAsTheListEndpoint() {
        Reporter serving = reporter("elasticsearch");
        stub(serving, "elasticsearch");

        assertThat(readSourceOf(getReporter(serving.getId()))).isTrue();
        assertThat(readSourceOf(listReporters().get(0))).isTrue();
    }

    @Test
    void reportsAReporterThatDoesNotServeReadsAsSuch() {
        Reporter notServing = reporter("kafka");
        stub(notServing, "elasticsearch");

        assertThat(readSourceOf(getReporter(notServing.getId()))).isFalse();
        assertThat(readSourceOf(listReporters().get(0))).isFalse();
    }

    @Test
    void reportsNoReadSourceWhenNothingHasResolvedYet() {
        Reporter reporter = reporter("elasticsearch");
        stub(reporter, null);

        assertThat(readSourceOf(getReporter(reporter.getId()))).isFalse();
    }

    private void stub(Reporter reporter, String readSourceId) {
        Domain domain = new Domain();
        domain.setId(DOMAIN_ID);

        doReturn(Maybe.just(domain)).when(domainService).findById(DOMAIN_ID);
        doReturn(Maybe.just(reporter)).when(reporterService).findById(reporter.getId());
        doReturn(Flowable.just(reporter)).when(reporterService).findByReference(Reference.domain(DOMAIN_ID));
        doReturn(Optional.ofNullable(readSourceId)).when(AuditReporterManager).getReadSourceId(Reference.domain(DOMAIN_ID));
    }

    private JsonNode getReporter(String reporterId) {
        return body(target("domains").path(DOMAIN_ID).path("reporters").path(reporterId).request().get());
    }

    private JsonNode listReporters() {
        return body(target("domains").path(DOMAIN_ID).path("reporters").request().get());
    }

    private static boolean readSourceOf(JsonNode reporter) {
        return reporter.path("readSource").asBoolean();
    }

    private JsonNode body(Response response) {
        assertThat(response.getStatus()).isEqualTo(HttpStatusCode.OK_200);
        try {
            return objectMapper.readTree(response.readEntity(String.class));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to read the response body", e);
        }
    }

    private static Reporter reporter(String id) {
        Reporter reporter = new Reporter();
        reporter.setId(id);
        reporter.setName(id);
        reporter.setType(id);
        reporter.setReference(Reference.domain(DOMAIN_ID));
        reporter.setEnabled(true);
        return reporter;
    }
}
