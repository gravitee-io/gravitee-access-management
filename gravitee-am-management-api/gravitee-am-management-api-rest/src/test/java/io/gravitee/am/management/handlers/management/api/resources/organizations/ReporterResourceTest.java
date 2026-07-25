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
package io.gravitee.am.management.handlers.management.api.resources.organizations;

import com.fasterxml.jackson.databind.JsonNode;
import io.gravitee.am.management.handlers.management.api.JerseySpringTest;
import io.gravitee.am.model.Organization;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.Reporter;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

/**
 * The organization-level single-reporter GET has the same obligation as the domain-level one: it must
 * agree with the list endpoint about which reporter is serving audit reads.
 *
 * @author GraviteeSource Team
 */
class ReporterResourceTest extends JerseySpringTest {

    private static final String ORGANIZATION_ID = "org-id";

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

    private void stub(Reporter reporter, String readSourceId) {
        Organization organization = new Organization();
        organization.setId(ORGANIZATION_ID);

        doReturn(Single.just(organization)).when(organizationService).findById(ORGANIZATION_ID);
        doReturn(Maybe.just(reporter)).when(reporterService).findById(reporter.getId());
        doReturn(Flowable.just(reporter)).when(reporterService).findByReference(Reference.organization(ORGANIZATION_ID));
        doReturn(Optional.ofNullable(readSourceId)).when(AuditReporterManager).getReadSourceId(Reference.organization(ORGANIZATION_ID));
    }

    private JsonNode getReporter(String reporterId) {
        return body(target("organizations").path(ORGANIZATION_ID).path("reporters").path(reporterId).request().get());
    }

    private JsonNode listReporters() {
        return body(target("organizations").path(ORGANIZATION_ID).path("reporters").request().get());
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
        reporter.setReference(Reference.organization(ORGANIZATION_ID));
        reporter.setEnabled(true);
        return reporter;
    }
}
