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
package io.gravitee.am.model;

import io.gravitee.am.model.application.ApplicationSecretSettings;
import io.gravitee.am.model.application.ApplicationSettings;
import io.gravitee.am.model.application.ApplicationType;
import io.gravitee.am.model.application.ClientSecret;
import io.gravitee.am.model.idp.ApplicationIdentityProvider;
import io.gravitee.am.model.oidc.Client;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.stream.Collectors;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Application implements Resource, PasswordSettingsAware {

    /**
     * Application technical id
     */
    private String id;
    /**
     * Application name
     */
    private String name;
    /**
     * Application type
     */
    private ApplicationType type;
    /**
     * Application description
     */
    private String description;
    /**
     * Agent Card URL for AGENT type applications
     */
    private String agentCardUrl;
    /**
     * Security domain associated to the application
     */
    private String domain;
    /**
     * Application state
     */
    private boolean enabled = true;
    /**
     * Boolean value specifying whether the application should be use as a registration template or active application
     */
    private boolean template;
    /**
     * Deprecated since 4.3
     * Instead use factorSettings
     * Factors used for authentication
     */
    @Deprecated
    private Set<String> factors;
    /**
     * Certificate use to sign the tokens
     */
    private String certificate;
    /**
     * Application metadata
     */
    private Map<String, Object> metadata;
    /**
     * Application settings
     */
    private ApplicationSettings settings;
    /**
     * Identity Provider Settings
     */
    private SortedSet<ApplicationIdentityProvider> identityProviders;
    /**
     * Application created date
     */
    @Schema(type = "java.lang.Long")
    private Date createdAt;
    /**
     * Application updated date
     */
    @Schema(type = "java.lang.Long")
    private Date updatedAt;

    private List<ApplicationSecretSettings> secretSettings;

    private List<ClientSecret> secrets;

    public Application(Application other) {
        this.id = other.id;
        this.name = other.name;
        this.type = other.type;
        this.description = other.description;
        this.agentCardUrl = other.agentCardUrl;
        this.domain = other.domain;
        this.enabled = other.enabled;
        this.template = other.template;
        this.factors = other.factors;
        this.certificate = other.certificate;
        this.metadata = other.metadata != null ? new HashMap<>(other.metadata) : null;
        this.settings = other.settings != null ? new ApplicationSettings(other.settings) : null;
        this.identityProviders = other.identityProviders;
        this.createdAt = other.createdAt;
        this.updatedAt = other.updatedAt;
        this.secretSettings = other.secretSettings;
        this.secrets = other.getSecrets().stream().map(ClientSecret::new).collect(Collectors.toList());
    }

    public List<ClientSecret> getSecrets() {
        if (secrets == null) {
            this.secrets = new ArrayList<>();
        }
        return secrets;
    }

    public Client toClient() {
        Client client = new Client();
        client.setId(this.id);
        client.setDomain(this.domain);
        client.setEnabled(this.enabled);
        client.setTemplate(this.template);
        client.setCertificate(this.certificate);
        client.setIdentityProviders(this.identityProviders);
        client.setFactors(this.factors);
        Optional.ofNullable(settings).map(ApplicationSettings::getMfa).map(MFASettings::getFactor).ifPresent(client::setFactorSettings);
        client.setMetadata(this.metadata);
        client.setCreatedAt(this.createdAt);
        client.setUpdatedAt(this.updatedAt);
        Optional.ofNullable(settings).ifPresent(s -> s.copyTo(client));
        client.setSecretSettings(this.secretSettings);
        client.setClientSecrets(this.getSecrets());
        return client;
    }

    @Override
    public PasswordSettings getPasswordSettings() {
        return Optional.ofNullable(settings).map(ApplicationSettings::getPasswordSettings).orElse(null);
    }
}
