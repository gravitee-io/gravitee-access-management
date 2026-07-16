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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Maps;
import com.nimbusds.jose.crypto.bc.BouncyCastleProviderSingleton;
import io.gravitee.am.common.audit.EventType;
import io.gravitee.am.common.event.Action;
import io.gravitee.am.common.event.Type;
import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.model.Certificate;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ManagedBy;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.plugins.certificate.core.CertificatePluginManager;
import io.gravitee.am.plugins.certificate.core.CertificateProviderConfiguration;
import io.gravitee.am.plugins.certificate.core.schema.CertificateSchema;
import io.gravitee.am.repository.management.api.CertificateRepository;
import io.gravitee.am.repository.management.api.DomainRepository;
import io.gravitee.am.service.ApplicationService;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.CertificatePluginService;
import io.gravitee.am.service.CertificateService;
import io.gravitee.am.service.EventService;
import io.gravitee.am.service.IdentityProviderService;
import io.gravitee.am.service.ProtectedResourceService;
import io.gravitee.am.service.PluginConfigurationValidationService;
import io.gravitee.am.service.PluginLicenseGate;
import io.gravitee.am.service.TaskManager;
import io.gravitee.am.service.exception.AbstractManagementException;
import io.gravitee.am.service.exception.CertificateIsFallbackException;
import io.gravitee.am.service.exception.CertificateAliasAlreadyExistsException;
import io.gravitee.am.service.exception.CertificateNotFoundException;
import io.gravitee.am.service.exception.CertificatePluginSchemaNotFoundException;
import io.gravitee.am.service.exception.CertificateWithApplicationsException;
import io.gravitee.am.service.exception.CertificateWithIdpException;
import io.gravitee.am.service.exception.CertificateWithProtectedResourceException;
import io.gravitee.am.service.exception.InvalidParameterException;
import io.gravitee.am.service.exception.InvalidPluginConfigurationException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.model.AutomationNewCertificate;
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
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static io.gravitee.am.certificate.api.CertificateMetadata.FILE;
import static io.gravitee.am.certificate.api.ConfigurationCertUtils.usageContains;
import static io.gravitee.am.identityprovider.api.common.IdentityProviderConfigurationUtils.extractCertificateId;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Slf4j
@Component
@Primary
public class CertificateServiceImpl implements CertificateService {
    public static final String DEFAULT_CERTIFICATE_PLUGIN = "pkcs12-am-certificate";

    private static final String ECDSA = "ECDSA";
    private static final String DEFAULT_CERT_CN_NAME = "cn=Gravitee.io";
    private static final String DEFAULT_CERT_ALGO = "SHA256withRSA";
    private static final String DEFAULT_CERT_PASS = "gravitee";
    private static final int DEFAULT_CERT_KEYSIZE = 2048;
    private static final int DEFAULT_CERT_VALIDITY_IN_DAYS = 365;
    private static final String DEFAULT_CERT_ALIAS = "default";
    private static final String RSA = "RSA";
    private static final String EC = "EC";
    private static final String CONTENT = "content";
    private static final String ALIAS = "alias";
    private static final String NAME = "name";
    private static final String KEY_PASS = "keypass";
    private static final String STORE_PASS = "storepass";

    @Lazy
    @Autowired
    private CertificateRepository certificateRepository;

    @Lazy
    @Autowired
    private DomainRepository domainRepository;

    @Autowired
    private ApplicationService applicationService;

    @Autowired
    private IdentityProviderService identityProviderService;

    @Autowired
    private ProtectedResourceService protectedResourceService;

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
    private PluginConfigurationValidationService validationService;

