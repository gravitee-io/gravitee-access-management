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
import io.gravitee.am.management.service.IdentityProviderPluginService;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.reporter.api.audit.model.Audit;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.management.service.exception.IdentityProviderPluginSchemaNotFoundException;
import io.gravitee.am.service.exception.IdentityProviderNotFoundException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.model.NewIdentityProvider;
import io.gravitee.am.service.model.UpdateIdentityProvider;
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
class IdentityProviderServiceProxyImplTest {

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
    private IdentityProviderPluginService identityProviderPluginService;
    @Mock
    private io.gravitee.am.service.IdentityProviderService identityProviderService;
    @Mock
    private AuditService auditService;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private IdentityProviderServiceProxyImpl service;

    @Test
    void shouldCreateIdentityProviderAndMaskConfiguration() {
        reset(auditService);
        var principal = mock(User.class);
        var newIdp = new NewIdentityProvider();
        newIdp.setName("idp");
        newIdp.setType("mock-idp");
        newIdp.setConfiguration("{\"secret\":\"value\"}");

        var created = buildIdp("{\"secret\":\"value\"}");

        when(identityProviderService.create(ReferenceType.DOMAIN, "domain", newIdp, principal, false)).thenReturn(Single.just(created));
        when(identityProviderPluginService.getSchema(anyString())).thenReturn(Maybe.just(SCHEMA));

        var result = service.create(ReferenceType.DOMAIN, "domain", newIdp, principal, false).blockingGet();

        assertThat(result.getId()).isEqualTo("idp-id");
        assertThat(maskedSecret(result.getConfiguration())).isEqualTo("********");
    }

    @Test
    void shouldFindByIdAndMaskConfiguration() {
        var found = buildIdp("{\"secret\":\"value\"}");
        when(identityProviderService.findById(ReferenceType.DOMAIN, "domain", "idp-id")).thenReturn(Single.just(found));
        when(identityProviderPluginService.getSchema(anyString())).thenReturn(Maybe.just(SCHEMA));

        service.findById(ReferenceType.DOMAIN, "domain", "idp-id")
                .test()
                .assertValue(idp -> maskedSecret(idp.getConfiguration()).equals("********"));
    }

    @Test
    void shouldUpdateIdentityProviderAndMaskConfiguration() {
        reset(auditService);
        var principal = mock(User.class);
        var update = new UpdateIdentityProvider();
        update.setName("idp");
        update.setType("mock-idp");
        update.setConfiguration("{\"secret\":\"new\"}");

        var existing = buildIdp("{\"secret\":\"old\"}");
        var updated = buildIdp("{\"secret\":\"new\"}");

        when(identityProviderService.findById("idp-id")).thenReturn(Maybe.just(existing));
        when(identityProviderPluginService.getSchema(anyString())).thenReturn(Maybe.just(SCHEMA));
        when(identityProviderService.update(ReferenceType.DOMAIN, "domain", "idp-id", update, principal, false)).thenReturn(Single.just(updated));

        var result = service.update(ReferenceType.DOMAIN, "domain", "idp-id", update, principal, false).blockingGet();

        assertThat(maskedSecret(result.getConfiguration())).isEqualTo("********");
        verify(identityProviderService).update(ReferenceType.DOMAIN, "domain", "idp-id", update, principal, false);
    }

    @Test
    void shouldFailUpdateWhenSchemaMissing() {
        var principal = mock(User.class);
        var update = new UpdateIdentityProvider();
        update.setName("idp");
        update.setType("mock-idp");
        update.setConfiguration("{\"secret\":\"new\"}");

        var existing = buildIdp("{\"secret\":\"old\"}");

        when(identityProviderService.findById("idp-id")).thenReturn(Maybe.just(existing));
        when(identityProviderPluginService.getSchema(anyString())).thenReturn(Maybe.empty());

        service.update(ReferenceType.DOMAIN, "domain", "idp-id", update, principal, false)
                .test()
                .assertError(IdentityProviderPluginSchemaNotFoundException.class);
    }

    @Test
    void shouldAuditWhenUpdateFailsBeforeFiltering() {
        var principal = mock(User.class);
        var referenceType = ReferenceType.DOMAIN;
        var referenceId = "domain";
        var update = new UpdateIdentityProvider();
        update.setName("idp");
        update.setType("mock-idp");
        update.setConfiguration("{\"foo\":\"bar\"}");

        var identityProvider = new IdentityProvider();
        identityProvider.setId("idp-id");
        identityProvider.setName("idp");
        identityProvider.setType("mock-idp");
        identityProvider.setConfiguration("{\"foo\":\"bar\"}");
        identityProvider.setReferenceType(referenceType);
        identityProvider.setReferenceId(referenceId);
        identityProvider.setCreatedAt(new Date());
        identityProvider.setUpdatedAt(new Date());

        when(identityProviderService.findById("idp-id")).thenReturn(Maybe.just(identityProvider));
        when(identityProviderPluginService.getSchema(anyString())).thenReturn(Maybe.just("{\"type\":\"object\"}"));
        when(identityProviderService.update(referenceType, referenceId, "idp-id", update, principal, false)).thenReturn(Single.error(new TechnicalManagementException("fail")));

        service.update(referenceType, referenceId, "idp-id", update, principal, false)
                .test()
                .assertError(TechnicalManagementException.class);

        var audit = captureAudit();
        assertThat(audit.getType()).isEqualTo(EventType.IDENTITY_PROVIDER_UPDATED);
        assertThat(audit.getReferenceId()).isEqualTo(referenceId);
        assertThat(audit.getOutcome().getStatus()).isEqualTo(Status.FAILURE);
    }

