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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

/**
 * An inherited organization reporter receives a domain's audits and can serve its reads, so it has to
 * appear in that domain's list — otherwise the reporter the audit screen is reading from is not on the
 * screen, and {@code readSource} has nothing to be marked on.
 *
 * @author GraviteeSource Team
 */
class ReportersResourceTest extends JerseySpringTest {

    private static final String DOMAIN_ID = "domain-id";
    private static final String ORGANIZATION_ID = "DEFAULT";

    @BeforeEach
    void stubDomain() {
        Domain domain = new Domain();
        domain.setId(DOMAIN_ID);
        doReturn(Maybe.just(domain)).when(domainService).findById(DOMAIN_ID);
        doReturn(Optional.empty()).when(AuditReporterManager).getReadSourceId(Reference.domain(DOMAIN_ID));
    }

    @Test
    void listsInheritedOrganizationReportersAlongsideTheDomainsOwn() {
        stubReporters(List.of(reporter("database", Reference.domain(DOMAIN_ID))),
                List.of(inherited("org-elasticsearch")));

        assertThat(ids(listReporters())).containsExactly("database", "org-elasticsearch");
    }

    @Test
    void leavesOutOrganizationReportersThatAreNotInherited() {
        stubReporters(List.of(reporter("database", Reference.domain(DOMAIN_ID))),
                List.of(reporter("org-only", Reference.organization(ORGANIZATION_ID))));

        assertThat(ids(listReporters())).containsExactly("database");
    }

    @Test
    void withholdsTheConfigurationOfAnInheritedReporter() {
        // it is administered from the organization, and its configuration can hold credentials a
        // domain-level permission does not entitle its holder to
        Reporter inherited = inherited("org-elasticsearch");
        inherited.setConfiguration("{\"password\":\"s3cret\"}");
        stubReporters(List.of(), List.of(inherited));

        JsonNode listed = listReporters().get(0);
        assertThat(listed.path("id").asText()).isEqualTo("org-elasticsearch");
        assertThat(listed.hasNonNull("configuration")).isFalse();
    }

    @Test
    void marksAnInheritedReporterAsTheReadSourceWhenItIsServingReads() {
        stubReporters(List.of(reporter("database", Reference.domain(DOMAIN_ID))),
                List.of(inherited("org-elasticsearch")));
        doReturn(Optional.of("org-elasticsearch")).when(AuditReporterManager).getReadSourceId(Reference.domain(DOMAIN_ID));

        JsonNode inherited = listReporters().get(1);
        assertThat(inherited.path("id").asText()).isEqualTo("org-elasticsearch");
        assertThat(inherited.path("readSource").asBoolean()).isTrue();
    }

    private void stubReporters(List<Reporter> domainReporters, List<Reporter> organizationReporters) {
        doReturn(Flowable.fromIterable(domainReporters)).when(reporterService).findByReference(Reference.domain(DOMAIN_ID));
        doReturn(Flowable.fromIterable(organizationReporters)).when(reporterService).findByReference(Reference.organization(ORGANIZATION_ID));
    }

    private List<JsonNode> listReporters() {
        Response response = target("domains").path(DOMAIN_ID).path("reporters").request().get();
        assertThat(response.getStatus()).isEqualTo(HttpStatusCode.OK_200);
        try {
            JsonNode body = objectMapper.readTree(response.readEntity(String.class));
            return StreamSupport.stream(body.spliterator(), false).toList();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to read the response body", e);
        }
    }

    private static List<String> ids(List<JsonNode> reporters) {
        return reporters.stream().map(reporter -> reporter.path("id").asText()).toList();
    }

    private static Reporter reporter(String id, Reference reference) {
        Reporter reporter = new Reporter();
        reporter.setId(id);
        reporter.setName(id);
        reporter.setType(id);
        reporter.setReference(reference);
        reporter.setEnabled(true);
        return reporter;
    }

    private static Reporter inherited(String id) {
        Reporter reporter = reporter(id, Reference.organization(ORGANIZATION_ID));
        reporter.setInherited(true);
        return reporter;
    }
}
