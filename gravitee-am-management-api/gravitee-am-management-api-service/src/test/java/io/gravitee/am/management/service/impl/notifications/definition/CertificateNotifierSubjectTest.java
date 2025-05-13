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
package io.gravitee.am.management.service.impl.notifications.definition;

import io.gravitee.am.model.Certificate;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.User;
import io.gravitee.am.model.safe.CertificateProperties;
import io.gravitee.am.model.safe.DomainProperties;
import io.gravitee.am.model.safe.UserProperties;
import org.junit.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class CertificateNotifierSubjectTest {

    @Test
    public void metadata_test(){
        // when
        Certificate certificate = new Certificate();
        certificate.setId("id");

        Domain domain = new Domain();
        domain.setId("domain");

        User user = new User();
        user.setId("user");

        CertificateNotifierSubject sub = new CertificateNotifierSubject(certificate, domain, user);

        // expect
        Map<String, Object> metadata = sub.getMetadata();
        assertEquals(certificate.getId(), metadata.get("certificateId"));
        assertEquals(domain.getId(), metadata.get("domainId"));
        assertEquals(user.getId(), metadata.get("domainOwner"));

    }

    @Test
    public void data_test(){
        // when
        Certificate certificate = new Certificate();
        certificate.setId("id");

        Domain domain = new Domain();
        domain.setId("domain");

        User user = new User();
        user.setId("user");

        CertificateNotifierSubject sub = new CertificateNotifierSubject(certificate, domain, user);

        // expect
        Map<String, Object> metadata = sub.getData();
        var certProperties = (CertificateProperties) metadata.get("certificate");
        var domainProperties = (DomainProperties) metadata.get("domain");
        var userProperties = (UserProperties) metadata.get("user");

        assertEquals(certificate.getId(), certProperties.getId());
        assertEquals(userProperties.getId(), user.getId());
        assertEquals(domainProperties.getId(), domain.getId());

    }


}