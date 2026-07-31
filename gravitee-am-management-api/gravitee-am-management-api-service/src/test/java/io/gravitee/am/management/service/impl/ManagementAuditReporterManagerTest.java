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
package io.gravitee.am.management.service.impl;

import io.gravitee.am.model.Organization;
import io.gravitee.am.service.OrganizationService;
import io.reactivex.rxjava3.core.Flowable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
public class ManagementAuditReporterManagerTest {

    @Mock
    private OrganizationService organizationService;

    @InjectMocks
    private ManagementAuditReporterManager cut;

    private static Organization organization(String id) {
        Organization organization = new Organization();
        organization.setId(id);
        return organization;
    }

    @Test
    public void shouldReportOrganizationsMissingAReporter() {
        when(organizationService.findAll()).thenReturn(Flowable.just(organization("DEFAULT"), organization("cockpit-org")));

        assertThat(cut.organizationsWithoutReporter(Set.of("DEFAULT"))).containsExactly("cockpit-org");
    }

    @Test
    public void shouldReportNothingWhenEveryOrganizationHasAReporter() {
        when(organizationService.findAll()).thenReturn(Flowable.just(organization("DEFAULT"), organization("cockpit-org")));

        assertThat(cut.organizationsWithoutReporter(Set.of("DEFAULT", "cockpit-org"))).isEmpty();
    }

    @Test
    public void shouldNotFailStartupWhenOrganizationLookupFails() {
        when(organizationService.findAll()).thenReturn(Flowable.error(new IllegalStateException("repository down")));

        assertThat(cut.organizationsWithoutReporter(Set.of())).isEmpty();
    }
}
