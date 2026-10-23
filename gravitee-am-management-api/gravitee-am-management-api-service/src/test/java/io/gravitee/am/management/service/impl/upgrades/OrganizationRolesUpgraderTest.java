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
package io.gravitee.am.management.service.impl.upgrades;

import io.gravitee.am.model.Organization;
import io.gravitee.am.service.OrganizationService;
import io.gravitee.am.service.RoleService;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
class OrganizationRolesUpgraderTest {

    @Mock
    private OrganizationService organizationService;

    @Mock
    private RoleService roleService;

    private OrganizationRolesUpgrader cut;

    @BeforeEach
    void before() {
        cut = new OrganizationRolesUpgrader(organizationService, roleService);
    }

    @Test
    void shouldReseedTheDefaultRolesOfEveryOrganization() {
        when(organizationService.findAll()).thenReturn(Flowable.just(organization("org-1"), organization("org-2")));
        when(roleService.createDefaultRoles("org-1")).thenReturn(Completable.complete());
        when(roleService.createDefaultRoles("org-2")).thenReturn(Completable.complete());

        assertTrue(cut.upgrade());

        verify(roleService).createDefaultRoles("org-1");
        verify(roleService).createDefaultRoles("org-2");
    }

    @Test
    void shouldReportFailure() {
        when(organizationService.findAll()).thenReturn(Flowable.just(organization("org-1")));
        when(roleService.createDefaultRoles("org-1")).thenReturn(Completable.error(new RuntimeException("boom")));

        assertFalse(cut.upgrade());
    }

    private static Organization organization(String id) {
        Organization organization = new Organization();
        organization.setId(id);
        return organization;
    }
}
