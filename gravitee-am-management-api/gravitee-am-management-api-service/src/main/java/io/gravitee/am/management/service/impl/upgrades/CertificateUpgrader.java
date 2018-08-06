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
package io.gravitee.am.management.service.impl.upgrades;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.model.Certificate;
import io.gravitee.am.service.CertificateService;
import io.reactivex.Observable;
import io.reactivex.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class CertificateUpgrader implements Upgrader, Ordered {

    private static final Logger logger = LoggerFactory.getLogger(CertificateUpgrader.class);
    private static final String JAVA_KEYSTORE_AM_TYPE = "javakeystore-am-certificate";
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private CertificateService certificateService;

    @Override
    public boolean upgrade() {
        logger.info("Applying certificate upgrade");
        certificateService.findAll()
                .flatMapObservable(certificates -> Observable.fromIterable(certificates))
                // only AM JAVA KEYSTORE must be update if the jks file is still set in the filesystem
                .filter(certificate -> JAVA_KEYSTORE_AM_TYPE.equals(certificate.getType()))
                .flatMapSingle(certificate -> upgradeCertificate(certificate))
                .subscribe();
        return true;
    }

    private Single<Certificate> upgradeCertificate(Certificate certificate)  {
        if (certificate.getMetadata() == null || certificate.getMetadata().get("file") == null) {
            logger.info("Update certificate " + certificate.getName() + ", trying to upload the jks file to the repository ...");
            try {
                JsonNode configuration = objectMapper.readTree(certificate.getConfiguration());
                String jksFilePath = configuration.get("jks").asText();
                File file = new File(jksFilePath);
                byte[] fileContent = Files.readAllBytes(file.toPath());
                // update metadata
                Map<String, Object> metadata = (certificate.getMetadata() == null) ? new HashMap<>() : new HashMap<>(certificate.getMetadata());
                metadata.put("file", fileContent);
                certificate.setMetadata(metadata);
                return certificateService.update(certificate)
                        .doOnSuccess(certificate1 -> logger.info("Certificate " + certificate1.getName() + " has been updated"));
            } catch (Exception e) {
                logger.error("An error occurs during certificate update process", e);
                return Single.error(e);
            }
        } else {
            // nothing to do
            return Single.just(certificate);
        }
    }

    @Override
    public int getOrder() {
        return 161;
    }
}
