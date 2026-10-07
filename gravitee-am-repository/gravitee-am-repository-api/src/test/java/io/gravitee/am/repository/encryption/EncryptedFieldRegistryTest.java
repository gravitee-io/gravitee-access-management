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
import io.gravitee.am.repository.management.api.DomainRepository;
import io.gravitee.am.repository.management.api.ExtensionGrantRepository;
import io.gravitee.am.repository.management.api.FactorRepository;
import io.gravitee.am.repository.management.api.IdentityProviderRepository;
import io.gravitee.am.repository.management.api.ReporterRepository;
import io.gravitee.am.repository.management.api.ServiceResourceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class EncryptedFieldRegistryTest {

    private static final FieldEncryptor ENCRYPTOR = FieldEncryptor.withKeys(List.of(new FieldEncryptor.EncryptionKey("2025", "a-secret")));
    private static final String CONFIGURATION = "{\"password\":\"s3cr3t\"}";

    static Stream<Arguments> repositories() {
        return Stream.of(
                Arguments.of(ReporterRepository.class, Reporter.class),
                Arguments.of(IdentityProviderRepository.class, IdentityProvider.class),
                Arguments.of(AlertNotifierRepository.class, AlertNotifier.class),
                Arguments.of(AuthenticationDeviceNotifierRepository.class, AuthenticationDeviceNotifier.class),
                Arguments.of(BotDetectionRepository.class, BotDetection.class),
                Arguments.of(CertificateRepository.class, Certificate.class),
                Arguments.of(DataPlaneDefinitionRepository.class, DataPlaneDefinition.class),
                Arguments.of(DeviceIdentifierRepository.class, DeviceIdentifier.class),
                Arguments.of(ExtensionGrantRepository.class, ExtensionGrant.class),
                Arguments.of(FactorRepository.class, Factor.class),
                Arguments.of(ServiceResourceRepository.class, ServiceResource.class)
        );
    }

    @ParameterizedTest
    @MethodSource("repositories")
    void should_register_the_entity_of_the_repository(Class<?> repository, Class<?> entity) {
        assertThat(EncryptedFieldRegistry.forRepository(repository))
                .hasValueSatisfying(field -> assertThat(field.type()).isEqualTo(entity));
    }

    @ParameterizedTest
    @MethodSource("repositories")
    void should_encrypt_a_copy_and_decrypt_back(Class<?> repository, Class<?> entity) throws Exception {
        EncryptedField<?> field = EncryptedFieldRegistry.forRepository(repository).orElseThrow();
        Object original = populated(entity);

        Object copy = field.encryptedCopy(original, ENCRYPTOR);

        assertThat(copy).isNotSameAs(original);
        assertThat(configurationOf(original)).isEqualTo(CONFIGURATION);
        assertThat(configurationOf(copy)).startsWith("enc:2025:");

        field.decryptInPlace(copy, ENCRYPTOR);
        assertThat(configurationOf(copy)).isEqualTo(CONFIGURATION);
    }

    /**
     * The proxy stores the copy, so a field the copy constructor forgets is lost on every create and update.
     */
    @ParameterizedTest
    @MethodSource("repositories")
    void should_copy_every_field(Class<?> repository, Class<?> entity) throws Exception {
        EncryptedField<?> field = EncryptedFieldRegistry.forRepository(repository).orElseThrow();
        Object original = populated(entity);

        Object copy = field.encryptedCopy(original, ENCRYPTOR);

        for (Field declared : instanceFields(entity)) {
            if (declared.getName().equals("configuration")) {
                continue;
            }
            assertThat(declared.get(copy))
                    .as("%s.%s", entity.getSimpleName(), declared.getName())
                    .isEqualTo(declared.get(original));
        }
    }

    @Test
    void should_ignore_repositories_without_encrypted_field() {
        assertThat(EncryptedFieldRegistry.forRepository(DomainRepository.class)).isEmpty();
    }

    private static String configurationOf(Object entity) throws Exception {
        Field field = findField(entity.getClass(), "configuration");
        return (String) field.get(entity);
    }

    private static Object populated(Class<?> type) throws Exception {
        Object instance = type.getDeclaredConstructor().newInstance();
        for (Field field : instanceFields(type)) {
            Object value = field.getName().equals("configuration") ? CONFIGURATION : sampleValue(field);
            if (value != null) {
                field.set(instance, value);
            }
        }
        return instance;
    }

    private static Object sampleValue(Field field) {
        Class<?> type = field.getType();
        if (type == String.class) {
            return field.getName() + "-value";
        }
        if (type == boolean.class || type == Boolean.class) {
            return true;
        }
        if (type == int.class || type == Integer.class) {
            return 42;
        }
        if (type == long.class || type == Long.class) {
            return 42L;
        }
        if (type == Date.class) {
            return new Date(1_000L);
        }
        if (type.isEnum()) {
            return type.getEnumConstants()[type.getEnumConstants().length - 1];
        }
        if (type == List.class) {
            return new ArrayList<>(List.of());
        }
        if (type == Set.class) {
            return new HashSet<>(Set.of());
        }
        if (type == Map.class) {
            return new HashMap<>(Map.of());
        }
        try {
            return type.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static List<Field> instanceFields(Class<?> type) {
        List<Field> fields = new ArrayList<>();
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers()) && !field.isSynthetic()) {
                    field.setAccessible(true);
                    fields.add(field);
                }
            }
        }
        return fields;
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        for (Field field : instanceFields(type)) {
            if (field.getName().equals(name)) {
                return field;
            }
        }
        throw new NoSuchFieldException(name);
    }
}
