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

package io.gravitee.am.gateway.handler.oidc.service.clientregistration.impl;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.gravitee.am.common.utils.SecureRandomString;
import io.gravitee.am.gateway.handler.oidc.service.clientregistration.ClientSecretService;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.application.ApplicationSecretSettings;
import io.gravitee.am.model.application.ClientSecret;
import io.gravitee.am.service.exception.ApplicationSecretConfigurationException;
import io.gravitee.am.service.impl.SecretService;
import io.gravitee.am.service.spring.application.SecretHashAlgorithm;
import io.micrometer.common.util.StringUtils;

public class ClientSecretServiceImpl implements ClientSecretService {

    @Autowired
    private SecretService secretService;

    @Override
    public Optional<ClientSecret> determineClientSecret(Client client) {
        List<ClientSecret> clientSecrets = client.getClientSecrets();
        if (client.getClientSecrets() == null) {
            return Optional.empty();
        }

        if (StringUtils.isNotBlank(client.getClientSecret())) {
            Optional<ClientSecret> clientSecretFromSecret = client.getClientSecrets().stream()
                    .filter(cs -> cs.getSecret().equals(client.getClientSecret())).findFirst();
            if (clientSecretFromSecret.isPresent()) {
                return clientSecretFromSecret;
            }
        }

        return clientSecrets.stream()
                .filter(cs -> client.getSecretSettings().stream()
                        .anyMatch(setting -> setting.getId().equals(cs.getSettingsId()) &&
                                SecretHashAlgorithm.NONE.name().equalsIgnoreCase(setting.getAlgorithm())))
                .findFirst();
    }

    @Override
    public String getSecretId(Client client, Optional<ClientSecret> clientSecretToRenew, Domain domain) {
        // Mapping client secret to list to handle the rotation.
        if (clientSecretToRenew.isPresent()) {
            return clientSecretToRenew.get().getId();
        }

        ApplicationSecretSettings noneSettings;
        if (client.getSecretSettings() == null || client.getSecretSettings().isEmpty()
                || client.getSecretSettings().stream().noneMatch(
                        setting -> setting.getAlgorithm().equalsIgnoreCase(SecretHashAlgorithm.NONE.name()))) {
            noneSettings = buildNoneSecretSettings();
            client.setSecretSettings(new ArrayList<>(List.of(noneSettings)));
        } else {
            noneSettings = client.getSecretSettings().stream()
                    .filter(setting -> setting.getAlgorithm().equalsIgnoreCase(SecretHashAlgorithm.NONE.name()))
                    .findFirst().get();
        }

        String rawClientSecret = StringUtils.isNotBlank(client.getClientSecret()) ? client.getClientSecret()
                : SecureRandomString.generate();
        clientSecretToRenew = Optional.of(secretService.generateClientSecret("Default", rawClientSecret, noneSettings,
                domain.getSecretExpirationSettings(), client.getSecretExpirationSettings()));

        client.setClientSecrets(new ArrayList<>(List.of(clientSecretToRenew.get())));
        return clientSecretToRenew.get().getId();
    }

    private ApplicationSecretSettings buildNoneSecretSettings() {
        try {
            ObjectMapper om = new ObjectMapper();
            SecretHashAlgorithm noneAlg = SecretHashAlgorithm.NONE;
            Map<String, Object> noProperties = Map.of();
            final var serializedConfig = om.writeValueAsString(List.of(noneAlg, noProperties));
            final var id = Base64.getEncoder()
                    .encodeToString(MessageDigest.getInstance("SHA-256").digest(serializedConfig.getBytes()));
            return new ApplicationSecretSettings(id, noneAlg.name(), noProperties);
        } catch (JsonProcessingException | NoSuchAlgorithmException e) {
            throw new ApplicationSecretConfigurationException(e);
        }
    }
}
