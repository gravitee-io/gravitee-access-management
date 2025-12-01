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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.common.audit.EventType;
import io.gravitee.am.common.audit.Status;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.management.service.ResourcePluginService;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.resource.ServiceResource;
import io.gravitee.am.reporter.api.audit.model.Audit;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.ServiceResourceService;
import io.gravitee.am.service.exception.ServiceResourceNotFoundException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.management.service.exception.ResourcePluginNotFoundException;
import io.gravitee.am.service.model.NewServiceResource;
import io.gravitee.am.service.model.UpdateServiceResource;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServiceResourceServiceProxyImplTest {

    private static final String SCHEMA = """
            {
              "type": "object",
              "properties": {
                "secret": {
                  "type": "string",
                  "sensitive": true
                }
              }
            }
            """;

    @Mock
    private ResourcePluginService resourcePluginService;
    @Mock
    private ServiceResourceService serviceResourceService;
    @Mock
    private AuditService auditService;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private ServiceResourceServiceProxyImpl service;

    @Test
    void shouldCreateServiceResourceAndMaskConfiguration() {
        reset(auditService);
        var domain = new Domain();
        domain.setId("domain");
        var principal = mock(User.class);
        var newResource = new NewServiceResource();
        newResource.setName("resource");
        newResource.setType("smtp");
        newResource.setConfiguration("{\"secret\":\"value\"}");

        var created = buildResource("{\"secret\":\"value\"}");

        when(serviceResourceService.create(domain, newResource, principal)).thenReturn(Single.just(created));
        when(resourcePluginService.getSchema(anyString())).thenReturn(Maybe.just(SCHEMA));

        var result = service.create(domain, newResource, principal).blockingGet();

        assertThat(result.getId()).isEqualTo("res-id");
        assertThat(maskedSecret(result.getConfiguration())).isEqualTo("********");
    }

    @Test
    void shouldFindServiceResourceByIdAndMaskConfiguration() {
        var resource = buildResource("{\"secret\":\"value\"}");
        when(serviceResourceService.findById("res-id")).thenReturn(Maybe.just(resource));
        when(resourcePluginService.getSchema(anyString())).thenReturn(Maybe.just(SCHEMA));

        service.findById("res-id")
                .test()
                .assertValue(res -> maskedSecret(res.getConfiguration()).equals("********"));
    }

    @Test
    void shouldUpdateServiceResourceAndMaskConfiguration() {
        reset(auditService);
        var domain = new Domain();
        domain.setId("domain");
        var principal = mock(User.class);
        var update = new UpdateServiceResource();
        update.setName("resource");
        update.setType("smtp");
        update.setConfiguration("{\"secret\":\"new\"}");

        var existing = buildResource("{\"secret\":\"old\"}");
        var updated = buildResource("{\"secret\":\"new\"}");

        when(serviceResourceService.findById("res-id")).thenReturn(Maybe.just(existing));
        when(resourcePluginService.getSchema(anyString())).thenReturn(Maybe.just(SCHEMA));
        when(serviceResourceService.update(domain, "res-id", update, principal)).thenReturn(Single.just(updated));

        var result = service.update(domain, "res-id", update, principal).blockingGet();

        assertThat(maskedSecret(result.getConfiguration())).isEqualTo("********");
    }

    @Test
    void shouldFailUpdateWhenSchemaMissing() {
        var domain = new Domain();
        domain.setId("domain");
        var principal = mock(User.class);
        var update = new UpdateServiceResource();
        update.setName("resource");
        update.setType("smtp");
        update.setConfiguration("{\"secret\":\"new\"}");

        var existing = buildResource("{\"secret\":\"old\"}");

        when(serviceResourceService.findById("res-id")).thenReturn(Maybe.just(existing));
        when(resourcePluginService.getSchema(anyString())).thenReturn(Maybe.empty());

        service.update(domain, "res-id", update, principal)
                .test()
                .assertError(ResourcePluginNotFoundException.class);
    }

    @Test
    void shouldDeleteServiceResource() {
        var domain = new Domain();
        domain.setId("domain");
        var principal = mock(User.class);
        when(serviceResourceService.delete("domain", "res-id", principal)).thenReturn(io.reactivex.rxjava3.core.Completable.complete());

        service.delete("domain", "res-id", principal)
                .test()
                .assertComplete();

        verify(serviceResourceService).delete("domain", "res-id", principal);
    }

    @Test
    void shouldAuditWhenUpdateFailsBeforeFiltering() {
        var domain = new Domain();
        domain.setId("domain");
        var principal = mock(User.class);
        var update = new UpdateServiceResource();
        update.setName("resource");
        update.setType("smtp");
        update.setConfiguration("{}");

        var resource = new ServiceResource();
        resource.setId("res-id");
        resource.setName("resource");
        resource.setType("smtp");
        resource.setConfiguration("{}");
        resource.setReferenceId("domain");
        resource.setReferenceType(ReferenceType.DOMAIN);
        resource.setCreatedAt(new Date());
        resource.setUpdatedAt(new Date());

        when(serviceResourceService.findById("res-id")).thenReturn(Maybe.just(resource));
        when(resourcePluginService.getSchema(anyString())).thenReturn(Maybe.just("{}"));
        when(serviceResourceService.update(domain, "res-id", update, principal)).thenReturn(Single.error(new TechnicalManagementException("fail")));

        service.update(domain, "res-id", update, principal)
                .test()
                .assertError(TechnicalManagementException.class);

        var audit = captureAudit();
        assertThat(audit.getType()).isEqualTo(EventType.RESOURCE_UPDATED);
        assertThat(audit.getReferenceId()).isEqualTo("domain");
        assertThat(audit.getOutcome().getStatus()).isEqualTo(Status.FAILURE);
    }

    @Test
    void shouldAuditSuccessfulUpdate() {
        var domain = new Domain();
        domain.setId("domain");
        var principal = mock(User.class);
        var update = new UpdateServiceResource();
        update.setName("resource");
        update.setType("smtp");
        update.setConfiguration("{\"foo\":\"bar\"}");

        var resource = new ServiceResource();
        resource.setId("res-id");
        resource.setName("resource");
        resource.setType("smtp");
        resource.setConfiguration("{\"foo\":\"bar\"}");
        resource.setReferenceId("domain");
        resource.setReferenceType(ReferenceType.DOMAIN);
        resource.setCreatedAt(new Date());
        resource.setUpdatedAt(new Date());

        var updated = new ServiceResource(resource);
        updated.setConfiguration("{\"foo\":\"baz\"}");

        when(serviceResourceService.findById("res-id")).thenReturn(Maybe.just(resource));
        when(resourcePluginService.getSchema(anyString())).thenReturn(Maybe.just("{\"type\":\"object\"}"));
        when(serviceResourceService.update(domain, "res-id", update, principal)).thenReturn(Single.just(updated));

        service.update(domain, "res-id", update, principal)
                .test()
                .assertValue(res -> "res-id".equals(res.getId()) && "{\"foo\":\"baz\"}".equals(res.getConfiguration()))
                .assertComplete();

        var audit = captureAudit();
        assertThat(audit.getType()).isEqualTo(EventType.RESOURCE_UPDATED);
        assertThat(audit.getReferenceId()).isEqualTo("domain");
        assertThat(audit.getOutcome().getStatus()).isEqualTo(Status.SUCCESS);
    }

    @Test
    void shouldFailWhenResourceMissing() {
        var domain = new Domain();
        domain.setId("domain");
        var principal = mock(User.class);
        var update = new UpdateServiceResource();
        update.setName("resource");
        update.setType("smtp");
        update.setConfiguration("{}");

        when(serviceResourceService.findById("missing")).thenReturn(Maybe.empty());

        service.update(domain, "missing", update, principal)
                .test()
                .assertError(ServiceResourceNotFoundException.class);
    }

    @Test
    void shouldReturnStubWhenSchemaMissingOnRead() {
        var resource = new ServiceResource();
        resource.setId("res-id");
        resource.setType("smtp");
        resource.setConfiguration("{\"sensitive\":\"value\"}");
        resource.setReferenceType(ReferenceType.DOMAIN);
        resource.setReferenceId("domain");
        resource.setCreatedAt(new Date());
        resource.setUpdatedAt(new Date());

        when(serviceResourceService.findById("res-id")).thenReturn(Maybe.just(resource));
        when(resourcePluginService.getSchema(anyString())).thenReturn(Maybe.empty());

        service.findById("res-id")
                .test()
                .assertValue(res -> "{}".equals(res.getConfiguration()));
    }

    private Audit captureAudit() {
        ArgumentCaptor<AuditBuilder<?>> captor = ArgumentCaptor.forClass(AuditBuilder.class);
        verify(auditService).report(captor.capture());
        return captor.getValue().build(objectMapper);
    }

    private ServiceResource buildResource(String configuration) {
        var resource = new ServiceResource();
        resource.setId("res-id");
        resource.setName("resource");
        resource.setType("smtp");
        resource.setConfiguration(configuration);
        resource.setReferenceType(ReferenceType.DOMAIN);
        resource.setReferenceId("domain");
        resource.setCreatedAt(new Date());
        resource.setUpdatedAt(new Date());
        return resource;
    }

    private String maskedSecret(String configuration) {
        try {
            return objectMapper.readTree(configuration).get("secret").asText();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
