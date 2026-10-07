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
package io.gravitee.am.repository.management;

import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.model.AuthenticationDeviceNotifier;
import io.gravitee.am.model.BotDetection;
import io.gravitee.am.model.Certificate;
import io.gravitee.am.model.DataPlaneDefinition;
import io.gravitee.am.model.DeviceIdentifier;
import io.gravitee.am.model.ExtensionGrant;
import io.gravitee.am.model.Factor;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.Reporter;
import io.gravitee.am.model.alert.AlertNotifier;
import io.gravitee.am.model.resource.ServiceResource;
import io.gravitee.am.repository.common.CrudRepository;
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
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Date;
import java.util.function.Function;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Every entity whose configuration is encrypted is stored with the {@code enc:<key id>:} prefix, and read back in
 * clear text. Each backend reads the stored value without going through the repository.
 *
 * @author GraviteeSource Team
 */
public abstract class AbstractConfigurationEncryptionTest extends AbstractManagementTest {

    private static final String SECRET_VALUE = "s3cr3t";
    private static final String CONFIGURATION = "{\"password\":\"" + SECRET_VALUE + "\"}";
    private static final String DOMAIN = "domain-encryption";

    @Autowired
    private ReporterRepository reporterRepository;
    @Autowired
    private IdentityProviderRepository identityProviderRepository;
    @Autowired
    private AlertNotifierRepository alertNotifierRepository;
    @Autowired
    private AuthenticationDeviceNotifierRepository authenticationDeviceNotifierRepository;
    @Autowired
    private BotDetectionRepository botDetectionRepository;
    @Autowired
    private CertificateRepository certificateRepository;
    @Autowired
    private DataPlaneDefinitionRepository dataPlaneDefinitionRepository;
    @Autowired
    private DeviceIdentifierRepository deviceIdentifierRepository;
    @Autowired
    private ExtensionGrantRepository extensionGrantRepository;
    @Autowired
    private FactorRepository factorRepository;
    @Autowired
    private ServiceResourceRepository serviceResourceRepository;

    /**
     * @return the configuration as stored in {@code table} (collection) for the row (document) {@code id}
     */
    protected abstract String storedConfiguration(String table, String id);

    @Test
    public void shouldEncryptReporterConfiguration() {
        Reporter reporter = new Reporter();
        reporter.setId(RandomString.generate());
        reporter.setName("reporter");
        reporter.setType("mongodb");
        reporter.setReference(Reference.domain(DOMAIN));
        reporter.setConfiguration(CONFIGURATION);
        assertEncrypted(reporterRepository, reporter, "reporters", Reporter::getId, Reporter::getConfiguration);
    }

    @Test
    public void shouldEncryptIdentityProviderConfiguration() {
        IdentityProvider provider = new IdentityProvider();
        provider.setId(RandomString.generate());
        provider.setName("idp");
        provider.setType("ldap");
        provider.setReferenceType(ReferenceType.DOMAIN);
        provider.setReferenceId(DOMAIN);
        provider.setConfiguration(CONFIGURATION);
        assertEncrypted(identityProviderRepository, provider, "identities", IdentityProvider::getId, IdentityProvider::getConfiguration);
    }

    @Test
    public void shouldEncryptAlertNotifierConfiguration() {
        AlertNotifier notifier = new AlertNotifier();
        notifier.setId(RandomString.generate());
        notifier.setEnabled(true);
        notifier.setName("alert-notifier");
        notifier.setType("webhook");
        notifier.setReferenceType(ReferenceType.DOMAIN);
        notifier.setReferenceId(DOMAIN);
        notifier.setConfiguration(CONFIGURATION);
        notifier.setCreatedAt(new Date());
        notifier.setUpdatedAt(new Date());
        assertEncrypted(alertNotifierRepository, notifier, "alert_notifiers", AlertNotifier::getId, AlertNotifier::getConfiguration);
    }

    @Test
    public void shouldEncryptAuthenticationDeviceNotifierConfiguration() {
        AuthenticationDeviceNotifier notifier = new AuthenticationDeviceNotifier();
        notifier.setId(RandomString.generate());
        notifier.setName("auth-device-notifier");
        notifier.setType("http");
        notifier.setReferenceType(ReferenceType.DOMAIN);
        notifier.setReferenceId(DOMAIN);
        notifier.setConfiguration(CONFIGURATION);
        notifier.setCreatedAt(new Date());
        notifier.setUpdatedAt(new Date());
        assertEncrypted(authenticationDeviceNotifierRepository, notifier, "authentication_device_notifiers",
                AuthenticationDeviceNotifier::getId, AuthenticationDeviceNotifier::getConfiguration);
    }

    @Test
    public void shouldEncryptBotDetectionConfiguration() {
        BotDetection botDetection = new BotDetection();
        botDetection.setId(RandomString.generate());
        botDetection.setName("bot-detection");
        botDetection.setType("recaptcha");
        botDetection.setDetectionType("CAPTCHA");
        botDetection.setReferenceType(ReferenceType.DOMAIN);
        botDetection.setReferenceId(DOMAIN);
        botDetection.setConfiguration(CONFIGURATION);
        botDetection.setCreatedAt(new Date());
        botDetection.setUpdatedAt(new Date());
        assertEncrypted(botDetectionRepository, botDetection, "bot_detections", BotDetection::getId, BotDetection::getConfiguration);
    }

