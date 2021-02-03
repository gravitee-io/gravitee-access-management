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
package io.gravitee.am.repository.management.api;

import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.model.Installation;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.repository.management.AbstractManagementTest;
import io.reactivex.observers.TestObserver;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
public class InstallationRepositoryTest extends AbstractManagementTest {

    @Autowired
    private InstallationRepository installationRepository;

    @Test
    public void testFindById() {
        // create idp
        Installation installation = buildInstallation();
        Installation installationCreated = installationRepository.create(installation).blockingGet();

        // fetch idp
        TestObserver<Installation> testObserver = installationRepository.findById(installationCreated.getId()).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(found -> found.getId().equals(installation.getId())
                && found.getAdditionalInformation().equals(installation.getAdditionalInformation()));
    }

    @Test
    public void testNotFoundById() {
        installationRepository.findById("UNKNOWN").test().assertEmpty();
    }

    @Test
    public void testCreate() {
        Installation installation = buildInstallation();
        TestObserver<Installation> testObserver = installationRepository.create(installation).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(idp -> idp.getId().equals(installation.getId())
                && idp.getAdditionalInformation().equals(installation.getAdditionalInformation()));
    }

    @Test
    public void testUpdate() {
        // create idp
        Installation installation = buildInstallation();
        Installation installationCreated = installationRepository.create(installation).blockingGet();

        // update idp
        Installation updatedInstallation = buildInstallation();
        updatedInstallation.setId(installationCreated.getId());
        updatedInstallation.getAdditionalInformation().put("key3", "value3");
        updatedInstallation.getAdditionalInformation().remove("key1");


        TestObserver<Installation> testObserver = installationRepository.update(updatedInstallation).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(updated -> updated.getId().equals(updatedInstallation.getId())
                && updated.getAdditionalInformation().equals(updatedInstallation.getAdditionalInformation()));
    }

    @Test
    public void testDelete() {
        // create idp
        Installation installation = buildInstallation();
        Installation installationCreated = installationRepository.create(installation).blockingGet();

        // delete idp
        TestObserver<Void> testObserver1 = installationRepository.delete(installationCreated.getId()).test();
        testObserver1.awaitTerminalEvent();

        // fetch idp
        installationRepository.findById(installationCreated.getId()).test().assertEmpty();
    }

    private Installation buildInstallation() {
        Installation installation = new Installation();
        installation.setId(RandomString.generate());

        Map<String, String> additionalInformation = new HashMap<>();
        additionalInformation.put("key1", "value1");
        additionalInformation.put("key2", "value2");
        installation.setAdditionalInformation(additionalInformation);

        installation.setCreatedAt(new Date());
        installation.setUpdatedAt(new Date());
        return installation;
    }
}
