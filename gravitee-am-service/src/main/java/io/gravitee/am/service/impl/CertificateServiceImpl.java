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
import io.gravitee.am.certificate.api.CertificateMetadata;
import io.gravitee.am.certificate.api.CertificateProvider;
import io.gravitee.am.model.Certificate;
import io.gravitee.am.plugins.certificate.core.CertificateSchema;
import io.gravitee.am.repository.management.api.CertificateRepository;
import io.gravitee.am.service.CertificateService;
import io.gravitee.am.service.ClientService;
import io.gravitee.am.service.DomainService;
import io.gravitee.am.service.exception.CertificateNotFoundException;
import io.gravitee.am.service.exception.CertificateWithClientsException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.model.NewCertificate;
import io.gravitee.am.service.model.UpdateCertificate;
import io.gravitee.common.utils.UUID;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class CertificateServiceImpl implements CertificateService {

    /**
     * Logger.
     */
    private final Logger LOGGER = LoggerFactory.getLogger(CertificateServiceImpl.class);

    @Autowired
    private CertificateRepository certificateRepository;

    @Autowired
    private ClientService clientService;

    @Autowired
    private DomainService domainService;

    private ObjectMapper objectMapper = new ObjectMapper();

    private Map<String, CertificateProvider> certificateProviders = new HashMap<>();

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
    public Single<List<Certificate>> findByDomain(String domain) {
        LOGGER.debug("Find certificates by domain: {}", domain);
        return certificateRepository.findByDomain(domain)
                .flatMap(certificates -> Single.just((List<Certificate>) new ArrayList<>(certificates)))
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find certificates by domain", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to find certificates by domain", ex));
                });
    }

    @Override
    public Single<List<Certificate>> findAll() {
        LOGGER.debug("Find all certificates");
        return certificateRepository.findAll()
                .flatMap(certificates -> Single.just((List<Certificate>) new ArrayList<>(certificates)))
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find all certificates", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to find all certificates by domain", ex));
                });
    }

    @Override
    public Single<Certificate> create(String domain, NewCertificate newCertificate, String schema) {
        LOGGER.debug("Create a new certificate {} for domain {}", newCertificate, domain);

        Single<Certificate> certificateSingle = Single.create(emitter -> {
            String certificateId = UUID.toString(UUID.random());
            Certificate certificate = new Certificate();
            certificate.setId(certificateId);
            certificate.setDomain(domain);
            certificate.setName(newCertificate.getName());
            certificate.setType(newCertificate.getType());
            // handle file
            try {
                CertificateSchema certificateSchema = objectMapper.readValue(schema, CertificateSchema.class);
                JsonNode certificateConfiguration = objectMapper.readTree(newCertificate.getConfiguration());

                certificateSchema.getProperties()
                        .entrySet()
                        .stream()
                        .filter(map -> map.getValue().getWidget() != null && "file".equals(map.getValue().getWidget()))
                        .map(map -> map.getKey())
                        .forEach(key -> {
                            try {
                                JsonNode file = objectMapper.readTree(certificateConfiguration.get(key).asText());
                                byte[] data = Base64.getDecoder().decode(file.get("content").asText());
                                certificate.setMetadata(Collections.singletonMap(CertificateMetadata.FILE, data));

                                // update configuration to set the file name
                                ((ObjectNode) certificateConfiguration).put(key, file.get("name").asText());
                                newCertificate.setConfiguration(objectMapper.writeValueAsString(certificateConfiguration));
                            } catch (IOException ex) {
                                LOGGER.error("An error occurs while trying to create certificate binaries", ex);
                                emitter.onError(ex);
                            }
                        });

                certificate.setConfiguration(newCertificate.getConfiguration());
                certificate.setCreatedAt(new Date());
                certificate.setUpdatedAt(certificate.getCreatedAt());
            } catch (Exception ex) {
                LOGGER.error("An error occurs while trying to create certificate configuration", ex);
                emitter.onError(ex);
            }
            emitter.onSuccess(certificate);
        });


        return certificateSingle
                .flatMap(certificate -> certificateRepository.create(certificate))
                .flatMap(certificate -> {
                    // Reload domain to take care about certificate create
                    return domainService.reload(domain).flatMap(domain1 -> Single.just(certificate));
                })
                .doOnError(ex -> {
                    LOGGER.error("An error occurs while trying to create a certificate", ex);
                    throw new TechnicalManagementException("An error occurs while trying to create a certificate", ex);
                });
    }

    @Override
    public Single<Certificate> update(String domain, String id, UpdateCertificate updateCertificate, String schema) {
        LOGGER.debug("Update a certificate {} for domain {}", id, domain);

        return certificateRepository.findById(id)
                .switchIfEmpty(Maybe.error(new CertificateNotFoundException(id)))
                .flatMapSingle(oldCertificate -> {
                    Single<Certificate> certificateSingle = Single.create(emitter -> {
                        oldCertificate.setName(updateCertificate.getName());

                        try {

                            CertificateSchema certificateSchema = objectMapper.readValue(schema, CertificateSchema.class);
                            JsonNode oldCertificateConfiguration = objectMapper.readTree(oldCertificate.getConfiguration());
                            JsonNode certificateConfiguration = objectMapper.readTree(updateCertificate.getConfiguration());

                            certificateSchema.getProperties()
                                    .entrySet()
                                    .stream()
                                    .filter(map -> map.getValue().getWidget() != null && "file".equals(map.getValue().getWidget()))
                                    .map(map -> map.getKey())
                                    .forEach(key -> {
                                        try {
                                            String oldFileInformation = oldCertificateConfiguration.get(key).asText();
                                            String fileInformation = certificateConfiguration.get(key).asText();
                                            // file has changed, let's update it
                                            if (!oldFileInformation.equals(fileInformation)) {
                                                JsonNode file = objectMapper.readTree(certificateConfiguration.get(key).asText());
                                                byte[] data = Base64.getDecoder().decode(file.get("content").asText());
                                                oldCertificate.setMetadata(Collections.singletonMap(CertificateMetadata.FILE, data));

                                                // update configuration to set the file path
                                                ((ObjectNode) certificateConfiguration).put(key, file.get("name").asText());
                                                updateCertificate.setConfiguration(objectMapper.writeValueAsString(certificateConfiguration));
                                            }
                                        } catch (IOException ex) {
                                            LOGGER.error("An error occurs while trying to update certificate binaries", ex);
                                            emitter.onError(ex);
                                        }
                                    });


                            oldCertificate.setConfiguration(updateCertificate.getConfiguration());
                            oldCertificate.setUpdatedAt(new Date());

                        } catch (Exception ex) {
                            LOGGER.error("An error occurs while trying to update certificate configuration", ex);
                            emitter.onError(ex);
                        }
                        emitter.onSuccess(oldCertificate);
                    });

                    return certificateSingle
                            .flatMap(certificate -> certificateRepository.update(certificate))
                            .flatMap(certificate1 -> {
                                // Reload domain to take care about certificate update
                                return domainService.reload(domain).flatMap(domain1 -> Single.just(certificate1));
                            })
                            .doOnError(ex -> {
                                LOGGER.error("An error occurs while trying to update a certificate", ex);
                                throw new TechnicalManagementException("An error occurs while trying to update a certificate", ex);
                            });
                });
    }

    @Override
    public Single<Certificate> update(Certificate certificate) {
        // update date
        certificate.setUpdatedAt(new Date());

        return certificateRepository.update(certificate)
                .flatMap(certificate1 -> {
                    // Reload domain to take care about certificate update
                    return domainService.reload(certificate1.getDomain()).flatMap(domain1 -> Single.just(certificate1));
                })
                .doOnError(ex -> {
                    LOGGER.error("An error occurs while trying to update a certificate", ex);
                    throw new TechnicalManagementException("An error occurs while trying to update a certificate", ex);
                });
    }

    @Override
    public Completable delete(String certificateId) {
        LOGGER.debug("Delete certificate {}", certificateId);
        return certificateRepository.findById(certificateId)
                .switchIfEmpty(Maybe.error(new CertificateNotFoundException(certificateId)))
                .flatMapSingle(certificate -> clientService.findByCertificate(certificateId)
                        .flatMap(clients -> {
                            if (clients.size() > 0) {
                                throw new CertificateWithClientsException();
                            }
                            return Single.just(certificate);
                        })
                )
                .flatMapCompletable(certificate -> certificateRepository.delete(certificateId))
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to delete certificate: {}", certificateId, ex);
                    return Completable.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to delete certificate: %s", certificateId), ex));
                });
    }

    @Override
    // TODO : refactor (after JWKS information)
    public void setCertificateProviders(Map<String, CertificateProvider> certificateProviders) {
        this.certificateProviders = certificateProviders;
    }

    @Override
    // TODO : refactor (after JWKS information)
    public void setCertificateProvider(String certificateId, CertificateProvider certificateProvider) {
        this.certificateProviders.put(certificateId, certificateProvider);
    }

    @Override
    // TODO : refactor (after JWKS information)
    public Maybe<CertificateProvider> getCertificateProvider(String certificateId) {
        return Maybe.create(emitter -> {
            try {
                CertificateProvider certificateProvider = this.certificateProviders.get(certificateId);
                if (certificateProvider != null) {
                    emitter.onSuccess(certificateProvider);
                } else {
                    emitter.onComplete();
                }
            } catch (Exception e) {
                emitter.onError(e);
            }
        });
    }
}