    @Test
    void shouldAuditSuccessfulUpdate() {
        var principal = mock(User.class);
        var referenceType = ReferenceType.DOMAIN;
        var referenceId = "domain";
        var update = new UpdateIdentityProvider();
        update.setName("idp");
        update.setType("mock-idp");
        update.setConfiguration("{\"foo\":\"bar\"}");

        var identityProvider = new IdentityProvider();
        identityProvider.setId("idp-id");
        identityProvider.setName("idp");
        identityProvider.setType("mock-idp");
        identityProvider.setConfiguration("{\"foo\":\"bar\"}");
        identityProvider.setReferenceType(referenceType);
        identityProvider.setReferenceId(referenceId);
        identityProvider.setCreatedAt(new Date());
        identityProvider.setUpdatedAt(new Date());

        var updated = new IdentityProvider(identityProvider);
        updated.setConfiguration("{\"foo\":\"baz\"}");

        when(identityProviderService.findById("idp-id")).thenReturn(Maybe.just(identityProvider));
        when(identityProviderPluginService.getSchema(anyString())).thenReturn(Maybe.just("{\"type\":\"object\"}"));
        when(identityProviderService.update(referenceType, referenceId, "idp-id", update, principal, false)).thenReturn(Single.just(updated));

        service.update(referenceType, referenceId, "idp-id", update, principal, false)
                .test()
                .assertValue(updated)
                .assertComplete();

        var audit = captureAudit();
        assertThat(audit.getType()).isEqualTo(EventType.IDENTITY_PROVIDER_UPDATED);
        assertThat(audit.getReferenceId()).isEqualTo(referenceId);
        assertThat(audit.getOutcome().getStatus()).isEqualTo(Status.SUCCESS);
    }

    @Test
    void shouldThrowWhenIdentityProviderMissing() {
        var principal = mock(User.class);
        var referenceType = ReferenceType.DOMAIN;
        var referenceId = "domain";
        var update = new UpdateIdentityProvider();
        update.setName("idp");
        update.setType("mock-idp");
        update.setConfiguration("{}");

        when(identityProviderService.findById("missing")).thenReturn(Maybe.empty());

        service.update(referenceType, referenceId, "missing", update, principal, false)
                .test()
                .assertError(IdentityProviderNotFoundException.class);
    }

    @Test
    void shouldReturnStubWhenSchemaMissingOnRead() {
        var identityProvider = new IdentityProvider();
        identityProvider.setId("idp-id");
        identityProvider.setType("mock-idp");
        identityProvider.setConfiguration("{\"clientSecret\":\"value\"}");
        identityProvider.setReferenceType(ReferenceType.DOMAIN);
        identityProvider.setReferenceId("domain");
        identityProvider.setCreatedAt(new Date());
        identityProvider.setUpdatedAt(new Date());

        when(identityProviderService.findById(ReferenceType.DOMAIN, "domain", "idp-id")).thenReturn(Single.just(identityProvider));
        when(identityProviderPluginService.getSchema(anyString())).thenReturn(Maybe.empty());

        service.findById(ReferenceType.DOMAIN, "domain", "idp-id")
                .test()
                .assertValue(idp -> "{}".equals(idp.getConfiguration()));
    }

    private Audit captureAudit() {
        ArgumentCaptor<AuditBuilder<?>> captor = ArgumentCaptor.forClass(AuditBuilder.class);
        verify(auditService).report(captor.capture());
        return captor.getValue().build(objectMapper);
    }

    private IdentityProvider buildIdp(String configuration) {
        var identityProvider = new IdentityProvider();
        identityProvider.setId("idp-id");
        identityProvider.setName("idp");
        identityProvider.setType("mock-idp");
        identityProvider.setConfiguration(configuration);
        identityProvider.setReferenceType(ReferenceType.DOMAIN);
        identityProvider.setReferenceId("domain");
        identityProvider.setCreatedAt(new Date());
        identityProvider.setUpdatedAt(new Date());
        return identityProvider;
    }

    private String maskedSecret(String configuration) {
        try {
            return objectMapper.readTree(configuration).get("secret").asText();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