    @Autowired
    private PluginLicenseGate pluginLicenseGate;

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
        log.debug("Find certificate by ID: {}", id);
        return certificateRepository.findById(id)
                .onErrorResumeNext(ex -> {
                    log.error("An error occurs while trying to find a certificate using its ID: {}", id, ex);
                    return Maybe.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find a certificate using its ID: %s", id), ex));
                });
    }

    @Override
    public Flowable<Certificate> findByDomain(String domain) {
        return innerFindByDomain(domain);
    }

    private Flowable<Certificate> innerFindByDomain(String domain) {
        log.debug("Find certificates by domain: {}", domain);
        return certificateRepository.findByDomain(domain)
                .onErrorResumeNext(ex -> {
                    log.error("An error occurs while trying to find certificates by domain", ex);
                    return Flowable.error(new TechnicalManagementException("An error occurs while trying to find certificates by domain", ex));
                });
    }

    @Override
    public Flowable<Certificate> findAll() {
        log.debug("Find all certificates");
        return certificateRepository.findAll()
                .onErrorResumeNext(ex -> {
                    log.error("An error occurs while trying to find all certificates", ex);
                    return Flowable.error(new TechnicalManagementException("An error occurs while trying to find all certificates by domain", ex));
                });
    }

    @Override
    public Single<Certificate> create(Domain domain, NewCertificate newCertificate, User principal, boolean isSystem) {
        log.debug("Create a new certificate {} for domain {}", newCertificate, domain.getId());
        final Completable licenseCheck = isSystem
                ? Completable.complete()
                : pluginLicenseGate.check(Reference.domain(domain.getId()), PluginLicenseGate.TYPE_CERTIFICATE, newCertificate.getType());
        return licenseCheck.andThen(validateConfiguration(newCertificate, isSystem)).andThen(Single.defer(() -> certificatePluginService
                .getSchema(newCertificate.getType())
                .switchIfEmpty(Single.error(() -> new CertificatePluginSchemaNotFoundException(newCertificate.getType())))
                .map(schema -> objectMapper.readValue(schema, CertificateSchema.class))
                .flatMap(schema -> {
                    try {
                        var certificate = schema.getFileKey()
                                .map(fileKey -> createCertificateWithEmbeddedKeys(domain.getId(), newCertificate, isSystem, fileKey))
                                .orElseGet(() -> createCertificate(domain.getId(), newCertificate, isSystem));
                        var validated = validate(certificate);
                        if (!isSystem) {
                            return extractAlias(validated.getConfiguration())
                                    .map(alias -> checkAliasUniqueness(domain.getId(), alias, null)
                                            .andThen(Single.defer(() -> certificateRepository.create(validated))))
                                    .orElseGet(() -> certificateRepository.create(validated));
                        }
                        return certificateRepository.create(validated);
                    } catch (CertificateException ex) {
                        log.error("An error occurs while trying to create certificate configuration", ex);
                        return Single.error(ex);
                    } catch (AbstractManagementException ex) {
                        // a malformed configuration is a client error; preserve its 4xx status
                        return Single.error(ex);
                    } catch (Exception ex) {
                        log.error("An error occurs while trying to create certificate configuration", ex);
                        return Single.error(new TechnicalManagementException("An error occurs while trying to create a certificate", ex));
                    }

                })
                // create event for sync process
                .flatMap(certificate -> {
                    Event event = new Event(Type.CERTIFICATE, new Payload(certificate.getId(), ReferenceType.DOMAIN, certificate.getDomain(), Action.CREATE));
                    return eventService.create(event, domain).flatMap(__ -> Single.just(certificate));
                })
                .onErrorResumeNext(ex -> {
                    log.error("An error occurs while trying to create a certificate", ex);
                    if (ex instanceof CertificateException) {
                        return Single.error(new InvalidParameterException(ex.getMessage()));
                    }
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }
                    return Single.error(new TechnicalManagementException("An error occurs while trying to create a certificate", ex));
                })));
    }

    private Completable validateConfiguration(NewCertificate newCertificate, boolean isSystem) {
        if (isSystem) {
            return Completable.complete();
        }
        return Completable.fromAction(() -> validationService.validate(newCertificate.getType(), newCertificate.getConfiguration()));
    }

    @SneakyThrows
    private Certificate createCertificateWithEmbeddedKeys(String domain, NewCertificate newCertificate, boolean isSystem, String fileKey) {
        ObjectNode configuration = (ObjectNode) objectMapper.readTree(newCertificate.getConfiguration());
        EmbeddedFile file = parseEmbeddedFile(requireFileValue(configuration, fileKey), fileKey);
        // update configuration to set the file name
        configuration.put(fileKey, file.name());
        newCertificate.setConfiguration(objectMapper.writeValueAsString(configuration));
        return createCertificate(domain, newCertificate, isSystem, file.content());
    }

    /**
     * Read the value carried under {@code fileKey} in a certificate configuration.
     */
    private String requireFileValue(JsonNode configuration, String fileKey) {
        JsonNode node = configuration.get(fileKey);
        if (node == null || node.isNull() || !node.isTextual() || node.asText().isBlank()) {
            throw InvalidPluginConfigurationException.fromValidationError("Field '" + fileKey + "' is required");
        }
        return node.asText();
    }

    /**
     * Parse the embedded keystore file ({@code content} + {@code name}) carried as the value of a file field.
     */
    private EmbeddedFile parseEmbeddedFile(String fileValue, String fileKey) {
        final JsonNode file;
        try {
            file = objectMapper.readTree(fileValue);
        } catch (JsonProcessingException e) {
            throw InvalidPluginConfigurationException.fromValidationError("Field '" + fileKey + "' must contain a valid certificate file");
        }
        JsonNode contentNode = file == null ? null : file.get(CONTENT);
        JsonNode nameNode = file == null ? null : file.get(NAME);
        if (contentNode == null || !contentNode.isTextual() || nameNode == null || !nameNode.isTextual()) {
            throw InvalidPluginConfigurationException.fromValidationError(
                    "Field '" + fileKey + "' must contain a certificate file with 'content' and 'name'");
        }
        try {
            return new EmbeddedFile(Base64.getDecoder().decode(contentNode.asText()), nameNode.asText());
        } catch (IllegalArgumentException e) {
            throw InvalidPluginConfigurationException.fromValidationError("Field '" + fileKey + "' contains invalid Base64 file content");
        }
    }

    private Certificate createCertificate(String domain, NewCertificate newCertificate, boolean isSystem) {
        return createCertificate(domain, newCertificate, isSystem, null);
    }

    private Certificate createCertificate(String domain, NewCertificate newCertificate, boolean isSystem, byte[] content) {
        var certificate = new Certificate();
        certificate.setDomain(domain);
        certificate.setName(newCertificate.getName());
        certificate.setType(newCertificate.getType());
        certificate.setSystem(isSystem);
        certificate.setMetadata(content == null ? Maps.newHashMap() : Maps.newHashMap(Map.of(FILE, content)));
        certificate.setConfiguration(newCertificate.getConfiguration());
        certificate.setCreatedAt(new Date());
        certificate.setUpdatedAt(certificate.getCreatedAt());
        if (newCertificate instanceof AutomationNewCertificate auto) {
            // the Automation API supplies a deterministic id derived from the domain + key
            certificate.setId(auto.getId() == null ? RandomString.generate() : auto.getId());
            certificate.setAutomationKey(auto.getAutomationKey());
            certificate.setManagedBy(ManagedBy.AUTOMATION_API);
        } else {
            certificate.setId(RandomString.generate());
        }
        return certificate;
    }

    private record CertificateWithSchema(Certificate certificate, CertificateSchema schema) {
    }

    @Override
    public Single<Certificate> update(Domain domain, String id, UpdateCertificate updateCertificate, User principal) {
        log.debug("Update a certificate {} for domain {}", id, domain.getId());
        return certificateRepository.findById(id)
                .switchIfEmpty(Single.error(() -> new CertificateNotFoundException(id)))
                .flatMap((Function<Certificate, SingleSource<CertificateWithSchema>>) certificate -> {
                    // 'type' is immutable for an existing certificate
                    if (updateCertificate.getType() != null && !updateCertificate.getType().isBlank()
                            && !updateCertificate.getType().equals(certificate.getType())) {
                        return Single.error(new InvalidParameterException("Certificate type cannot be changed"));
                    }
                    final Completable licenseCheck = certificate.isSystem()
                            ? Completable.complete()
                            : pluginLicenseGate.check(Reference.domain(domain.getId()), PluginLicenseGate.TYPE_CERTIFICATE, certificate.getType());
                    return licenseCheck.andThen(Single.defer(() -> certificatePluginService.getSchema(certificate.getType())
                            .switchIfEmpty(Single.error(() -> new CertificatePluginSchemaNotFoundException(certificate.getType())))
                            .map(schema -> new CertificateWithSchema(certificate, objectMapper.readValue(schema, CertificateSchema.class)))));
                })
                .flatMap(oldCertificate -> {
                    boolean oldWithMTls = usageContains(oldCertificate.certificate().getConfiguration(), "mtls");
                    boolean newWithMTls = usageContains(updateCertificate.getConfiguration(), "mtls");
                    if (oldWithMTls && !newWithMTls) {
                        return checkIdentityProviderUsage(oldCertificate.certificate())
                                .map(cert -> oldCertificate);
                    } else {
                        return Single.just(oldCertificate);
                    }
                })
                .flatMap(oldCertificate -> {
                    Certificate existing = oldCertificate.certificate();
                    if (!existing.isSystem()) {
                        ensureParseableConfiguration(updateCertificate.getConfiguration());
                    }
                    var certificate = getCertificateToUpdate(updateCertificate, oldCertificate);

                    Optional<String> newAlias = extractAlias(certificate.getConfiguration());
                    Optional<String> oldAlias = extractAlias(oldCertificate.certificate().getConfiguration());
                    boolean aliasChanged = newAlias.isPresent() && !newAlias.equals(oldAlias);

                    Single<Certificate> validateAndUpdate = Single.defer(() -> {
                        try {
                            // for update validate config against schema here instead of the resource
                            // as certificate may be system certificate so on the UI config is empty.
                            validationService.validate(certificate.getType(), certificate.getConfiguration());
                            var validated = validate(certificate);
                            return certificateRepository.update(validated);
                        } catch (CertificateException ex) {
                            log.error("An error occurs while trying to update certificate binaries", ex);
                            return Single.error(ex);
                        } catch (Exception ex) {
                            log.error("An error occurs while trying to update certificate configuration", ex);
                            return Single.error(ex);
                        }
                    });

                    if (aliasChanged) {
                        return checkAliasUniqueness(certificate.getDomain(), newAlias.get(), certificate.getId())
                                .andThen(validateAndUpdate);
                    }
                    return validateAndUpdate;
                })
                // create event for sync process
                .flatMap(certificate1 -> {
                    Event event = new Event(Type.CERTIFICATE, new Payload(certificate1.getId(), ReferenceType.DOMAIN, certificate1.getDomain(), Action.UPDATE));
                    return eventService.create(event, domain).flatMap(__ -> Single.just(certificate1));
                })
                .onErrorResumeNext(ex -> {
                    log.error("An error occurs while trying to update a certificate", ex);
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }
                    if (ex instanceof CertificateException) {
                        return Single.error(new InvalidParameterException(ex.getMessage()));
                    }
                    return Single.error(new TechnicalManagementException("An error occurs while trying to update a certificate", ex));
                });
    }

    /**
     * Ensure a user-supplied configuration is present and well-formed JSON before it is parsed for key
     * embedding.
     */
    private void ensureParseableConfiguration(String configuration) {
        if (configuration == null || configuration.isBlank()) {
            throw InvalidPluginConfigurationException.fromValidationError("configuration is required");
        }
        try {
            objectMapper.readTree(configuration);
        } catch (JsonProcessingException e) {
            throw InvalidPluginConfigurationException.fromValidationError("configuration is not valid JSON");
        }
    }

    private Certificate getCertificateToUpdate(UpdateCertificate updateCertificate, CertificateWithSchema oldCertificate) throws JsonProcessingException, CertificateException {
        var certificateToUpdate = new Certificate(oldCertificate.certificate());
        certificateToUpdate.setName(updateCertificate.getName());
        certificateToUpdate.setUpdatedAt(new Date());
        // A system certificate's configuration is immutable through the service layer (it is owned by
        // gravitee.yaml); only a non-system certificate's configuration is updated.
        if (!certificateToUpdate.isSystem()) {
            oldCertificate.schema.getFileKey().ifPresent(fileKey -> updateEmbeddedKeys(updateCertificate, certificateToUpdate, oldCertificate, fileKey));
            certificateToUpdate.setConfiguration(updateCertificate.getConfiguration());
        }

        return certificateToUpdate;
    }

    @SneakyThrows
    private void updateEmbeddedKeys(UpdateCertificate updateCertificate,
                                    Certificate certificateToUpdate,
                                    CertificateWithSchema oldCertificate,
                                    String fileKey) {
        ObjectNode updateCertJson = (ObjectNode) objectMapper.readTree(updateCertificate.getConfiguration());
        var fileInformation = requireFileValue(updateCertJson, fileKey);
        JsonNode oldFileNode = objectMapper.readTree(oldCertificate.certificate().getConfiguration()).get(fileKey);
        var oldFileInformation = oldFileNode == null ? null : oldFileNode.asText();
        // file has changed, let's update it
        if (!oldFileInformation.equals(fileInformation)) {
            EmbeddedFile file = parseEmbeddedFile(fileInformation, fileKey);
            certificateToUpdate.setMetadata(Maps.newHashMap(Map.of(FILE, file.content())));
            // update configuration to set the file path
            updateCertJson.put(fileKey, file.name());
        }
        updateCertificate.setConfiguration(objectMapper.writeValueAsString(updateCertJson));
    }

    private Single<Certificate> checkIdentityProviderUsage(Certificate certificate) {
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

    private Single<Certificate> checkProtectedResourceUsage(Certificate certificate) {
        return protectedResourceService.findByCertificate(certificate.getId())
                .count()
                .flatMap(count -> {
                    if (count > 0) {
                        return Single.error(new CertificateWithProtectedResourceException());
                    }
                    return Single.just(certificate);
                });
    }

    private Single<Certificate> checkFallbackUsage(Certificate certificate) {
        return domainRepository.findById(certificate.getDomain())
                .flatMap(domain -> {
                    if (domain.getCertificateSettings() != null
                            && certificate.getId().equals(domain.getCertificateSettings().getFallbackCertificate())) {
                        return Maybe.error(new CertificateIsFallbackException());
                    }
                    return Maybe.just(certificate);
                })
                .defaultIfEmpty(certificate);
    }

    @Override
    public Completable delete(String certificateId, User principal, boolean force) {
        log.debug("Delete certificate {}", certificateId);
        return certificateRepository.findById(certificateId)
                .switchIfEmpty(Maybe.error(new CertificateNotFoundException(certificateId)))
                .flatMapSingle(certificate -> force ? Single.just(certificate) : checkApplicationsUsage(certificate)
                                .flatMap(this::checkIdentityProviderUsage)
                                .flatMap(this::checkProtectedResourceUsage)
                                .flatMap(this::checkFallbackUsage))
                .flatMapCompletable(certificate -> {
                    // create event for sync process
                    Event event = new Event(Type.CERTIFICATE, new Payload(certificate.getId(), ReferenceType.DOMAIN, certificate.getDomain(), Action.DELETE));
                    return Completable.fromSingle(certificateRepository.delete(certificateId)
                                    .andThen(eventService.create(event)))
                            .doOnComplete(() -> auditService.report(AuditBuilder.builder(CertificateAuditBuilder.class).principal(principal).type(EventType.CERTIFICATE_DELETED).certificate(certificate)))
                            .doOnError(throwable -> auditService.report(AuditBuilder.builder(CertificateAuditBuilder.class).principal(principal).type(EventType.CERTIFICATE_DELETED).certificate(certificate).throwable(throwable)));
                })
                .onErrorResumeNext(ex -> {
                    log.error("An error occurs while trying to delete certificate: {}", certificateId, ex);
                    if (ex instanceof AbstractManagementException) {
                        return Completable.error(ex);
                    } else {
                        return Completable.error(new TechnicalManagementException(
                                String.format("An error occurs while trying to delete certificate: %s", certificateId), ex));
                    }
                });
    }

    private Single<Certificate> checkApplicationsUsage(Certificate certificate) {
        return applicationService.findByCertificate(certificate.getId()).count()
                .flatMap(applications -> {
                    if (applications > 0) {
                        return Single.error(new CertificateWithApplicationsException());
                    }
                    return Single.just(certificate);
                });
    }

    @Override
    public Completable updateExpirationDate(String certificateId, Date expirationDate) {
        if (expirationDate == null) {
            log.warn("updateExpirationDate call with null for certificate '{}'", certificateId);
            return Completable.complete();
        }
        return this.certificateRepository.updateExpirationDate(certificateId, expirationDate);
    }

    @Override
    public Single<Certificate> create(Domain domain) {
        return buildDefaultCertificate(domain)
                .flatMap(certificate -> create(domain, certificate, true));
    }

    @Override
    public Single<Certificate> createSystem(Domain domain, String id, String automationKey, User principal) {
        return buildDefaultCertificate(domain)
                .flatMap(base -> {
                    AutomationNewCertificate auto = new AutomationNewCertificate();
                    auto.setId(id);
                    auto.setAutomationKey(automationKey);
                    auto.setName(base.getName());
                    auto.setType(base.getType());
                    auto.setConfiguration(base.getConfiguration());
                    return create(domain, auto, principal, true);
                });
    }

    /**
     * Build the default PKCS12 {@link NewCertificate} for the given domain from the
     * {@code domains.certificates.default.*} settings in {@code gravitee.yaml}. The returned model carries
     * the name, type and configuration but no id and no automation key — those are assigned by the caller.
     */
    private Single<NewCertificate> buildDefaultCertificate(Domain domain) {
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
                    final String keyPass = environment.getProperty("domains.certificates.default.keypass", String.class, DEFAULT_CERT_PASS);
                    final String storePass = environment.getProperty("domains.certificates.default.storepass", String.class, DEFAULT_CERT_PASS);

                    ByteArrayOutputStream generateCertificate = generateCertificate(sigAlgName, keySize, name, validity, alias, keyPass, storePass);

                    ObjectNode certificateNode = objectMapper.createObjectNode();

                    ObjectNode contentNode = objectMapper.createObjectNode();
                    contentNode.put(CONTENT, new String(Base64.getEncoder().encode(generateCertificate.toByteArray())));
                    contentNode.put(NAME, domain + ".p12");
                    certificateNode.put(CONTENT, objectMapper.writeValueAsString(contentNode));
                    certificateNode.put(ALIAS, alias);
                    certificateNode.put(STORE_PASS, storePass);
                    certificateNode.put(KEY_PASS, keyPass);

                    return objectMapper.writeValueAsString(certificateNode);
                })
                .map(configuration -> {
                    certificate.setConfiguration(configuration);
                    return certificate;
                });
    }

    @Override
    public Single<Certificate> rotate(Domain domain, User principal) {
        return this.innerFindByDomain(domain.getId())// search using inner method to not go through the CertificateServiceProxy filtering
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
                        rotatedCertificate.setConfiguration(generateCertificateConfiguration(domain.getId(), deprecatedCert.getConfiguration(), now));
                        return create(domain, rotatedCertificate, true).map(newCertificate -> {
                            final var task = new AssignSystemCertificate(applicationService, certificateRepository, taskManager);
                            final var definition = new AssignSystemCertificateDefinition(domain.getId(), newCertificate.getId(), deprecatedCert.getId());
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
                        .reference(Reference.domain(domain.getId()))
                        .type(EventType.CERTIFICATE_CREATED)
                        .certificate(certificate)))
                .doOnError(error -> auditService.report(AuditBuilder.builder(CertificateAuditBuilder.class)
                        .principal(principal)
                        .reference(Reference.domain(domain.getId()))
                        .type(EventType.CERTIFICATE_CREATED).throwable(error)));
    }

    private String generateCertificateConfiguration(String domain, String previousConfig, LocalDateTime now) throws Exception {
        final var suffix = DateTimeFormatter.ofPattern("-yyyyMMddHHmmss").format(now);

        final int keySize = environment.getProperty("domains.certificates.default.keysize", int.class, DEFAULT_CERT_KEYSIZE);
        final int validity = environment.getProperty("domains.certificates.default.validity", int.class, DEFAULT_CERT_VALIDITY_IN_DAYS);
        final String name = environment.getProperty("domains.certificates.default.name", String.class, DEFAULT_CERT_CN_NAME);
        final String alias = environment.getProperty("domains.certificates.default.alias", String.class, DEFAULT_CERT_ALIAS) + suffix;
        final String sigAlgName = environment.getProperty("domains.certificates.default.algorithm", String.class, DEFAULT_CERT_ALGO);
        final String keyPass = environment.getProperty("domains.certificates.default.keypass", String.class, DEFAULT_CERT_PASS);
        final String storePass = environment.getProperty("domains.certificates.default.storepass", String.class, DEFAULT_CERT_PASS);

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
        certificateNode.put(KEY_PASS, keyPass);

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

    private Optional<String> extractAlias(String configuration) {
        if (configuration == null) {
            return Optional.empty();
        }
        try {
            JsonNode configNode = objectMapper.readTree(configuration);
            JsonNode aliasNode = configNode.get(ALIAS);
            if (aliasNode != null && !aliasNode.isNull() && aliasNode.isTextual()) {
                String alias = aliasNode.asText();
                return alias.isBlank() ? Optional.empty() : Optional.of(alias);
            }
            return Optional.empty();
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse certificate configuration for alias extraction", e);
            return Optional.empty();
        }
    }

    private Completable checkAliasUniqueness(String domainId, String alias, String excludeCertId) {
        return certificateRepository.findByDomain(domainId)
                .filter(existingCert -> {
                    if (excludeCertId != null && excludeCertId.equals(existingCert.getId())) {
                        return false;
                    }
                    return extractAlias(existingCert.getConfiguration())
                            .map(existingAlias -> existingAlias.equals(alias))
                            .orElse(false);
                })
                .firstElement()
                .flatMapCompletable(conflicting -> Completable.error(
                        new CertificateAliasAlreadyExistsException(alias)));
    }

    private Certificate validate(Certificate certificate) throws CertificateException {
        var providerConfig = new CertificateProviderConfiguration(certificate);
        var validationResult = certificatePluginManager.validate(providerConfig);
        if (validationResult.failed()) {
            throw new CertificateException(validationResult.failedMessage());
        }
        validationResult.getAdditionalInformation("expDate", Date.class)
            .ifPresent(certificate::setExpiresAt);
        return certificate;
    }

    private record EmbeddedFile(byte[] content, String name) {
    }
}
