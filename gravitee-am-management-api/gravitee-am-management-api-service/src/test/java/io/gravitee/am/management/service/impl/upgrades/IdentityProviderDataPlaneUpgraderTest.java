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

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.gravitee.am.dataplane.api.DataPlaneDescription;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.service.IdentityProviderService;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IdentityProviderDataPlaneUpgraderTest {

    @Mock
    private IdentityProviderService identityProviderService;

    @InjectMocks
    private IdentityProviderDataPlaneUpgrader upgrader;

    @Test
    void shouldAssignDefaultDataPlaneId() {
        IdentityProvider identityProvider = new IdentityProvider();
        identityProvider.setId("identityProvider-id");
        identityProvider.setName("identityProvider");
        when(identityProviderService.findAll(ReferenceType.DOMAIN)).thenReturn(Flowable.just(identityProvider));
        ArgumentCaptor<IdentityProvider> argumentCaptor = ArgumentCaptor.forClass(IdentityProvider.class);
        when(identityProviderService.assignDataPlane(argumentCaptor.capture(), eq(DataPlaneDescription.DEFAULT_DATA_PLANE_ID))).thenAnswer(i -> Single.just(i.getArgument(1)));
        assertTrue(upgrader.upgrade());

        assertNull(argumentCaptor.getValue().getDataPlaneId());
    }

    @Test
    void shouldNotChangeDataPlaneId() {
        IdentityProvider identityProvider = new IdentityProvider();
        identityProvider.setId("identityProvider-id");
        identityProvider.setName("identityProvider");
        identityProvider.setDataPlaneId(DataPlaneDescription.DEFAULT_DATA_PLANE_ID);
        when(identityProviderService.findAll(ReferenceType.DOMAIN)).thenReturn(Flowable.just(identityProvider));

        assertTrue(upgrader.upgrade());

        verify(identityProviderService, never()).assignDataPlane(any(), any());
    }

}
