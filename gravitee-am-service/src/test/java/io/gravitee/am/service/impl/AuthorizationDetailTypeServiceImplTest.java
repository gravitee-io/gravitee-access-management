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
package io.gravitee.am.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.common.audit.EntityType;
import io.gravitee.am.common.audit.EventType;
import io.gravitee.am.common.audit.Status;
import io.gravitee.am.common.event.Action;
import io.gravitee.am.common.event.Type;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.model.oauth2.AuthorizationDetailType;
import io.gravitee.am.reporter.api.audit.model.Audit;
import io.gravitee.am.repository.exceptions.UniqueConstraintViolationException;
import io.gravitee.am.repository.management.api.AuthorizationDetailTypeRepository;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.EventService;
import io.gravitee.am.service.exception.AuthorizationDetailTypeAlreadyExistsException;
import io.gravitee.am.service.exception.InvalidAuthorizationDetailTypeException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.model.NewAuthorizationDetailType;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.validators.authorizationdetailtype.AuthorizationDetailTypeValidator;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthorizationDetailTypeServiceImplTest {

    private static final String DOMAIN_ID = "domain-1";
    private static final String SCHEMA = "{\"type\":\"object\",\"properties\":{\"type\":{\"const\":\"payment_initiation\"}},\"required\":[\"type\"]}";

    @InjectMocks
    private AuthorizationDetailTypeServiceImpl service;

    @Mock
    private AuthorizationDetailTypeRepository repository;

    @Spy
    private AuthorizationDetailTypeValidator validator = new AuthorizationDetailTypeValidator();

    @Mock
    private EventService eventService;

    @Mock
    private AuditService auditService;

    private Domain domain;

    @BeforeEach
    void setUp() {
        domain = new Domain();
        domain.setId(DOMAIN_ID);
    }

    @Test
    void shouldCreate() {
        when(repository.findByDomainAndType(DOMAIN_ID, "payment_initiation")).thenReturn(Maybe.empty());
        when(repository.create(any())).thenAnswer(invocation -> Single.just(invocation.getArgument(0)));
        when(eventService.create(any(), eq(domain))).thenReturn(Single.just(new Event()));

        AuthorizationDetailType created = service.create(domain, input("payment_initiation", SCHEMA), principal()).blockingGet();

        assertThat(UUID.fromString(created.getId()).version()).isEqualTo(4);
        assertThat(created.getDomain()).isEqualTo(DOMAIN_ID);
        assertThat(created.getType()).isEqualTo("payment_initiation");
        assertThat(created.getName()).isEqualTo("Payment initiation");
        assertThat(created.getDescription()).isEqualTo("Initiates a payment");
        assertThat(created.getSchema()).isEqualTo(SCHEMA);
        assertThat(created.isDiscovery()).isTrue();
        assertThat(created.getExpiresIn()).isEqualTo(3600);
        assertThat(created.getCreatedAt()).isNotNull().isEqualTo(created.getUpdatedAt());

        ArgumentCaptor<Event> event = ArgumentCaptor.forClass(Event.class);
        verify(eventService).create(event.capture(), eq(domain));
        assertThat(event.getValue().getType()).isEqualTo(Type.AUTHORIZATION_DETAIL_TYPE);
        assertThat(event.getValue().getPayload().getId()).isEqualTo(created.getId());
        assertThat(event.getValue().getPayload().getReferenceType()).isEqualTo(ReferenceType.DOMAIN);
        assertThat(event.getValue().getPayload().getReferenceId()).isEqualTo(DOMAIN_ID);
        assertThat(event.getValue().getPayload().getAction()).isEqualTo(Action.CREATE);

        Audit audit = reportedAudit();
        assertThat(audit.getType()).isEqualTo(EventType.AUTHORIZATION_DETAIL_TYPE_CREATED);
        assertThat(audit.getOutcome().getStatus()).isEqualTo(Status.SUCCESS);
        assertThat(audit.getActor().getId()).isEqualTo("principal-1");
        assertThat(audit.getReferenceType()).isEqualTo(ReferenceType.DOMAIN);
        assertThat(audit.getReferenceId()).isEqualTo(DOMAIN_ID);
        assertThat(audit.getTarget().getId()).isEqualTo(created.getId());
        assertThat(audit.getTarget().getType()).isEqualTo(EntityType.AUTHORIZATION_DETAIL_TYPE);
        assertThat(audit.getTarget().getAlternativeId()).isEqualTo("payment_initiation");
    }

    @Test
    void shouldCreateWithoutOptionalFields() {
        NewAuthorizationDetailType input = new NewAuthorizationDetailType();
        input.setType("https://scheme.example.org/files");
        input.setName("Files");
        when(repository.findByDomainAndType(DOMAIN_ID, "https://scheme.example.org/files")).thenReturn(Maybe.empty());
        when(repository.create(any())).thenAnswer(invocation -> Single.just(invocation.getArgument(0)));
        when(eventService.create(any(), eq(domain))).thenReturn(Single.just(new Event()));

        AuthorizationDetailType created = service.create(domain, input, principal()).blockingGet();

        assertThat(created.getDescription()).isNull();
        assertThat(created.getSchema()).isNull();
        assertThat(created.isDiscovery()).isFalse();
        assertThat(created.getExpiresIn()).isNull();
    }

    @Test
    void shouldRejectInvalidInputWithoutStoringIt() {
        service.create(domain, input("payment initiation", null), principal()).test()
                .assertError(error -> error instanceof InvalidAuthorizationDetailTypeException
                        && error.getMessage().equals("Type must contain only visible ASCII characters (%x21-7E)"));

        verify(repository, never()).findByDomainAndType(any(), any());
        verify(repository, never()).create(any());
        verify(eventService, never()).create(any(), any());
        assertFailureAudited();
    }

    @Test
    void shouldRejectInvalidSchemaWithoutStoringIt() {
        service.create(domain, input("payment_initiation", "{\"type\":\"object\"}"), principal()).test()
                .assertError(error -> error instanceof InvalidAuthorizationDetailTypeException
                        && error.getMessage().startsWith("Schema must constrain \"type\""));

        verify(repository, never()).create(any());
        assertFailureAudited();
    }

    @Test
    void shouldRejectDuplicateIgnoringCase() {
        when(repository.findByDomainAndType(DOMAIN_ID, "Payment_Initiation"))
                .thenReturn(Maybe.just(AuthorizationDetailType.builder().id("existing").type("payment_initiation").build()));

        service.create(domain, input("Payment_Initiation", null), principal()).test()
                .assertError(error -> error instanceof AuthorizationDetailTypeAlreadyExistsException
                        && error.getMessage().contains("already exists"));

        verify(repository, never()).create(any());
        verify(eventService, never()).create(any(), any());
        assertFailureAudited();
    }

    @Test
    void shouldRejectDuplicateCreatedConcurrently() {
        when(repository.findByDomainAndType(DOMAIN_ID, "payment_initiation")).thenReturn(Maybe.empty());
        when(repository.create(any())).thenReturn(Single.error(new UniqueConstraintViolationException("duplicate", new RuntimeException())));

        service.create(domain, input("payment_initiation", null), principal()).test()
                .assertError(error -> error instanceof AuthorizationDetailTypeAlreadyExistsException
                        && error.getMessage().contains("already exists"));

        verify(eventService, never()).create(any(), any());
        assertFailureAudited();
    }

    @Test
    void shouldWrapRepositoryFailure() {
        when(repository.findByDomainAndType(DOMAIN_ID, "payment_initiation")).thenReturn(Maybe.empty());
        when(repository.create(any())).thenReturn(Single.error(new RuntimeException("boom")));

        service.create(domain, input("payment_initiation", null), principal()).test()
                .assertError(TechnicalManagementException.class);

        verify(eventService, never()).create(any(), any());
        assertFailureAudited();
    }

    @Test
    void shouldFindByIdInDomain() {
        AuthorizationDetailType existing = AuthorizationDetailType.builder().id("id-1").domain(DOMAIN_ID).build();
        when(repository.findByDomainAndId(DOMAIN_ID, "id-1")).thenReturn(Maybe.just(existing));

        service.findById(DOMAIN_ID, "id-1").test().assertValue(existing);
    }

    @Test
    void shouldFindNothingOutsideDomain() {
        when(repository.findByDomainAndId("other-domain", "id-1")).thenReturn(Maybe.empty());

        service.findById("other-domain", "id-1").test().assertComplete().assertNoValues();
    }

    @Test
    void shouldDeleteByDomain() {
        when(repository.deleteByDomain(DOMAIN_ID)).thenReturn(Completable.complete());

        service.deleteByDomain(DOMAIN_ID).test().assertComplete();

        verify(repository).deleteByDomain(DOMAIN_ID);
    }

    private void assertFailureAudited() {
        Audit audit = reportedAudit();
        assertThat(audit.getType()).isEqualTo(EventType.AUTHORIZATION_DETAIL_TYPE_CREATED);
        assertThat(audit.getOutcome().getStatus()).isEqualTo(Status.FAILURE);
        assertThat(audit.getActor().getId()).isEqualTo("principal-1");
        assertThat(audit.getReferenceType()).isEqualTo(ReferenceType.DOMAIN);
        assertThat(audit.getReferenceId()).isEqualTo(DOMAIN_ID);
    }

    private Audit reportedAudit() {
        ArgumentCaptor<AuditBuilder<?>> builder = ArgumentCaptor.forClass(AuditBuilder.class);
        verify(auditService).report(builder.capture());
        return builder.getValue().build(new ObjectMapper());
    }

    private static NewAuthorizationDetailType input(String type, String schema) {
        NewAuthorizationDetailType input = new NewAuthorizationDetailType();
        input.setType(type);
        input.setName("Payment initiation");
        input.setDescription("Initiates a payment");
        input.setSchema(schema);
        input.setDiscovery(true);
        input.setExpiresIn(3600);
        return input;
    }

    private static User principal() {
        DefaultUser user = new DefaultUser("admin");
        user.setId("principal-1");
        return user;
    }
}
