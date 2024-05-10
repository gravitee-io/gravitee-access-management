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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Maps;
import com.nimbusds.jose.crypto.bc.BouncyCastleProviderSingleton;
import io.gravitee.am.certificate.api.CertificateMetadata;
import io.gravitee.am.common.audit.EventType;
import io.gravitee.am.common.event.Action;
import io.gravitee.am.common.event.Type;
import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.model.Certificate;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.plugins.certificate.core.CertificatePluginManager;
import io.gravitee.am.plugins.certificate.core.CertificateProviderConfiguration;
import io.gravitee.am.plugins.certificate.core.schema.CertificateSchema;
import io.gravitee.am.repository.management.api.CertificateRepository;
import io.gravitee.am.service.ApplicationService;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.CertificatePluginService;
import io.gravitee.am.service.CertificateService;
import io.gravitee.am.service.EventService;
import io.gravitee.am.service.IdentityProviderService;
import io.gravitee.am.service.TaskManager;
import io.gravitee.am.service.exception.AbstractManagementException;
import io.gravitee.am.service.exception.CertificateNotFoundException;
import io.gravitee.am.service.exception.CertificatePluginSchemaNotFoundException;
import io.gravitee.am.service.exception.CertificateWithApplicationsException;
import io.gravitee.am.service.exception.CertificateWithIdpException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.model.NewCertificate;
import io.gravitee.am.service.model.UpdateCertificate;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.management.CertificateAuditBuilder;
import io.gravitee.am.service.tasks.AssignSystemCertificate;
import io.gravitee.am.service.tasks.AssignSystemCertificateDefinition;
import io.gravitee.am.service.utils.CertificateTimeComparator;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.core.SingleSource;
import io.reactivex.rxjava3.functions.Function;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static io.gravitee.am.certificate.api.ConfigurationCertUtils.usageContains;
import static io.gravitee.am.identityprovider.api.oidc.OpenIDConnectConfigurationUtils.extractCertificateId;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
@Primary
public class CertificateServiceImpl implements CertificateService {
    public static final String DEFAULT_CERTIFICATE_PLUGIN = "pkcs12-am-certificate";

    public static final String ECDSA = "ECDSA";
    public static final String DEFAULT_CERT_CN_NAME = "cn=Gravitee.io";
    public static final String DEFAULT_CERT_ALGO = "SHA256withRSA";
    public static final String DEFAULT_CERT_PWD = "gravitee";
    public static final int DEFAULT_CERT_KEYSIZE = 2048;
    public static final int DEFAULT_CERT_VALIDITY_IN_DAYS = 365;
    public static final String DEFAULT_CERT_ALIAS = "default";
    /**
     * Logger.
     */
    private final Logger LOGGER = LoggerFactory.getLogger(CertificateServiceImpl.class);
    private static final String RSA = "RSA";
    private static final String EC = "EC";
    private static final String CONTENT = "content";
    private static final String STORE_PASS = "storepass";
    private static final String ALIAS = "alias";
    private static final String NAME = "name";

    @Lazy
    @Autowired
    private CertificateRepository certificateRepository;

    @Autowired
    private ApplicationService applicationService;

    @Autowired
    private IdentityProviderService identityProviderService;

    @Autowired
    private EventService eventService;

    @Autowired
    private AuditService auditService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CertificatePluginService certificatePluginService;

    @Autowired
    private CertificatePluginManager certificatePluginManager;

    @Autowired
    private Environment environment;

    @Autowired
    private TaskManager taskManager;

    @Value("${domains.certificates.default.refresh.delay:10}")
    private int delay;

    @Value("${domains.certificates.default.refresh.timeUnit:MINUTES}")
    private String timeUnit;

