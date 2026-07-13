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

import io.gravitee.am.model.License;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.repository.management.AbstractManagementTest;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * @author GraviteeSource Team
 */
public class LicenseRepositoryTest extends AbstractManagementTest {

    @Autowired
    private LicenseRepository licenseRepository;

    private License buildLicense(String referenceId, ReferenceType referenceType, String value) {
        License license = new License();
        license.setReferenceId(referenceId);
        license.setReferenceType(referenceType);
        license.setLicense(value);
        license.setCreatedAt(new Date());
        license.setUpdatedAt(new Date());
        return license;
    }

    @Test
    public void testCreate() {
        License license = buildLicense("orga-1", ReferenceType.ORGANIZATION, "license-value");

        TestObserver<License> obs = licenseRepository.create(license).test();
        obs.awaitDone(10, TimeUnit.SECONDS);

        obs.assertComplete();
        obs.assertNoErrors();
        obs.assertValue(l -> "orga-1".equals(l.getReferenceId()));
        obs.assertValue(l -> ReferenceType.ORGANIZATION.equals(l.getReferenceType()));
        obs.assertValue(l -> "license-value".equals(l.getLicense()));
    }

    @Test
    public void testFindById() {
        License license = buildLicense("orga-2", ReferenceType.ORGANIZATION, "license-value");
        licenseRepository.create(license).blockingGet();

        TestObserver<License> obs = licenseRepository.findById("orga-2", ReferenceType.ORGANIZATION).test();
        obs.awaitDone(10, TimeUnit.SECONDS);

        obs.assertComplete();
        obs.assertNoErrors();
        obs.assertValue(l -> "orga-2".equals(l.getReferenceId()));
        obs.assertValue(l -> ReferenceType.ORGANIZATION.equals(l.getReferenceType()));
        obs.assertValue(l -> "license-value".equals(l.getLicense()));
    }

    @Test
    public void testNotFoundById() {
        TestObserver<License> obs = licenseRepository.findById("unknown", ReferenceType.ORGANIZATION).test();
        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertNoErrors();
        obs.assertNoValues();
    }

    @Test
    public void testFindAllEmpty() {
        TestObserver<List<License>> obs = licenseRepository.findAll().toList().test();
        obs.awaitDone(10, TimeUnit.SECONDS);

        obs.assertComplete();
        obs.assertNoErrors();
        obs.assertValue(List::isEmpty);
    }

    @Test
    public void testFindAll() {
        licenseRepository.create(buildLicense("orga-all-1", ReferenceType.ORGANIZATION, "license-1")).blockingGet();
        licenseRepository.create(buildLicense("orga-all-2", ReferenceType.ORGANIZATION, "license-2")).blockingGet();

        TestObserver<List<License>> obs = licenseRepository.findAll().toList().test();
        obs.awaitDone(10, TimeUnit.SECONDS);

        obs.assertComplete();
        obs.assertNoErrors();
        obs.assertValue(items -> items.size() == 2);
        obs.assertValue(items -> items.stream().map(License::getReferenceId).toList()
                .containsAll(List.of("orga-all-1", "orga-all-2")));
    }

    @Test
    public void testUpdate() {
        License license = buildLicense("orga-3", ReferenceType.ORGANIZATION, "license-value");
        licenseRepository.create(license).blockingGet();

        License updated = buildLicense("orga-3", ReferenceType.ORGANIZATION, "license-value-updated");

        TestObserver<License> obs = licenseRepository.update(updated).test();
        obs.awaitDone(10, TimeUnit.SECONDS);

        obs.assertComplete();
        obs.assertNoErrors();
        obs.assertValue(l -> "orga-3".equals(l.getReferenceId()));
        obs.assertValue(l -> "license-value-updated".equals(l.getLicense()));
    }

    @Test
    public void testDelete() {
        License license = buildLicense("orga-4", ReferenceType.ORGANIZATION, "license-value");
        licenseRepository.create(license).blockingGet();

        assertNotNull(licenseRepository.findById("orga-4", ReferenceType.ORGANIZATION).blockingGet());

        TestObserver<Void> obs = licenseRepository.delete("orga-4", ReferenceType.ORGANIZATION).test();
        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertComplete();
        obs.assertNoErrors();

        assertNull(licenseRepository.findById("orga-4", ReferenceType.ORGANIZATION).blockingGet());
    }

    @Test
    public void testCompositeKeyDistinctness() {
        // same referenceId, different referenceType must be two distinct rows
        licenseRepository.create(buildLicense("shared-id", ReferenceType.ORGANIZATION, "org-license")).blockingGet();
        licenseRepository.create(buildLicense("shared-id", ReferenceType.PLATFORM, "platform-license")).blockingGet();

        License org = licenseRepository.findById("shared-id", ReferenceType.ORGANIZATION).blockingGet();
        License platform = licenseRepository.findById("shared-id", ReferenceType.PLATFORM).blockingGet();

        assertNotNull(org);
        assertNotNull(platform);
        assertEquals("org-license", org.getLicense());
        assertEquals("platform-license", platform.getLicense());
    }
}
