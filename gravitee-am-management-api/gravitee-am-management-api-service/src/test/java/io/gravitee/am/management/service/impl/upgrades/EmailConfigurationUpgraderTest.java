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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.model.resource.ServiceResource;
import io.gravitee.am.repository.management.api.ServiceResourceRepository;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailConfigurationUpgraderTest {

    private static final String SMTP_RESOURCE_NAME = "smtp-am-resource";

    @Mock
    private ServiceResourceRepository serviceResourceRepository;

    @InjectMocks
    private EmailConfigurationUpgrader upgrader;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void should_apply_default_authentication_type_when_missing() throws Exception {
        ServiceResource resourceNeedingUpgrade = serviceResource("resource-1", "{\"authentication\":true}");

        when(serviceResourceRepository.findByType(eq(SMTP_RESOURCE_NAME)))
                .thenReturn(Flowable.just(resourceNeedingUpgrade));
        when(serviceResourceRepository.update(any())).thenAnswer(invocation -> Single.just(invocation.getArgument(0)));

        assertThat(upgrader.upgrade()).isTrue();

        ArgumentCaptor<ServiceResource> captor = ArgumentCaptor.forClass(ServiceResource.class);
        verify(serviceResourceRepository).update(captor.capture());

        Map<String, Object> config = mapper.readValue(captor.getValue().getConfiguration(), new TypeReference<>() {});
        assertThat(config).containsEntry("authenticationType", "basic");
    }

    @Test
    void should_skip_resources_without_required_changes() {
        ServiceResource blankConfig = serviceResource("resource-blank", "   ");
        ServiceResource alreadyConfigured =
                serviceResource("resource-typed", "{\"authentication\":true,\"authenticationType\":\"oauth2\"}");

        when(serviceResourceRepository.findByType(eq(SMTP_RESOURCE_NAME)))
                .thenReturn(Flowable.just(blankConfig, alreadyConfigured));

        assertThat(upgrader.upgrade()).isTrue();

        verify(serviceResourceRepository, times(1))
                .findByType(SMTP_RESOURCE_NAME);
        verify(serviceResourceRepository, never()).update(any());
    }

    @Test
    void should_continue_when_update_fails() {
        ServiceResource resourceNeedingUpgrade = serviceResource("resource-error", "{\"authentication\":true}");

        when(serviceResourceRepository.findByType(eq(SMTP_RESOURCE_NAME)))
                .thenReturn(Flowable.just(resourceNeedingUpgrade));
        when(serviceResourceRepository.update(any())).thenReturn(Single.error(new RuntimeException("boom")));

        assertThat(upgrader.upgrade()).isTrue();

        verify(serviceResourceRepository).update(any());
    }

    private static ServiceResource serviceResource(String id, String configuration) {
        ServiceResource resource = new ServiceResource();
        resource.setId(id);
        resource.setConfiguration(configuration);
        return resource;
    }
}