    @Override
    public Maybe<Certificate> findById(String id) {
        LOGGER.debug("Find certificate by ID: {}", id);
        return certificateRepository.findById(id)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find a certificate using its ID: {}", id, ex);
                    return Maybe.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find a certificate using its ID: %s", id), ex));
                });
    }

    @Override
    public Flowable<Certificate> findByDomain(String domain) {
        return innerFindByDomain(domain);
    }

    private Flowable<Certificate> innerFindByDomain(String domain) {
        LOGGER.debug("Find certificates by domain: {}", domain);
        return certificateRepository.findByDomain(domain)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find certificates by domain", ex);
                    return Flowable.error(new TechnicalManagementException("An error occurs while trying to find certificates by domain", ex));
                });
    }

    @Override
    public Flowable<Certificate> findAll() {
        LOGGER.debug("Find all certificates");
        return certificateRepository.findAll()
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find all certificates", ex);
                    return Flowable.error(new TechnicalManagementException("An error occurs while trying to find all certificates by domain", ex));
                });
    }

    @Override
    public Single<Certificate> create(String domain, NewCertificate newCertificate, User principal, boolean isSystem) {
        LOGGER.debug("Create a new certificate {} for domain {}", newCertificate, domain);
        return certificatePluginService
                .getSchema(newCertificate.getType())
                .switchIfEmpty(Single.error(() -> new CertificatePluginSchemaNotFoundException(newCertificate.getType())))
                .map(schema -> objectMapper.readValue(schema, CertificateSchema.class))
                .flatMap(certificateSchema -> {
                    var certificate = new Certificate();
                    certificate.setId(RandomString.generate());
                    certificate.setDomain(domain);
                    certificate.setName(newCertificate.getName());
                    certificate.setType(newCertificate.getType());
                    certificate.setSystem(isSystem);
                    var certificateConfiguration = objectMapper.readTree(newCertificate.getConfiguration());
                    var fileKey = certificateSchema.getProperties()
                            .entrySet()
                            .stream()
                            .filter(map -> map.getValue().getWidget() != null && "file".equals(map.getValue().getWidget()))
                            .map(Map.Entry::getKey)
                            .findFirst().orElse(null);
                    if (fileKey == null || !certificateConfiguration.has(fileKey)) {
                        return Single.error(() -> new CertificateException("A valid certificate file was not uploaded. Please make sure to attach one."));
                    }
                    try {
                        var file = objectMapper.readTree(certificateConfiguration.get(fileKey).asText());
                        var fileContent = Base64.getDecoder().decode(file.get(CONTENT).asText());
                        certificate.setMetadata(Maps.newHashMap(Map.of(CertificateMetadata.FILE, fileContent)));
                        // update configuration to set the file name
                        ((ObjectNode) certificateConfiguration).put(fileKey, file.get(NAME).asText());
                        newCertificate.setConfiguration(objectMapper.writeValueAsString(certificateConfiguration));
                        certificate.setConfiguration(newCertificate.getConfiguration());
                        certificate.setCreatedAt(new Date());
                        certificate.setUpdatedAt(certificate.getCreatedAt());
                        return Single.just(certificate);
                    } catch (Exception ex) {
                        LOGGER.error("An error occurs while trying to create certificate configuration", ex);
                        return Single.error(() -> new TechnicalManagementException("An error occurs while trying to create a certificate", ex));
                    }
                })
                .map(this::getValid)
                .flatMap(certificate -> certificateRepository.create(certificate))
                // create event for sync process
                .flatMap(certificate -> {
                    Event event = new Event(Type.CERTIFICATE, new Payload(certificate.getId(), ReferenceType.DOMAIN, certificate.getDomain(), Action.CREATE));
                    return eventService.create(event).flatMap(__ -> Single.just(certificate));
                })
                .doOnError(ex -> LOGGER.error("An error occurs while trying to create a certificate", ex));
    }

    private static class CertificateWithSchema {
        private final Certificate certificate;
        private final CertificateSchema schema;

        public CertificateWithSchema(Certificate certificate, CertificateSchema schema) {
            this.certificate = certificate;
            this.schema = schema;
        }

        public Certificate getCertificate() {
            return certificate;
        }

        public CertificateSchema getSchema() {
            return schema;
        }
    }

    @Override
    public Single<Certificate> update(String domain, String id, UpdateCertificate updateCertificate, User principal) {
        LOGGER.debug("Update a certificate {} for domain {}", id, domain);
        return certificateRepository.findById(id)
                .switchIfEmpty(Single.error(() -> new CertificateNotFoundException(id)))
                .flatMap((Function<Certificate, SingleSource<CertificateWithSchema>>) certificate -> certificatePluginService.getSchema(certificate.getType())
                        .switchIfEmpty(Single.error(() -> new CertificatePluginSchemaNotFoundException(certificate.getType())))
                        .flatMap(schema -> Single.just(new CertificateWithSchema(certificate, objectMapper.readValue(schema, CertificateSchema.class)))))
                .flatMap(oldCertificate -> {
                    boolean oldWithMTls = usageContains(oldCertificate.getCertificate().getConfiguration(), "mtls");
                    boolean newWithMTls = usageContains(updateCertificate.getConfiguration(), "mtls");
                    if(oldWithMTls && !newWithMTls){
                        return checkIdentityProviderUsage(oldCertificate.getCertificate())
                                .map(cert -> oldCertificate);
                    } else {
                        return Single.just(oldCertificate);
                    }
                })
                .flatMap(oldCertificate -> {
                    Certificate certificateToUpdate = new Certificate(oldCertificate.getCertificate());
                    certificateToUpdate.setName(updateCertificate.getName());
                    certificateToUpdate.setUpdatedAt(new Date());
                    if (!certificateToUpdate.isSystem()) { // system certificate can't be updated
                        try {
                            CertificateSchema certificateSchema = oldCertificate.getSchema();
                            JsonNode oldCertificateConfiguration = objectMapper.readTree(oldCertificate.getCertificate().getConfiguration());
                            JsonNode certificateConfiguration = objectMapper.readTree(updateCertificate.getConfiguration());
                            var key = certificateSchema.getProperties()
                                    .entrySet()
                                    .stream()
                                    .filter(map -> map.getValue().getWidget() != null && "file".equals(map.getValue().getWidget()))
                                    .map(Map.Entry::getKey)
                                    .findFirst().orElse(null);
                            String oldFileInformation = oldCertificateConfiguration.get(key).asText();
                            String fileInformation = certificateConfiguration.get(key).asText();
                            // file has changed, let's update it
                            if (!oldFileInformation.equals(fileInformation)) {
                                JsonNode file = objectMapper.readTree(certificateConfiguration.get(key).asText());
                                byte[] data = Base64.getDecoder().decode(file.get(CONTENT).asText());
                                certificateToUpdate.setMetadata(Collections.singletonMap(CertificateMetadata.FILE, data));

                                // update configuration to set the file path
                                ((ObjectNode) certificateConfiguration).put(key, file.get(NAME).asText());
                                updateCertificate.setConfiguration(objectMapper.writeValueAsString(certificateConfiguration));
                            }
                            certificateToUpdate.setConfiguration(updateCertificate.getConfiguration());
                        } catch (IOException ex) {
                            LOGGER.error("An error occurs while trying to update certificate binaries", ex);
                            return Single.error(() -> ex);
                        } catch (Exception ex) {
                            LOGGER.error("An error occurs while trying to update certificate configuration", ex);
                            return Single.error(() -> ex);
                        }
                    }
                    return Single.just(certificateToUpdate);
                })
                .map(this::getValid)
                .flatMap(certificate -> certificateRepository.update(certificate))
                // create event for sync process
                .flatMap(certificate1 -> {
                    Event event = new Event(Type.CERTIFICATE, new Payload(certificate1.getId(), ReferenceType.DOMAIN, certificate1.getDomain(), Action.UPDATE));
                    return eventService.create(event).flatMap(__ -> Single.just(certificate1));
                })
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to update a certificate", ex);
                    if(ex instanceof AbstractManagementException){
                        return Single.error(ex);
                    } else {
                        return Single.error(new TechnicalManagementException("An error occurs while trying to update a certificate", ex));
                    }
                });
    }

    private Single<Certificate> checkIdentityProviderUsage(Certificate certificate){
        return identityProviderService.findByDomain(certificate.getDomain())
                .filter(idp -> extractCertificateId(idp.getConfiguration())
                        .map(certId -> certId.equals(certificate.getId()))
                        .orElse(false))
                .count()
                .flatMap(count -> {
                    if (count > 0) {
                        return Single.error(new CertificateWithIdpException());
                    }
                    return Single.just(certificate);
                });
    }

    @Override
    public Completable delete(String certificateId, User principal) {
        LOGGER.debug("Delete certificate {}", certificateId);
        return certificateRepository.findById(certificateId)
                .switchIfEmpty(Maybe.error(new CertificateNotFoundException(certificateId)))
                .flatMapSingle(certificate -> applicationService.findByCertificate(certificateId).count()
                        .flatMap(applications -> {
                            if (applications > 0) {
                                return Single.error(new CertificateWithApplicationsException());
                            }
                            return Single.just(certificate);
                        })
                )
                .flatMapSingle(this::checkIdentityProviderUsage)
                .flatMapCompletable(certificate -> {
                    // create event for sync process
                    Event event = new Event(Type.CERTIFICATE, new Payload(certificate.getId(), ReferenceType.DOMAIN, certificate.getDomain(), Action.DELETE));
                    return Completable.fromSingle(certificateRepository.delete(certificateId)
                                    .andThen(eventService.create(event)))
                            .doOnComplete(() -> auditService.report(AuditBuilder.builder(CertificateAuditBuilder.class).principal(principal).type(EventType.CERTIFICATE_DELETED).certificate(certificate)))
                            .doOnError(throwable -> auditService.report(AuditBuilder.builder(CertificateAuditBuilder.class).principal(principal).type(EventType.CERTIFICATE_DELETED).throwable(throwable)));
                })
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to delete certificate: {}", certificateId, ex);
                    if (ex instanceof AbstractManagementException) {
                        return Completable.error(ex);
                    } else {
                        return Completable.error(new TechnicalManagementException(
                                String.format("An error occurs while trying to delete certificate: %s", certificateId), ex));
                    }
                });
    }

    @Override
    public Completable updateExpirationDate(String certificateId, Date expirationDate) {
        if (expirationDate == null) {
            LOGGER.warn("updateExpirationDate call with null for certificate '{}'", certificateId);
            return Completable.complete();
        }
        return this.certificateRepository.updateExpirationDate(certificateId, expirationDate);
    }

    @Override
    public Single<Certificate> create(String domain) {
        // Define the default certificate
        // Create a default PKCS12 certificate: io.gravitee.am.certificate.pkcs12.PKCS12Configuration
        NewCertificate certificate = new NewCertificate();
        certificate.setName("Default");

        // TODO: how-to handle default certificate type ?
        certificate.setType(DEFAULT_CERTIFICATE_PLUGIN);

        return certificatePluginService
                // Just to check that certificate is available
                .getSchema(certificate.getType())
                .switchIfEmpty(Single.error(new CertificatePluginSchemaNotFoundException(certificate.getType())))
                .map(schema -> objectMapper.readValue(schema, CertificateSchema.class))
                .map(certificateSchema -> {
                    final int keySize = environment.getProperty("domains.certificates.default.keysize", int.class, DEFAULT_CERT_KEYSIZE);
                    final int validity = environment.getProperty("domains.certificates.default.validity", int.class, DEFAULT_CERT_VALIDITY_IN_DAYS);
                    final String name = environment.getProperty("domains.certificates.default.name", String.class, DEFAULT_CERT_CN_NAME);
                    final String sigAlgName = environment.getProperty("domains.certificates.default.algorithm", String.class, DEFAULT_CERT_ALGO);
                    final String alias = environment.getProperty("domains.certificates.default.alias", String.class, DEFAULT_CERT_ALIAS);
                    final String keyPass = environment.getProperty("domains.certificates.default.keypass", String.class, DEFAULT_CERT_PWD);
                    final String storePass = environment.getProperty("domains.certificates.default.storepass", String.class, DEFAULT_CERT_PWD);

                    ByteArrayOutputStream generateCertificate = generateCertificate(sigAlgName, keySize, name, validity, alias, keyPass, storePass);

                    ObjectNode certificateNode = objectMapper.createObjectNode();

                    ObjectNode contentNode = objectMapper.createObjectNode();
                    contentNode.put(CONTENT, new String(Base64.getEncoder().encode(generateCertificate.toByteArray())));
                    contentNode.put(NAME, domain + ".p12");
                    certificateNode.put(CONTENT, objectMapper.writeValueAsString(contentNode));
                    certificateNode.put(ALIAS, alias);
                    certificateNode.put(STORE_PASS, storePass);
                    certificateNode.put("keypass", keyPass);

                    return objectMapper.writeValueAsString(certificateNode);
                }).flatMap(configuration -> {
                    certificate.setConfiguration(configuration);
                    return create(domain, certificate, true);
                });
    }

    @Override
    public Single<Certificate> rotate(String domain, User principal) {
        return this.innerFindByDomain(domain)// search using inner method to not go through the CertificateServiceProxy filtering
                .filter(Certificate::isSystem)
                .sorted(new CertificateTimeComparator())
                .firstElement()
                .map(Optional::ofNullable)
                .switchIfEmpty(Single.just(Optional.empty()))
                .flatMap(optCert -> {
                    if (optCert.isPresent()) {
                        final Certificate deprecatedCert = optCert.get();

                        final var now = LocalDateTime.now();
                        // Define the default certificate
                        // Create a default PKCS12 certificate: io.gravitee.am.certificate.pkcs12.PKCS12Configuration
                        NewCertificate rotatedCertificate = new NewCertificate();
                        rotatedCertificate.setName("Default " + DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(now));
                        rotatedCertificate.setType(DEFAULT_CERTIFICATE_PLUGIN);
                        rotatedCertificate.setConfiguration(generateCertificateConfiguration(domain, deprecatedCert.getConfiguration(), now));
                        return create(domain, rotatedCertificate, true).map(newCertificate -> {
                            final var task = new AssignSystemCertificate(applicationService, certificateRepository, taskManager);
                            final var definition = new AssignSystemCertificateDefinition(domain, newCertificate.getId(), deprecatedCert.getId());
                            definition.setDelay(delay);
                            definition.setUnit(TimeUnit.valueOf(timeUnit.toUpperCase()));
                            task.setDefinition(definition);
                            taskManager.schedule(task);
                            return newCertificate;
                        });
                    } else {
                        // If no certificate has been found, we still create a default certificate
                        return create(domain);
                    }
                })
                .doOnSuccess(certificate -> auditService.report(AuditBuilder.builder(CertificateAuditBuilder.class)
                        .principal(principal)
                        .referenceId(domain)
                        .referenceType(ReferenceType.DOMAIN)
                        .type(EventType.CERTIFICATE_CREATED)
                        .certificate(certificate)))
                .doOnError(error -> auditService.report(AuditBuilder.builder(CertificateAuditBuilder.class)
                        .principal(principal)
                        .referenceId(domain)
                        .referenceType(ReferenceType.DOMAIN)
                        .type(EventType.CERTIFICATE_CREATED).throwable(error)));
    }

    private String generateCertificateConfiguration(String domain, String previousConfig, LocalDateTime now) throws Exception {
        final var suffix = DateTimeFormatter.ofPattern("-yyyyMMddHHmmss").format(now);

        final int keySize = environment.getProperty("domains.certificates.default.keysize", int.class, DEFAULT_CERT_KEYSIZE);
        final int validity = environment.getProperty("domains.certificates.default.validity", int.class, DEFAULT_CERT_VALIDITY_IN_DAYS);
        final String name = environment.getProperty("domains.certificates.default.name", String.class, DEFAULT_CERT_CN_NAME);
        final String alias = environment.getProperty("domains.certificates.default.alias", String.class, DEFAULT_CERT_ALIAS) + suffix;
        final String sigAlgName = environment.getProperty("domains.certificates.default.algorithm", String.class, DEFAULT_CERT_ALGO);
        final String keyPass = environment.getProperty("domains.certificates.default.keypass", String.class, DEFAULT_CERT_PWD);
        final String storePass = environment.getProperty("domains.certificates.default.storepass", String.class, DEFAULT_CERT_PWD);

        ByteArrayOutputStream generateCertificate = generateCertificate(sigAlgName, keySize, name, validity, alias, keyPass, storePass);
        // create configuration based on the previous one
        // to keep settings that maybe set after the certificate creation
        ObjectNode certificateNode = objectMapper.readValue(previousConfig, ObjectNode.class);
        ObjectNode contentNode = objectMapper.createObjectNode();
        contentNode.put(CONTENT, new String(Base64.getEncoder().encode(generateCertificate.toByteArray())));
        contentNode.put(NAME, domain + suffix + ".p12");
        certificateNode.put(CONTENT, objectMapper.writeValueAsString(contentNode));
        certificateNode.put(ALIAS, alias);
        certificateNode.put(STORE_PASS, storePass);
        certificateNode.put("keypass", keyPass);

        return objectMapper.writeValueAsString(certificateNode);
    }

    private ByteArrayOutputStream generateCertificate(String sigAlgName, int keySize, String name, int validity, String alias, String keyPass, String storePass) throws GeneralSecurityException, IOException, OperatorCreationException {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(getAlgorithmCategory(sigAlgName));
        keyPairGenerator.initialize(keySize);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();
        java.security.cert.Certificate[] chain = {generateCertificate(name, keyPair, validity, sigAlgName)};
        KeyStore ks = KeyStore.getInstance("pkcs12");
        ks.load(null, null);
        ks.setKeyEntry(alias, keyPair.getPrivate(), keyPass.toCharArray(), chain);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ks.store(outputStream, storePass.toCharArray());
        return outputStream;
    }

    private String getAlgorithmCategory(String sigAlgName) {
        String category;
        if (sigAlgName.endsWith(RSA)) {
            category = RSA;
        } else if (sigAlgName.endsWith(ECDSA)) {
            category = EC;
        } else {
            throw new IllegalArgumentException("Unsupported signing algorithm");
        }
        return category;
    }

    private X509Certificate generateCertificate(String dn, KeyPair keyPair, int validity, String sigAlgName) throws GeneralSecurityException, IOException, OperatorCreationException {
        // Use appropriate signature algorithm based on your keyPair algorithm.
        String signatureAlgorithm = sigAlgName;

        X500Name dnName = new X500Name(dn);
        Date from = new Date();
        Date to = new Date(from.getTime() + validity * 1000L * 24L * 60L * 60L);

        // Using the current timestamp as the certificate serial number
        BigInteger certSerialNumber = new BigInteger(Long.toString(from.getTime()));

        ContentSigner contentSigner = new JcaContentSignerBuilder(signatureAlgorithm).build(keyPair.getPrivate());
        JcaX509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                dnName, certSerialNumber, from, to, dnName, keyPair.getPublic());

        // true for CA, false for EndEntity
        BasicConstraints basicConstraints = new BasicConstraints(true);

        // Basic Constraints is usually marked as critical.
        certBuilder.addExtension(new ASN1ObjectIdentifier("2.5.29.19"), true, basicConstraints);

        return new JcaX509CertificateConverter().setProvider(BouncyCastleProviderSingleton.getInstance()).getCertificate(certBuilder.build(contentSigner));
    }

    private Certificate getValid(Certificate certificate) throws CertificateException {
        var providerConfig = new CertificateProviderConfiguration(certificate);
        var certificateProvider = certificatePluginManager.create(providerConfig);
        if (certificateProvider == null) {
            throw new CertificateException("The configuration details entered are incorrect. Please check those and try again.");
        }
        var expiryDate = certificateProvider.getExpirationDate().orElse(null);
        if (expiryDate != null && Instant.now().isAfter(expiryDate.toInstant())) {
            throw new CertificateException("The certificate you uploaded has already expired. Please select a different certificate to upload.");
        }
        return certificate;
    }
}
