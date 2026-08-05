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
package io.gravitee.am.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.common.audit.EntityType;
import io.gravitee.am.common.audit.EventType;
import io.gravitee.am.common.audit.Status;
import io.gravitee.am.common.event.Action;
import io.gravitee.am.common.event.Type;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.reporter.api.audit.model.Audit;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.dataplane.api.DataPlane;
import io.gravitee.am.model.DataPlaneDefinition;
import io.gravitee.am.model.Environment;
import io.gravitee.am.model.Organization;
import io.gravitee.am.plugins.dataplane.core.DataPlanePluginManager;
import io.gravitee.am.plugins.dataplane.core.MultiDataPlaneLoader;
import io.gravitee.am.plugins.handlers.api.core.PluginConfigurationValidator;
import io.gravitee.am.plugins.handlers.api.core.PluginConfigurationValidatorsRegistry;
import io.gravitee.am.repository.management.api.DataPlaneDefinitionRepository;
import io.gravitee.am.repository.management.api.DomainRepository;
import io.gravitee.am.service.dataplane.config.JdbcDataPlaneConfigHandler;
import io.gravitee.am.service.dataplane.config.MongoDataPlaneConfigHandler;
import io.gravitee.am.service.exception.DataPlaneDefinitionAlreadyExistsException;
import io.gravitee.am.service.exception.DataPlaneDefinitionNotFoundException;
import io.gravitee.am.service.exception.DataPlaneInUseByDomainsException;
import io.gravitee.am.service.exception.EnvironmentNotFoundException;
import io.gravitee.am.service.exception.InvalidParameterException;
import io.gravitee.am.service.exception.OrganizationNotFoundException;
import io.gravitee.am.service.impl.DataPlaneDefinitionServiceImpl;
import io.gravitee.am.service.model.DataPlaneDefinitionSummary;
import io.gravitee.am.service.model.NewDataPlaneDefinition;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DataPlaneDefinitionServiceTest {

    private static final String MONGO_CONFIGURATION = "{\"mongodb\": {\"dbname\": \"gravitee-am-acme\", \"host\": \"mongo\", \"port\": 27017}}";
    private static final String MONGO_CONFIGURATION_WITH_SECRETS =
            "{\"mongodb\": {\"dbname\": \"gravitee-am-acme\", \"host\": \"mongo\", \"port\": 27017, \"username\": \"am-user\", \"password\": \"sup3r-s3cret\"}}";

    @Mock
    private DataPlaneDefinitionRepository dataPlaneDefinitionRepository;

    @Mock
    private DomainRepository domainRepository;

    @Mock
    private OrganizationService organizationService;

    @Mock
    private EnvironmentService environmentService;

    @Mock
    private DataPlanePluginManager dataPlanePluginManager;

    @Mock
    private AuditService auditService;

    @Mock
    private EventService eventService;

    @Mock
    private MultiDataPlaneLoader configurationLoader;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final PluginConfigurationValidatorsRegistry validatorsRegistry = new PluginConfigurationValidatorsRegistry();

    private DataPlaneDefinitionService service;

    @BeforeEach
    void setUp() {
        service = new DataPlaneDefinitionServiceImpl(
                dataPlaneDefinitionRepository,
                domainRepository,
                organizationService,
                environmentService,
                dataPlanePluginManager,
                validatorsRegistry,
                List.of(new MongoDataPlaneConfigHandler(), new JdbcDataPlaneConfigHandler()),
                auditService,
                eventService,
                configurationLoader);

        when(dataPlanePluginManager.get("dataplane-am-mongodb")).thenReturn(mock(DataPlane.class));
        when(dataPlanePluginManager.get("dataplane-am-jdbc")).thenReturn(mock(DataPlane.class));
        when(organizationService.findById(anyString())).thenAnswer(invocation -> Single.just(organization(invocation.getArgument(0))));
        when(environmentService.findById(anyString(), anyString())).thenAnswer(invocation -> Single.just(environment(invocation.getArgument(0))));
        when(dataPlaneDefinitionRepository.findById(anyString())).thenReturn(Maybe.empty());
        when(dataPlaneDefinitionRepository.create(any())).thenAnswer(invocation -> Single.just(invocation.getArgument(0)));
        when(dataPlaneDefinitionRepository.delete(anyString())).thenReturn(Completable.complete());
        when(domainRepository.existsByDataPlaneId(anyString())).thenReturn(Single.just(false));
        when(eventService.create(any())).thenAnswer(invocation -> Single.just(invocation.getArgument(0)));
    }

    @Test
    void shouldCreateWithDefaultOrganizationAndEnvironment() {
        TestObserver<DataPlaneDefinitionSummary> observer = service.create(payload()).test();
        observer.awaitDone(10, TimeUnit.SECONDS);

        observer.assertComplete();
        observer.assertNoErrors();

        ArgumentCaptor<DataPlaneDefinition> captor = ArgumentCaptor.forClass(DataPlaneDefinition.class);
        verify(dataPlaneDefinitionRepository).create(captor.capture());

        DataPlaneDefinition created = captor.getValue();
        assertThat(created.getId()).isEqualTo("dp-acme");
        assertThat(created.getName()).isEqualTo("Customer ACME");
        assertThat(created.getType()).isEqualTo("mongodb");
        assertThat(created.getOrganizationId()).isEqualTo(Organization.DEFAULT);
        assertThat(created.getEnvironmentId()).isEqualTo(Environment.DEFAULT);
        assertThat(created.getCreatedAt()).isNotNull();
        assertThat(created.getUpdatedAt()).isNotNull();
        assertThat(created.getConfiguration()).contains("gravitee-am-acme");
    }

    @Test
    void shouldReturnTheRedactedSummaryOnCreate() {
        NewDataPlaneDefinition payload = payload();
        payload.setConfiguration(readTree(MONGO_CONFIGURATION_WITH_SECRETS));

        TestObserver<DataPlaneDefinitionSummary> observer = service.create(payload).test();
        observer.awaitDone(10, TimeUnit.SECONDS);

        observer.assertValue(summary -> "dp-acme".equals(summary.id()));
        observer.assertValue(summary -> "gravitee-am-acme".equals(summary.database()));
        observer.assertValue(summary -> List.of("mongo:27017").equals(summary.hosts()));
        observer.assertValue(summary -> !summary.toString().contains("sup3r-s3cret"));
    }

    @Test
    void shouldReportAnOrganizationAuditOnCreate() {
        NewDataPlaneDefinition payload = payload();
        payload.setOrganizationId("org-1");

        service.create(payload).test().awaitDone(10, TimeUnit.SECONDS).assertComplete().assertNoErrors();

        Audit audit = capturedAudit();
        assertThat(audit.getType()).isEqualTo(EventType.DATA_PLANE_CREATED);
        assertThat(audit.getReferenceType()).isEqualTo(ReferenceType.ORGANIZATION);
        assertThat(audit.getReferenceId()).isEqualTo("org-1");
        assertThat(audit.getTarget().getId()).isEqualTo("dp-acme");
        assertThat(audit.getTarget().getType()).isEqualTo(EntityType.DATA_PLANE);
        assertThat(audit.getOutcome().getStatus()).isEqualTo(Status.SUCCESS);
    }

    @Test
    void shouldNotLeakCredentialsIntoTheAudit() {
        NewDataPlaneDefinition payload = payload();
        payload.setConfiguration(readTree(MONGO_CONFIGURATION_WITH_SECRETS));

        service.create(payload).test().awaitDone(10, TimeUnit.SECONDS).assertComplete().assertNoErrors();

        assertThat(capturedAudit().toString()).doesNotContain("sup3r-s3cret", "am-user", "configuration");
    }

    @Test
    void shouldReportAFailedAuditWhenThePersistFails() {
        doReturn(Single.error(new TechnicalManagementException("boom"))).when(dataPlaneDefinitionRepository).create(any());

        service.create(payload()).test().awaitDone(10, TimeUnit.SECONDS)
                .assertError(TechnicalManagementException.class);

        Audit audit = capturedAudit();
        assertThat(audit.getType()).isEqualTo(EventType.DATA_PLANE_CREATED);
        assertThat(audit.getOutcome().getStatus()).isEqualTo(Status.FAILURE);
        assertThat(audit.getTarget().getId()).isEqualTo("dp-acme");
    }

    @Test
    void shouldNotReportAnAuditWhenValidationRejectsThePayload() {
        NewDataPlaneDefinition payload = payload();
        payload.setId(null);

        service.create(payload).test().awaitDone(10, TimeUnit.SECONDS)
                .assertError(InvalidParameterException.class);

        verify(auditService, never()).report(any());
    }

    private Audit capturedAudit() {
        ArgumentCaptor<AuditBuilder<?>> captor = ArgumentCaptor.forClass(AuditBuilder.class);
        verify(auditService).report(captor.capture());
        return captor.getValue().build(objectMapper);
    }

    @Test
    void shouldKeepTheProvidedOrganizationAndEnvironment() {
        NewDataPlaneDefinition payload = payload();
        payload.setOrganizationId("org-1");
        payload.setEnvironmentId("env-1");

        service.create(payload).test().awaitDone(10, TimeUnit.SECONDS).assertComplete().assertNoErrors();

        ArgumentCaptor<DataPlaneDefinition> captor = ArgumentCaptor.forClass(DataPlaneDefinition.class);
        verify(dataPlaneDefinitionRepository).create(captor.capture());
        assertThat(captor.getValue().getOrganizationId()).isEqualTo("org-1");
        assertThat(captor.getValue().getEnvironmentId()).isEqualTo("env-1");
        verify(environmentService).findById("env-1", "org-1");
    }

    @Test
    void shouldStoreTheConfigurationVerbatim() {
        service.create(payload()).test().awaitDone(10, TimeUnit.SECONDS);

        ArgumentCaptor<DataPlaneDefinition> captor = ArgumentCaptor.forClass(DataPlaneDefinition.class);
        verify(dataPlaneDefinitionRepository).create(captor.capture());

        JsonNode stored = readTree(captor.getValue().getConfiguration());
        assertThat(stored).isEqualTo(readTree(MONGO_CONFIGURATION));
    }

    @Test
    void shouldRejectAMissingId() {
        NewDataPlaneDefinition payload = payload();
        payload.setId("  ");

        assertRejected(payload, InvalidParameterException.class, "'id' is required");
    }

    @Test
    void shouldRejectTheReservedDefaultId() {
        NewDataPlaneDefinition payload = payload();
        payload.setId("default");

        assertRejected(payload, InvalidParameterException.class, "reserved");
    }

    @Test
    void shouldRejectAnIdDeclaredInTheGraviteeYml() {
        when(configurationLoader.isDeclared("dp-yml")).thenReturn(true);

        NewDataPlaneDefinition payload = payload();
        payload.setId("dp-yml");

        assertRejected(payload, InvalidParameterException.class, "reserved for a data plane declared in the gravitee.yml");
    }

    @Test
    void shouldRejectAMissingName() {
        NewDataPlaneDefinition payload = payload();
        payload.setName(null);

        assertRejected(payload, InvalidParameterException.class, "'name' is required");
    }

    @Test
    void shouldRejectAMissingType() {
        NewDataPlaneDefinition payload = payload();
        payload.setType(null);

        assertRejected(payload, InvalidParameterException.class, "'type' is required");
    }

    @Test
    void shouldRejectATypeWithNoDeployedPlugin() {
        NewDataPlaneDefinition payload = payload();
        payload.setType("cassandra");

        assertRejected(payload, InvalidParameterException.class, "No data plane plugin is deployed");
    }

    @Test
    void shouldRejectAConfigurationThatFailsThePluginSchema() {
        registerMongoSchema();
        NewDataPlaneDefinition payload = payload();
        payload.setConfiguration(readTree("{\"mongodb\": {\"dbname\": \"acme\", \"host\": \"mongo\", \"port\": \"twenty-seven-thousand\"}}"));

        assertRejected(payload, InvalidParameterException.class, "configuration.mongodb is not valid");
    }

    @Test
    void shouldAcceptAConfigurationThatMatchesThePluginSchema() {
        registerMongoSchema();

        service.create(payload()).test().awaitDone(10, TimeUnit.SECONDS).assertComplete().assertNoErrors();
    }

    @Test
    void shouldStillApplyTheTypeSpecificChecksOnceTheSchemaPasses() {
        registerMongoSchema();
        NewDataPlaneDefinition payload = payload();
        payload.setConfiguration(readTree("{\"mongodb\": {\"host\": \"mongo\", \"port\": 27017}}"));

        assertRejected(payload, InvalidParameterException.class, "requires either 'uri' or 'dbname'");
    }

    @Test
    void shouldSkipTheSchemaPassWhenThePluginShipsNoSchema() {
        NewDataPlaneDefinition payload = payload();
        payload.setConfiguration(readTree("{\"mongodb\": {\"dbname\": \"acme\", \"host\": \"mongo\", \"port\": \"twenty-seven-thousand\"}}"));

        service.create(payload).test().awaitDone(10, TimeUnit.SECONDS).assertComplete().assertNoErrors();
    }

    private void registerMongoSchema() {
        validatorsRegistry.put(PluginConfigurationValidator.defaultSchemaValidator("dataplane-am-mongodb", """
                {
                  "type": "object",
                  "properties": {
                    "dbname": { "type": "string" },
                    "host": { "type": "string" },
                    "port": { "type": "number" }
                  }
                }
                """));
    }

    @Test
    void shouldRejectATypeWithNoHandler() {
        when(dataPlanePluginManager.get("dataplane-am-cassandra")).thenReturn(mock(DataPlane.class));
        NewDataPlaneDefinition payload = payload();
        payload.setType("cassandra");
        payload.setConfiguration(readTree("{\"cassandra\": {\"host\": \"cassandra\"}}"));

        assertRejected(payload, InvalidParameterException.class, "No configuration validator is available");
    }

    @Test
    void shouldRejectAMissingConfiguration() {
        NewDataPlaneDefinition payload = payload();
        payload.setConfiguration(null);

        assertRejected(payload, InvalidParameterException.class, "'configuration' is required");
    }

    @Test
    void shouldRejectAConfigurationThatIsNotAnObject() {
        NewDataPlaneDefinition payload = payload();
        payload.setConfiguration(readTree("[]"));

        assertRejected(payload, InvalidParameterException.class, "'configuration' is required");
    }

    @Test
    void shouldRejectAConfigurationMissingTheDatabaseName() {
        NewDataPlaneDefinition payload = payload();
        payload.setConfiguration(readTree("{\"mongodb\": {\"host\": \"mongo\"}}"));

        assertRejected(payload, InvalidParameterException.class, "dbname");
    }

    @Test
    void shouldRejectAConfigurationDeclaringAForeignBlock() {
        NewDataPlaneDefinition payload = payload();
        payload.setConfiguration(readTree("{\"mongodb\": {\"dbname\": \"db\", \"host\": \"mongo\"}, \"jdbc\": {\"uri\": \"r2dbc:postgresql://pg/db\"}}"));

        assertRejected(payload, InvalidParameterException.class, "must only declare the 'mongodb' block");
    }

    @Test
    void shouldResolveTheOrganizationAndEnvironmentFromTheirHrids() {
        when(organizationService.findByHrid("acme")).thenReturn(Maybe.just(organization("org-1")));
        when(environmentService.findByHrid("org-1", "prod")).thenReturn(Maybe.just(environment("env-1")));
        NewDataPlaneDefinition payload = payload();
        payload.setOrganizationHrid("acme");
        payload.setEnvironmentHrid("prod");

        service.create(payload).test().awaitDone(10, TimeUnit.SECONDS).assertComplete().assertNoErrors();

        ArgumentCaptor<DataPlaneDefinition> captor = ArgumentCaptor.forClass(DataPlaneDefinition.class);
        verify(dataPlaneDefinitionRepository).create(captor.capture());
        assertThat(captor.getValue().getOrganizationId()).isEqualTo("org-1");
        assertThat(captor.getValue().getEnvironmentId()).isEqualTo("env-1");
    }

    @Test
    void shouldPreferTheIdsOverTheHrids() {
        NewDataPlaneDefinition payload = payload();
        payload.setOrganizationId("org-1");
        payload.setOrganizationHrid("ignored-org");
        payload.setEnvironmentId("env-1");
        payload.setEnvironmentHrid("ignored-env");

        service.create(payload).test().awaitDone(10, TimeUnit.SECONDS).assertComplete().assertNoErrors();

        ArgumentCaptor<DataPlaneDefinition> captor = ArgumentCaptor.forClass(DataPlaneDefinition.class);
        verify(dataPlaneDefinitionRepository).create(captor.capture());
        assertThat(captor.getValue().getOrganizationId()).isEqualTo("org-1");
        assertThat(captor.getValue().getEnvironmentId()).isEqualTo("env-1");
        verify(organizationService, never()).findByHrid(anyString());
        verify(environmentService, never()).findByHrid(anyString(), anyString());
    }

    @Test
    void shouldResolveTheEnvironmentHridWithinTheOrganizationNamedById() {
        when(environmentService.findByHrid("org-1", "prod")).thenReturn(Maybe.just(environment("env-1")));
        NewDataPlaneDefinition payload = payload();
        payload.setOrganizationId("org-1");
        payload.setEnvironmentHrid("prod");

        service.create(payload).test().awaitDone(10, TimeUnit.SECONDS).assertComplete().assertNoErrors();

        ArgumentCaptor<DataPlaneDefinition> captor = ArgumentCaptor.forClass(DataPlaneDefinition.class);
        verify(dataPlaneDefinitionRepository).create(captor.capture());
        assertThat(captor.getValue().getEnvironmentId()).isEqualTo("env-1");
    }

    @Test
    void shouldRejectAnUnknownOrganizationHrid() {
        when(organizationService.findByHrid("nope")).thenReturn(Maybe.empty());
        NewDataPlaneDefinition payload = payload();
        payload.setOrganizationHrid("nope");

        assertRejected(payload, InvalidParameterException.class, "Unknown organization hrid [nope]");
    }

    @Test
    void shouldRejectAnUnknownEnvironmentHrid() {
        when(environmentService.findByHrid(Organization.DEFAULT, "nope")).thenReturn(Maybe.empty());
        NewDataPlaneDefinition payload = payload();
        payload.setEnvironmentHrid("nope");

        assertRejected(payload, InvalidParameterException.class, "Unknown environment hrid [nope]");
    }

    @Test
    void shouldRejectAnUnknownOrganization() {
        when(organizationService.findById("org-unknown")).thenReturn(Single.error(new OrganizationNotFoundException("org-unknown")));
        NewDataPlaneDefinition payload = payload();
        payload.setOrganizationId("org-unknown");

        assertRejected(payload, InvalidParameterException.class, "Unknown organization");
    }

    @Test
    void shouldRejectAnUnknownEnvironment() {
        when(environmentService.findById("env-unknown", Organization.DEFAULT))
                .thenReturn(Single.error(new EnvironmentNotFoundException("env-unknown")));
        NewDataPlaneDefinition payload = payload();
        payload.setEnvironmentId("env-unknown");

        assertRejected(payload, InvalidParameterException.class, "Unknown environment");
    }

    @Test
    void shouldRejectADuplicateId() {
        when(dataPlaneDefinitionRepository.findById("dp-acme")).thenReturn(Maybe.just(definition("dp-acme", Environment.DEFAULT)));

        assertRejected(payload(), DataPlaneDefinitionAlreadyExistsException.class, "dp-acme");
    }

    @Test
    void shouldAcceptASecondDefinitionForTheSameEnvironment() {
        NewDataPlaneDefinition second = payload();
        second.setId("dp-acme-2");

        TestObserver<DataPlaneDefinitionSummary> observer = service.create(second).test();
        observer.awaitDone(10, TimeUnit.SECONDS);

        observer.assertComplete();
        observer.assertNoErrors();
        observer.assertValue(summary -> "dp-acme-2".equals(summary.id())
                && Environment.DEFAULT.equals(summary.environmentId()));
    }

    @Test
    void shouldListTheDefinitionsWithoutTheirCredentials() {
        DataPlaneDefinition withSecrets = definition("dp-acme", "env-1");
        withSecrets.setConfiguration(MONGO_CONFIGURATION_WITH_SECRETS);
        when(dataPlaneDefinitionRepository.findAll()).thenReturn(Flowable.just(withSecrets, definition("dp-other", "env-2")));

        TestObserver<List<DataPlaneDefinitionSummary>> observer = service.findAll().toList().test();
        observer.awaitDone(10, TimeUnit.SECONDS);

        observer.assertComplete();
        observer.assertNoErrors();
        observer.assertValue(summaries -> summaries.size() == 2);
        observer.assertValue(summaries -> summaries.stream().map(DataPlaneDefinitionSummary::id).toList()
                .equals(List.of("dp-acme", "dp-other")));
        observer.assertValue(summaries -> summaries.stream().allMatch(s -> "gravitee-am-acme".equals(s.database())));
        observer.assertValue(summaries -> !summaries.toString().contains("sup3r-s3cret"));
    }

    @Test
    void shouldFindOneDefinitionWithoutItsCredentials() {
        DataPlaneDefinition withSecrets = definition("dp-acme", "env-1");
        withSecrets.setConfiguration(MONGO_CONFIGURATION_WITH_SECRETS);
        when(dataPlaneDefinitionRepository.findById("dp-acme")).thenReturn(Maybe.just(withSecrets));

        TestObserver<DataPlaneDefinitionSummary> observer = service.findById("dp-acme").test();
        observer.awaitDone(10, TimeUnit.SECONDS);

        observer.assertComplete();
        observer.assertValue(summary -> "dp-acme".equals(summary.id()));
        observer.assertValue(summary -> "env-1".equals(summary.environmentId()));
        observer.assertValue(summary -> "gravitee-am-acme".equals(summary.database()));
        observer.assertValue(summary -> List.of("mongo:27017").equals(summary.hosts()));
        observer.assertValue(summary -> !summary.toString().contains("sup3r-s3cret"));
    }

    @Test
    void shouldFailWhenTheDefinitionDoesNotExist() {
        TestObserver<DataPlaneDefinitionSummary> observer = service.findById("dp-missing").test();
        observer.awaitDone(10, TimeUnit.SECONDS);

        observer.assertError(DataPlaneDefinitionNotFoundException.class);
    }

    @Test
    void shouldSummarizeADefinitionWhoseTypeIsNoLongerDeployed() {
        DataPlaneDefinition orphan = definition("dp-orphan", "env-1");
        orphan.setType("cassandra");
        when(dataPlaneDefinitionRepository.findById("dp-orphan")).thenReturn(Maybe.just(orphan));

        TestObserver<DataPlaneDefinitionSummary> observer = service.findById("dp-orphan").test();
        observer.awaitDone(10, TimeUnit.SECONDS);

        observer.assertComplete();
        observer.assertValue(summary -> summary.database() == null);
        observer.assertValue(summary -> summary.hosts().isEmpty());
    }

    @Test
    void shouldSummarizeADefinitionWhoseConfigurationIsUnreadable() {
        DataPlaneDefinition corrupted = definition("dp-corrupted", "env-1");
        corrupted.setConfiguration("not json");
        when(dataPlaneDefinitionRepository.findById("dp-corrupted")).thenReturn(Maybe.just(corrupted));

        TestObserver<DataPlaneDefinitionSummary> observer = service.findById("dp-corrupted").test();
        observer.awaitDone(10, TimeUnit.SECONDS);

        observer.assertComplete();
        observer.assertValue(summary -> "dp-corrupted".equals(summary.id()));
        observer.assertValue(summary -> summary.database() == null);
    }

    @Test
    void shouldReportADuplicateIdWhenTheInsertLosesTheRace() {
        when(dataPlaneDefinitionRepository.findById("dp-acme"))
                .thenReturn(Maybe.empty())                                  // free when we checked
                .thenReturn(Maybe.just(definition("dp-acme", "env-1")));    // taken by the time we insert
        doReturn(Single.error(new TechnicalManagementException("duplicate key")))
                .when(dataPlaneDefinitionRepository).create(any());

        service.create(payload()).test().awaitDone(10, TimeUnit.SECONDS)
                .assertError(DataPlaneDefinitionAlreadyExistsException.class);
    }

    @Test
    void shouldDeleteAndReportAnAudit() {
        when(dataPlaneDefinitionRepository.findById("dp-acme")).thenReturn(Maybe.just(definition("dp-acme", "env-1")));

        TestObserver<Void> observer = service.delete("dp-acme").test();
        observer.awaitDone(10, TimeUnit.SECONDS);

        observer.assertComplete();
        verify(dataPlaneDefinitionRepository).delete("dp-acme");

        Audit audit = capturedAudit();
        assertThat(audit.getType()).isEqualTo(EventType.DATA_PLANE_DELETED);
        assertThat(audit.getOutcome().getStatus()).isEqualTo(Status.SUCCESS);
        assertThat(audit.getTarget().getId()).isEqualTo("dp-acme");
        assertThat(audit.getTarget().getType()).isEqualTo(EntityType.DATA_PLANE);
        assertThat(audit.getOutcome().getMessage()).contains("gravitee-am-acme", "mongo:27017");
    }

    @Test
    void shouldFailDeletingAnUnknownDefinition() {
        TestObserver<Void> observer = service.delete("dp-missing").test();
        observer.awaitDone(10, TimeUnit.SECONDS);

        observer.assertError(DataPlaneDefinitionNotFoundException.class);
        verify(dataPlaneDefinitionRepository, never()).delete(anyString());
        verify(auditService, never()).report(any());
    }

    @Test
    void shouldRejectDeletingADataPlaneStillUsedByADomain() {
        when(dataPlaneDefinitionRepository.findById("dp-acme")).thenReturn(Maybe.just(definition("dp-acme", "env-1")));
        when(domainRepository.existsByDataPlaneId("dp-acme")).thenReturn(Single.just(true));

        TestObserver<Void> observer = service.delete("dp-acme").test();
        observer.awaitDone(10, TimeUnit.SECONDS);

        observer.assertError(DataPlaneInUseByDomainsException.class);
        verify(dataPlaneDefinitionRepository, never()).delete(anyString());

        Audit audit = capturedAudit();
        assertThat(audit.getType()).isEqualTo(EventType.DATA_PLANE_DELETED);
        assertThat(audit.getOutcome().getStatus()).isEqualTo(Status.FAILURE);
    }

    @Test
    void shouldNotLeakCredentialsIntoTheDeleteAudit() {
        DataPlaneDefinition withSecrets = definition("dp-acme", "env-1");
        withSecrets.setConfiguration(MONGO_CONFIGURATION_WITH_SECRETS);
        when(dataPlaneDefinitionRepository.findById("dp-acme")).thenReturn(Maybe.just(withSecrets));

        service.delete("dp-acme").test().awaitDone(10, TimeUnit.SECONDS);

        assertThat(capturedAudit().toString()).doesNotContain("sup3r-s3cret", "am-user", "configuration");
    }

    @Test
    void shouldPublishADeployEventOnCreate() {
        service.create(payload()).test().awaitDone(10, TimeUnit.SECONDS).assertComplete().assertNoErrors();

        Payload payload = capturedEvent(Type.DATA_PLANE).getPayload();
        assertThat(payload.getId()).isEqualTo("dp-acme");
        assertThat(payload.getReferenceType()).isEqualTo(ReferenceType.ENVIRONMENT);
        assertThat(payload.getReferenceId()).isEqualTo(Environment.DEFAULT);
        assertThat(payload.getAction()).isEqualTo(Action.CREATE);
    }

    @Test
    void shouldPublishAnUndeployEventOnDelete() {
        when(dataPlaneDefinitionRepository.findById("dp-acme")).thenReturn(Maybe.just(definition("dp-acme", "env-1")));

        service.delete("dp-acme").test().awaitDone(10, TimeUnit.SECONDS).assertComplete();

        Payload payload = capturedEvent(Type.DATA_PLANE).getPayload();
        assertThat(payload.getId()).isEqualTo("dp-acme");
        assertThat(payload.getReferenceType()).isEqualTo(ReferenceType.ENVIRONMENT);
        assertThat(payload.getReferenceId()).isEqualTo("env-1");
        assertThat(payload.getAction()).isEqualTo(Action.DELETE);
    }

    /**
     * The event carries no data plane id, so it reaches every gateway rather than one. They have no
     * listener for it and drop it, which is what already happens to license events.
     */
    @Test
    void shouldNotScopeTheEventToASingleDataPlane() {
        service.create(payload()).test().awaitDone(10, TimeUnit.SECONDS).assertComplete();

        Event event = capturedEvent(Type.DATA_PLANE);
        assertThat(event.getDataPlaneId()).isNull();
        assertThat(event.getEnvironmentId()).isNull();
    }

    @Test
    void shouldFailTheCreationWhenTheEventCannotBeWritten() {
        // doReturn: when(...) would invoke the mock and hit the setUp stub with a null event
        doReturn(Single.error(new TechnicalManagementException("events are down"))).when(eventService).create(any());

        TestObserver<DataPlaneDefinitionSummary> observer = service.create(payload()).test();
        observer.awaitDone(10, TimeUnit.SECONDS);

        observer.assertError(TechnicalManagementException.class);
        // the row is committed and inert: no node registers it until a restart
        verify(dataPlaneDefinitionRepository).create(any());
        // the audit runs before the event, so the creation is still recorded as the success it was
        assertThat(capturedAudit().getOutcome().getStatus()).isEqualTo(Status.SUCCESS);
    }

    @Test
    void shouldNotPublishAnEventWhenTheDeleteIsRefused() {
        when(dataPlaneDefinitionRepository.findById("dp-acme")).thenReturn(Maybe.just(definition("dp-acme", "env-1")));
        when(domainRepository.existsByDataPlaneId("dp-acme")).thenReturn(Single.just(true));

        service.delete("dp-acme").test().awaitDone(10, TimeUnit.SECONDS)
                .assertError(DataPlaneInUseByDomainsException.class);

        verify(eventService, never()).create(any());
    }

    private Event capturedEvent(Type expectedType) {
        ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
        verify(eventService).create(captor.capture());
        Event event = captor.getValue();
        assertThat(event.getType()).isEqualTo(expectedType);
        return event;
    }

    private void assertRejected(NewDataPlaneDefinition payload, Class<? extends Throwable> type, String messageFragment) {
        TestObserver<DataPlaneDefinitionSummary> observer = service.create(payload).test();
        observer.awaitDone(10, TimeUnit.SECONDS);

        observer.assertError(type);
        observer.assertError(throwable -> throwable.getMessage().contains(messageFragment));
        verify(dataPlaneDefinitionRepository, never()).create(any());
    }

    private NewDataPlaneDefinition payload() {
        NewDataPlaneDefinition payload = new NewDataPlaneDefinition();
        payload.setId("dp-acme");
        payload.setName("Customer ACME");
        payload.setType("mongodb");
        payload.setGatewayUrl("https://gw-acme.cloud.gravitee.io");
        payload.setConfiguration(readTree(MONGO_CONFIGURATION));
        return payload;
    }

    private Organization organization(String id) {
        Organization organization = new Organization();
        organization.setId(id);
        return organization;
    }

    private Environment environment(String id) {
        Environment environment = new Environment();
        environment.setId(id);
        return environment;
    }

    private DataPlaneDefinition definition(String id, String environmentId) {
        DataPlaneDefinition definition = new DataPlaneDefinition();
        definition.setId(id);
        definition.setName("Data plane " + id);
        definition.setType("mongodb");
        definition.setOrganizationId(Organization.DEFAULT);
        definition.setEnvironmentId(environmentId);
        definition.setConfiguration(MONGO_CONFIGURATION);
        definition.setCreatedAt(new Date());
        definition.setUpdatedAt(new Date());
        return definition;
    }

    private JsonNode readTree(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }
}
