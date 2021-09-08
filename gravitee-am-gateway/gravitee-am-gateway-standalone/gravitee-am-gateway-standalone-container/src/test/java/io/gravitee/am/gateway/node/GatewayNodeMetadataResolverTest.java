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
package io.gravitee.am.gateway.node;

import io.gravitee.am.model.Installation;
import io.gravitee.am.model.Organization;
import io.gravitee.am.repository.management.api.EnvironmentRepository;
import io.gravitee.am.repository.management.api.InstallationRepository;
import io.gravitee.am.repository.management.api.OrganizationRepository;
import io.gravitee.node.api.Node;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.core.env.Environment;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;

import static io.gravitee.am.gateway.node.GatewayNodeMetadataResolver.ENVIRONMENTS_SYSTEM_PROPERTY;
import static io.gravitee.am.gateway.node.GatewayNodeMetadataResolver.ORGANIZATIONS_SYSTEM_PROPERTY;
import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class GatewayNodeMetadataResolverTest {


    @Mock
    private InstallationRepository installationRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private EnvironmentRepository environmentRepository;

    @Mock
    private Environment configuration;

    @InjectMocks
    private GatewayNodeMetadataResolver cut;

    @Test
    public void shouldResolveOrganizations() {

        final Installation installation = new Installation();
        installation.setId("install#1");

        final Organization organization = new Organization();
        organization.setId("org#1");
        organization.setHrids(Arrays.asList("france"));

        final io.gravitee.am.model.Environment environment = new io.gravitee.am.model.Environment();
        environment.setId("env#1");
        environment.setHrids(Arrays.asList("dev"));
        environment.setOrganizationId(organization.getId());

        when(configuration.getProperty(ORGANIZATIONS_SYSTEM_PROPERTY)).thenReturn("france");
        when(installationRepository.find()).thenReturn(Maybe.just(installation));
        when(organizationRepository.findByHrids(Arrays.asList("france"))).thenReturn(Flowable.just(organization));
        when(environmentRepository.findAll(organization.getId())).thenReturn(Flowable.just(environment));

        final Map<String, Object> metadata = cut.resolve();

        assertEquals(singleton("org#1"), metadata.get(Node.META_ORGANIZATIONS));
        assertEquals(singleton("env#1"), metadata.get(Node.META_ENVIRONMENTS));
    }

    @Test
    public void shouldResolveOrganizationAndEnvironments() {

        final Installation installation = new Installation();
        installation.setId("install#1");

        final Organization organization = new Organization();
        organization.setId("org#1");
        organization.setHrids(Arrays.asList("france"));

        final io.gravitee.am.model.Environment environmentDev = new io.gravitee.am.model.Environment();
        environmentDev.setId("env#1");
        environmentDev.setHrids(Arrays.asList("dev"));
        environmentDev.setOrganizationId(organization.getId());

        final io.gravitee.am.model.Environment environmentQa = new io.gravitee.am.model.Environment();
        environmentQa.setId("env#2");
        environmentQa.setHrids(Arrays.asList("qa"));
        environmentQa.setOrganizationId(organization.getId());

        final io.gravitee.am.model.Environment environmentPrep = new io.gravitee.am.model.Environment();
        environmentPrep.setId("env#3");
        environmentPrep.setHrids(Arrays.asList("prep"));
        environmentPrep.setOrganizationId(organization.getId());

        when(configuration.getProperty(ORGANIZATIONS_SYSTEM_PROPERTY)).thenReturn("france");
        when(configuration.getProperty(ENVIRONMENTS_SYSTEM_PROPERTY)).thenReturn("dev,qa");
        when(installationRepository.find()).thenReturn(Maybe.just(installation));
        when(organizationRepository.findByHrids(Arrays.asList("france"))).thenReturn(Flowable.just(organization));
        when(environmentRepository.findAll(organization.getId())).thenReturn(Flowable.just(environmentDev, environmentQa, environmentPrep));

        final Map<String, Object> metadata = cut.resolve();

        assertEquals(singleton("org#1"), metadata.get(Node.META_ORGANIZATIONS));
        assertEquals(new HashSet<>(Arrays.asList("env#1", "env#2")), metadata.get(Node.META_ENVIRONMENTS));
    }

    @Test
    public void shouldResolveEnvironments() {

        final Installation installation = new Installation();
        installation.setId("install#1");

        final io.gravitee.am.model.Environment environment = new io.gravitee.am.model.Environment();
        environment.setId("env#1");
        environment.setHrids(Arrays.asList("dev"));

        when(configuration.getProperty(ENVIRONMENTS_SYSTEM_PROPERTY)).thenReturn("dev");
        when(installationRepository.find()).thenReturn(Maybe.just(installation));
        when(environmentRepository.findAll()).thenReturn(Flowable.just(environment));

        final Map<String, Object> metadata = cut.resolve();

        assertEquals(singleton("env#1"), metadata.get(Node.META_ENVIRONMENTS));
    }

    @Test
    public void shouldResolveEmptyOrganizationsAndEnvironments() {

        final Installation installation = new Installation();
        installation.setId("install#1");

        when(installationRepository.find()).thenReturn(Maybe.just(installation));

        final Map<String, Object> metadata = cut.resolve();

        assertEquals(emptySet(), metadata.get(Node.META_ORGANIZATIONS));
        assertEquals(emptySet(), metadata.get(Node.META_ENVIRONMENTS));
    }
}