    @Test
    public void shouldEncryptCertificateConfiguration() {
        Certificate certificate = new Certificate();
        certificate.setId(RandomString.generate());
        certificate.setName("certificate");
        certificate.setType("PEM");
        certificate.setDomain(DOMAIN);
        certificate.setConfiguration(CONFIGURATION);
        certificate.setCreatedAt(new Date());
        certificate.setUpdatedAt(new Date());
        assertEncrypted(certificateRepository, certificate, "certificates", Certificate::getId, Certificate::getConfiguration);
    }

    @Test
    public void shouldEncryptDataPlaneDefinitionConfiguration() {
        DataPlaneDefinition definition = new DataPlaneDefinition();
        definition.setId(RandomString.generate());
        definition.setName("data-plane");
        definition.setType("mongodb");
        definition.setGatewayUrl("https://gateway.example.com");
        definition.setOrganizationId("DEFAULT");
        definition.setEnvironmentId("DEFAULT");
        definition.setConfiguration(CONFIGURATION);
        definition.setCreatedAt(new Date());
        definition.setUpdatedAt(new Date());
        assertEncrypted(dataPlaneDefinitionRepository, definition, "dataplanes", DataPlaneDefinition::getId, DataPlaneDefinition::getConfiguration);
    }

    @Test
    public void shouldEncryptDeviceIdentifierConfiguration() {
        DeviceIdentifier deviceIdentifier = new DeviceIdentifier();
        deviceIdentifier.setId(RandomString.generate());
        deviceIdentifier.setName("device-identifier");
        deviceIdentifier.setType("fingerprintjs");
        deviceIdentifier.setReferenceType(ReferenceType.DOMAIN);
        deviceIdentifier.setReferenceId(DOMAIN);
        deviceIdentifier.setConfiguration(CONFIGURATION);
        deviceIdentifier.setCreatedAt(new Date());
        deviceIdentifier.setUpdatedAt(new Date());
        assertEncrypted(deviceIdentifierRepository, deviceIdentifier, "device_identifiers", DeviceIdentifier::getId, DeviceIdentifier::getConfiguration);
    }

    @Test
    public void shouldEncryptExtensionGrantConfiguration() {
        ExtensionGrant extensionGrant = new ExtensionGrant();
        extensionGrant.setId(RandomString.generate());
        extensionGrant.setName("extension-grant");
        extensionGrant.setType("jwtbearer");
        extensionGrant.setDomain(DOMAIN);
        extensionGrant.setGrantType("urn:ietf:params:oauth:grant-type:jwt-bearer");
        extensionGrant.setConfiguration(CONFIGURATION);
        extensionGrant.setCreatedAt(new Date());
        extensionGrant.setUpdatedAt(new Date());
        assertEncrypted(extensionGrantRepository, extensionGrant, "extension_grants", ExtensionGrant::getId, ExtensionGrant::getConfiguration);
    }

    @Test
    public void shouldEncryptFactorConfiguration() {
        Factor factor = new Factor();
        factor.setId(RandomString.generate());
        factor.setName("factor");
        factor.setType("otp");
        factor.setFactorType("TOTP");
        factor.setDomain(DOMAIN);
        factor.setConfiguration(CONFIGURATION);
        factor.setCreatedAt(new Date());
        factor.setUpdatedAt(new Date());
        assertEncrypted(factorRepository, factor, "factors", Factor::getId, Factor::getConfiguration);
    }

    @Test
    public void shouldEncryptServiceResourceConfiguration() {
        ServiceResource resource = new ServiceResource();
        resource.setId(RandomString.generate());
        resource.setName("resource");
        resource.setType("smtp");
        resource.setReferenceType(ReferenceType.DOMAIN);
        resource.setReferenceId(DOMAIN);
        resource.setConfiguration(CONFIGURATION);
        resource.setCreatedAt(new Date());
        resource.setUpdatedAt(new Date());
        assertEncrypted(serviceResourceRepository, resource, "service_resources", ServiceResource::getId, ServiceResource::getConfiguration);
    }

    private <T> void assertEncrypted(CrudRepository<T, String> repository, T entity, String table,
                                     Function<T, String> id, Function<T, String> configuration) {
        T created = repository.create(entity).blockingGet();
        assertEquals(CONFIGURATION, configuration.apply(created));
        assertEquals("the caller instance must stay in clear text", CONFIGURATION, configuration.apply(entity));
        assertStoredEncrypted(table, id.apply(created));

        T updated = repository.update(created).blockingGet();
        assertEquals(CONFIGURATION, configuration.apply(updated));
        assertStoredEncrypted(table, id.apply(created));

        assertEquals(CONFIGURATION, configuration.apply(repository.findById(id.apply(created)).blockingGet()));
    }

    private void assertStoredEncrypted(String table, String id) {
        String stored = storedConfiguration(table, id);
        assertTrue(table + " stores " + stored, stored.startsWith("enc:repository-tests:"));
        assertFalse(stored.contains(SECRET_VALUE));
    }
}
