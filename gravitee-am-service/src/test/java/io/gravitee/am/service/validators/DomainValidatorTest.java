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
package io.gravitee.am.service.validators;

import io.gravitee.am.model.Domain;
import io.gravitee.am.model.VirtualHost;
import io.gravitee.am.service.exception.InvalidDomainException;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.junit.Assert.*;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
public class DomainValidatorTest {

    @Test
    public void validate() {

        Domain domain = getValidDomain();

        Throwable throwable = DomainValidator.validate(domain, emptyList()).blockingGet();

        assertNull(throwable);
    }

    @Test
    public void validate_invalidEmptyPath() {

        Domain domain = getValidDomain();
        domain.setPath("");

        Throwable throwable = DomainValidator.validate(domain, emptyList()).blockingGet();

        // Empty path is replaced with '/' but '/' is not allowed in context-path mode.
        assertNotNull(throwable);
        assertTrue(throwable instanceof InvalidDomainException);
    }

    @Test
    public void validate_invalidNullPath() {

        Domain domain = getValidDomain();
        domain.setPath(null);

        Throwable throwable = DomainValidator.validate(domain, emptyList()).blockingGet();

        // Null path is replaced with '/' but '/' is not allowed in context-path mode.
        assertNotNull(throwable);
        assertTrue(throwable instanceof InvalidDomainException);
    }

    @Test
    public void validate_multipleSlashesPath() {

        Domain domain = getValidDomain();
        domain.setPath("/////test////");

        Throwable throwable = DomainValidator.validate(domain, emptyList()).blockingGet();

        // Multiple '/' in path should be removed.
        assertNull(throwable);
    }

    @Test
    public void validate_notStartingSlashPath() {

        Domain domain = getValidDomain();
        domain.setPath("test");

        Throwable throwable = DomainValidator.validate(domain, emptyList()).blockingGet();

        // '/' should be automatically append.
        assertNull(throwable);
    }

    @Test
    public void validate_invalidName() {

        Domain domain = getValidDomain();
        domain.setName("Invalid/Name");

        Throwable throwable = DomainValidator.validate(domain, emptyList()).blockingGet();

        assertNotNull(throwable);
        assertTrue(throwable instanceof InvalidDomainException);
    }

    @Test
    public void validate_vhostModeRequired() {

        Domain domain = getValidDomain();
        domain.setVhostMode(false);

        Throwable throwable = DomainValidator.validate(domain, singletonList("constraint.gravitee.io")).blockingGet();

        assertNotNull(throwable);
        assertTrue(throwable instanceof InvalidDomainException);
    }

    @Test
    public void validate_invalidEmptyVhosts() {

        Domain domain = getValidDomain();
        domain.setVhostMode(true);
        domain.setVhosts(emptyList());

        Throwable throwable = DomainValidator.validate(domain, emptyList()).blockingGet();

        assertNotNull(throwable);
        assertTrue(throwable instanceof InvalidDomainException);
    }

    @Test
    public void validate_invalidNullVhosts() {

        Domain domain = getValidDomain();
        domain.setVhostMode(true);
        domain.setVhosts(null);

        Throwable throwable = DomainValidator.validate(domain, emptyList()).blockingGet();

        assertNotNull(throwable);
        assertTrue(throwable instanceof InvalidDomainException);
    }

    @Test
    public void validate_invalidMultipleVhostFlaggedWithOverrideEntrypoint() {

        Domain domain = getValidDomain();
        domain.setVhostMode(true);
        VirtualHost vhost = new VirtualHost();
        vhost.setHost("valid.host.gravitee.io");
        vhost.setPath("/validVhostPath");
        vhost.setOverrideEntrypoint(true);
        domain.getVhosts().add(vhost);

        Throwable throwable = DomainValidator.validate(domain, emptyList()).blockingGet();

        assertNotNull(throwable);
        assertTrue(throwable instanceof InvalidDomainException);
    }


    @Test
    public void validate_invalidNoVhostFlaggedWithOverrideEntrypoint() {

        Domain domain = getValidDomain();
        domain.setVhostMode(true);
        domain.getVhosts().get(0).setOverrideEntrypoint(false);

        Throwable throwable = DomainValidator.validate(domain, emptyList()).blockingGet();

        assertNotNull(throwable);
        assertTrue(throwable instanceof InvalidDomainException);
    }

    @Test
    public void validate_vhosts() {

        Domain domain = getValidDomain();
        domain.setVhostMode(true);

        Throwable throwable = DomainValidator.validate(domain, emptyList()).blockingGet();

        assertNull(throwable);
    }

    private Domain getValidDomain() {

        Domain domain = new Domain();
        domain.setName("Domain Test");
        domain.setPath("/validPath");
        domain.setVhostMode(false);

        ArrayList<VirtualHost> vhosts = new ArrayList<>();
        VirtualHost vhost = new VirtualHost();
        vhost.setHost("valid.host.gravitee.io");
        vhost.setPath("/validVhostPath");
        vhost.setOverrideEntrypoint(true);
        vhosts.add(vhost);

        domain.setVhosts(vhosts);

        return domain;
    }
}