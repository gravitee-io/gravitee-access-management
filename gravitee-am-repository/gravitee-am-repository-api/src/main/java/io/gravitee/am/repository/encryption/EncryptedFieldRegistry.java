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
package io.gravitee.am.repository.encryption;

import io.gravitee.am.model.AuthenticationDeviceNotifier;
import io.gravitee.am.model.BotDetection;
import io.gravitee.am.model.Certificate;
import io.gravitee.am.model.DataPlaneDefinition;
import io.gravitee.am.model.DeviceIdentifier;
import io.gravitee.am.model.ExtensionGrant;
import io.gravitee.am.model.Factor;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.model.Reporter;
import io.gravitee.am.model.alert.AlertNotifier;
import io.gravitee.am.model.resource.ServiceResource;
import io.gravitee.am.repository.management.api.AlertNotifierRepository;
import io.gravitee.am.repository.management.api.AuthenticationDeviceNotifierRepository;
import io.gravitee.am.repository.management.api.BotDetectionRepository;
import io.gravitee.am.repository.management.api.CertificateRepository;
import io.gravitee.am.repository.management.api.DataPlaneDefinitionRepository;
import io.gravitee.am.repository.management.api.DeviceIdentifierRepository;
import io.gravitee.am.repository.management.api.ExtensionGrantRepository;
import io.gravitee.am.repository.management.api.FactorRepository;
import io.gravitee.am.repository.management.api.IdentityProviderRepository;
import io.gravitee.am.repository.management.api.ReporterRepository;
import io.gravitee.am.repository.management.api.ServiceResourceRepository;

import java.util.Map;
import java.util.Optional;

import static java.util.Map.entry;

/**
 * Lists the repositories whose entities have an encrypted field.
 *
 * @author GraviteeSource Team
 */
public final class EncryptedFieldRegistry {

    private static final Map<Class<?>, EncryptedField<?>> FIELDS = Map.ofEntries(
            entry(ReporterRepository.class, new EncryptedField<>(Reporter.class, Reporter::getConfiguration, Reporter::setConfiguration, Reporter::new)),
            entry(IdentityProviderRepository.class, new EncryptedField<>(IdentityProvider.class, IdentityProvider::getConfiguration, IdentityProvider::setConfiguration, IdentityProvider::new)),
            entry(AlertNotifierRepository.class, new EncryptedField<>(AlertNotifier.class, AlertNotifier::getConfiguration, AlertNotifier::setConfiguration, AlertNotifier::new)),
            entry(AuthenticationDeviceNotifierRepository.class, new EncryptedField<>(AuthenticationDeviceNotifier.class, AuthenticationDeviceNotifier::getConfiguration, AuthenticationDeviceNotifier::setConfiguration, AuthenticationDeviceNotifier::new)),
            entry(BotDetectionRepository.class, new EncryptedField<>(BotDetection.class, BotDetection::getConfiguration, BotDetection::setConfiguration, BotDetection::new)),
            entry(CertificateRepository.class, new EncryptedField<>(Certificate.class, Certificate::getConfiguration, Certificate::setConfiguration, Certificate::new)),
            entry(DataPlaneDefinitionRepository.class, new EncryptedField<>(DataPlaneDefinition.class, DataPlaneDefinition::getConfiguration, DataPlaneDefinition::setConfiguration, DataPlaneDefinition::new)),
            entry(DeviceIdentifierRepository.class, new EncryptedField<>(DeviceIdentifier.class, DeviceIdentifier::getConfiguration, DeviceIdentifier::setConfiguration, DeviceIdentifier::new)),
            entry(ExtensionGrantRepository.class, new EncryptedField<>(ExtensionGrant.class, ExtensionGrant::getConfiguration, ExtensionGrant::setConfiguration, ExtensionGrant::new)),
            entry(FactorRepository.class, new EncryptedField<>(Factor.class, Factor::getConfiguration, Factor::setConfiguration, Factor::new)),
            entry(ServiceResourceRepository.class, new EncryptedField<>(ServiceResource.class, ServiceResource::getConfiguration, ServiceResource::setConfiguration, ServiceResource::new))
    );

    private EncryptedFieldRegistry() {
    }

    /**
     * @return the encrypted field of the entity managed by {@code repositoryClass}, if that class implements a registered repository
     */
    public static Optional<EncryptedField<?>> forRepository(Class<?> repositoryClass) {
        return FIELDS.entrySet().stream()
                .filter(entry -> entry.getKey().isAssignableFrom(repositoryClass))
                .<EncryptedField<?>>map(Map.Entry::getValue)
                .findFirst();
    }
}
