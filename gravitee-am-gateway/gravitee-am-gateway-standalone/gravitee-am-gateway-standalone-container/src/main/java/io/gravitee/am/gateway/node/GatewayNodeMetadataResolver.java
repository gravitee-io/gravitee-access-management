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

import io.gravitee.am.model.Organization;
import io.gravitee.am.repository.management.api.EnvironmentRepository;
import io.gravitee.am.repository.management.api.InstallationRepository;
import io.gravitee.am.repository.management.api.OrganizationRepository;
import io.gravitee.node.api.NodeMetadataResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static io.gravitee.node.api.Node.META_ENVIRONMENTS;
import static io.gravitee.node.api.Node.META_INSTALLATION;
import static io.gravitee.node.api.Node.META_ORGANIZATIONS;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
public class GatewayNodeMetadataResolver implements NodeMetadataResolver {

    private final Logger logger = LoggerFactory.getLogger(GatewayNodeMetadataResolver.class);

    protected static final String SEPARATOR = ",";
    protected static final String ENVIRONMENTS_SYSTEM_PROPERTY = "environments";
    protected static final String ORGANIZATIONS_SYSTEM_PROPERTY = "organizations";

    @Lazy
    @Autowired
    private InstallationRepository installationRepository;

    @Lazy
    @Autowired
    private OrganizationRepository organizationRepository;

    @Lazy
    @Autowired
    private EnvironmentRepository environmentRepository;

    @Autowired
    private Environment configuration;

    public Map<String, Object> resolve() {
        final HashMap<String, Object> metadata = new HashMap<>();

        final String installationId = getInstallationId();
        final List<io.gravitee.am.model.Environment> environments = loadEnvironments();
        final Set<String> organizationIds =  environments.stream().map(io.gravitee.am.model.Environment::getOrganizationId).collect(Collectors.toSet());
        final Set<String> environmentIds = environments.stream().map(io.gravitee.am.model.Environment::getId).collect(Collectors.toSet());

        metadata.put(META_INSTALLATION, installationId);
        metadata.put(META_ORGANIZATIONS, organizationIds);
        metadata.put(META_ENVIRONMENTS, environmentIds);

        return metadata;
    }

    private String getInstallationId() {
        String installationId = null;
        try {
            final var installation = installationRepository.find().blockingGet();
            if (installation != null) {
                installationId = installation.getId();
            } else {
                logger.debug("No installation found");
            }
        } catch (Exception e) {
            logger.warn("Unable to load installation id", e);
        }
        return installationId;
    }

    private List<io.gravitee.am.model.Environment> loadEnvironments() {
        Optional<List<String>> environmentHrids = getSystemValues(ENVIRONMENTS_SYSTEM_PROPERTY);
        Optional<List<String>> organizationHrids = getSystemValues(ORGANIZATIONS_SYSTEM_PROPERTY);

        List<io.gravitee.am.model.Environment> environments = new ArrayList<>();

        if (organizationHrids.isPresent()) {
            final List<Organization> foundOrgs = organizationRepository.findByHrids(organizationHrids.get()).toList().blockingGet();
            environments = foundOrgs.stream().flatMap(org -> environmentRepository.findAll(org.getId())
                    .filter(environment1 -> environmentHrids.map(strings -> environment1.getHrids().stream().anyMatch(strings::contains)).orElse(true))
                    .toList().blockingGet().stream()).distinct().collect(Collectors.toList());
        } else if (environmentHrids.isPresent()) {
            environments = environmentRepository.findAll()
                    .filter(environment1 -> environment1.getHrids().stream().anyMatch(h -> environmentHrids.get().contains(h)))
                    .toList()
                    .blockingGet();
        }

        return environments;
    }

    private Optional<List<String>> getSystemValues(String key) {
        String systemPropertyEnvs = System.getProperty(key);
        String envs = systemPropertyEnvs == null ? configuration.getProperty(key) : systemPropertyEnvs;
        if (envs != null && !envs.isEmpty()) {
            return Optional.of(Arrays.asList(envs.split(SEPARATOR)));
        }
        return Optional.empty();
    }
}