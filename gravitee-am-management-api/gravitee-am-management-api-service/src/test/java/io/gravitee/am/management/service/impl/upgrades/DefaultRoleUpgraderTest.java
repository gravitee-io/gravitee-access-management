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

import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.service.RoleService;
import io.reactivex.rxjava3.core.Completable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.Assert.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
public class DefaultRoleUpgraderTest {

    private RoleService roleService;

    private DefaultRoleUpgrader cut;

    @BeforeEach
    public void before() {
        roleService = Mockito.mock(RoleService.class);
        cut = new DefaultRoleUpgrader(roleService);
    }

    @Test
    void shouldCreateSystemRoles() {
        when(roleService.createOrUpdateSystemRoles()).thenReturn(Completable.complete());
        assertTrue(cut.upgrade());
    }

    @Test
    void shouldCreateSystemRoles_technicalError() {
        when(roleService.createOrUpdateSystemRoles()).thenReturn(Completable.error(TechnicalException::new));
        assertFalse(cut.upgrade());
    }
}
