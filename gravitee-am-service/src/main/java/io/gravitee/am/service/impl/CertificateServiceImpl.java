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
import io.gravitee.am.certificate.api.CertificateProvider;
import io.gravitee.am.management.certificate.core.CertificateSchema;
import io.gravitee.am.model.Certificate;
import io.gravitee.am.repository.exceptions.TechnicalException;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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

    @Value("${certificates.path:${gravitee.home}/certificates}")
    private String certificatesPath;

    private Map<String, Map<String, CertificateProvider>> certificateProviders = new HashMap<>();

    @Override
    public Certificate findById(String id) {
        try {
            LOGGER.debug("Find certificate by ID: {}", id);
            Optional<Certificate> certificateOpt = certificateRepository.findById(id);

            if (!certificateOpt.isPresent()) {
                throw new CertificateNotFoundException(id);
            }

            return certificateOpt.get();
        } catch (TechnicalException ex) {
            LOGGER.error("An error occurs while trying to find a certificate using its ID: {}", id, ex);
            throw new TechnicalManagementException(
                    String.format("An error occurs while trying to find a certificate using its ID: %s", id), ex);
        }
    }

    @Override
    public List<Certificate> findByDomain(String domain) {
        try {
            LOGGER.debug("Find certificates by domain: {}", domain);
            return new ArrayList<>(certificateRepository.findByDomain(domain));
        } catch (TechnicalException ex) {
            LOGGER.error("An error occurs while trying to find certificates by domain", ex);
            throw new TechnicalManagementException("An error occurs while trying to find certificates by domain", ex);
        }
    }

    @Override
    public Certificate create(String domain, NewCertificate newCertificate, String schema) {
        try {
            LOGGER.debug("Create a new certificate {} for domain {}", newCertificate, domain);

            String certificateId = UUID.toString(UUID.random());
            Certificate certificate = new Certificate();
            certificate.setId(certificateId);
            certificate.setDomain(domain);
            certificate.setName(newCertificate.getName());
            certificate.setType(newCertificate.getType());

            // handle file
            // String schema = certificatePluginService.getSchema(newCertificate.getType());
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
                            File certificateFile = new File(certificatesPath + '/' + domain + '/' + certificateId + '/' + file.get("name").asText());
                            if (!certificateFile.exists()) {
                                certificateFile.getParentFile().mkdirs();
                                certificateFile.createNewFile();
                            }
                            try (FileOutputStream fop = new FileOutputStream(certificateFile)) {
                                fop.write(data);
                                fop.flush();
                                fop.close();

                                // update configuration to set the file path
                                ((ObjectNode) certificateConfiguration).put(key, certificateFile.getAbsolutePath());
                                newCertificate.setConfiguration(objectMapper.writeValueAsString(certificateConfiguration));
                            }
                        } catch (IOException ex) {
                            LOGGER.error("An error occurs while trying to create certificate binaries", ex);
                            throw new TechnicalManagementException("An error occurs while trying to create certificate binaries", ex);
                        }
                    });

            certificate.setConfiguration(newCertificate.getConfiguration());
            certificate.setCreatedAt(new Date());
            certificate.setUpdatedAt(certificate.getCreatedAt());


            Certificate certificateCreated = certificateRepository.create(certificate);

            // Reload domain to take care about certificate create
            domainService.reload(domain);

            return certificateCreated;
        } catch (TechnicalException | IOException ex) {
            LOGGER.error("An error occurs while trying to create a certificate", ex);
            throw new TechnicalManagementException("An error occurs while trying to create a certificate", ex);
        }
    }

    @Override
    public Certificate update(String domain, String id, UpdateCertificate updateCertificate, String schema) {
        try {
            LOGGER.debug("Update a certificate {} for domain {}", id, domain);

            Optional<Certificate> certificateOpt = certificateRepository.findById(id);
            if (!certificateOpt.isPresent()) {
                throw new CertificateNotFoundException(id);
            }

            Certificate oldCertificate = certificateOpt.get();
            oldCertificate.setName(updateCertificate.getName());

            // handle file
            // String schema = certificatePluginService.getSchema(oldCertificate.getType());
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
                                File certificateFile = new File(certificatesPath + '/' + domain + '/' + oldCertificate.getId() + '/' + file.get("name").asText());
                                if (!certificateFile.exists()) {
                                    certificateFile.getParentFile().mkdirs();
                                    certificateFile.createNewFile();
                                }
                                try (FileOutputStream fop = new FileOutputStream(certificateFile)) {
                                    fop.write(data);
                                    fop.flush();
                                    fop.close();

                                    // update configuration to set the file path
                                    ((ObjectNode) certificateConfiguration).put(key, certificateFile.getAbsolutePath());
                                    updateCertificate.setConfiguration(objectMapper.writeValueAsString(certificateConfiguration));
                                }
                            }
                        } catch (IOException ex) {
                            LOGGER.error("An error occurs while trying to update certificate binaries", ex);
                            throw new TechnicalManagementException("An error occurs while trying to update certificate binaries", ex);
                        }
                    });


            oldCertificate.setConfiguration(updateCertificate.getConfiguration());
            oldCertificate.setUpdatedAt(new Date());

            Certificate certificate = certificateRepository.update(oldCertificate);

            // Reload domain to take care about certificate update
            domainService.reload(domain);

            return certificate;
        } catch (TechnicalException | IOException ex) {
            LOGGER.error("An error occurs while trying to update a certificate", ex);
            throw new TechnicalManagementException("An error occurs while trying to update a certificate", ex);
        }
    }

    @Override
    public void delete(String certificateId) {
        try {
            LOGGER.debug("Delete certificate {}", certificateId);

            Optional<Certificate> optCertificate = certificateRepository.findById(certificateId);
            if (! optCertificate.isPresent()) {
                throw new CertificateNotFoundException(certificateId);
            }

            int clients = clientService.findByCertificate(certificateId).size();
            if (clients > 0) {
                throw new CertificateWithClientsException();
            }

            // delete certificate files
            Path certificatePath = Paths.get(certificatesPath + '/' + optCertificate.get().getDomain() + '/' + certificateId);
            Files.walk(certificatePath, FileVisitOption.FOLLOW_LINKS)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);

            certificateRepository.delete(certificateId);
        } catch (TechnicalException | IOException ex) {
            LOGGER.error("An error occurs while trying to delete certificate: {}", certificateId, ex);
            throw new TechnicalManagementException(
                    String.format("An error occurs while trying to delete certificate: %s", certificateId), ex);
        }
    }

    @Override
    public void setCertificateProviders(String domainId, Map<String, CertificateProvider> certificateProviders) {
        this.certificateProviders.put(domainId, certificateProviders);
    }

    @Override
    public CertificateProvider getCertificateProvider(String domainId, String certificateId) {
        return this.certificateProviders.get(domainId).get(certificateId);
    }


}
