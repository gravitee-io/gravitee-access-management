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
import io.gravitee.am.service.validators.domain.DomainValidator;
import io.gravitee.am.service.validators.domain.DomainValidatorImpl;
import io.gravitee.am.service.validators.path.PathValidatorImpl;
import io.gravitee.am.service.validators.virtualhost.VirtualHostValidatorImpl;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.junit.Assert.*;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
public class DomainValidatorTest {

    private DomainValidator domainValidator;

    @Before
    public void before(){
        final PathValidatorImpl pathValidator = new PathValidatorImpl();
        domainValidator = new DomainValidatorImpl(pathValidator, new VirtualHostValidatorImpl(pathValidator));
    }

    @Test
    public void validate() {
        Domain domain = getValidDomain();
        domainValidator.validate(domain, emptyList()).test().assertNoErrors();
    }

    @Test
    public void validate_invalidEmptyPath() {
        Domain domain = getValidDomain();
        domain.setPath("");

        // Empty path is replaced with '/' but '/' is not allowed in context-path mode.
        TestObserver<Void> test = domainValidator.validate(domain, emptyList()).test().assertError(InvalidDomainException.class);
    }

    @Test
    public void validate_invalidNullPath() {
        Domain domain = getValidDomain();
        domain.setPath(null);

        // Null path is replaced with '/' but '/' is not allowed in context-path mode.
        domainValidator.validate(domain, emptyList()).test().assertError(InvalidDomainException.class);
    }

    @Test
    public void validate_multipleSlashesPath() {
        Domain domain = getValidDomain();
        domain.setPath("/////test////");

        // Multiple '/' in path should be removed.
        domainValidator.validate(domain, emptyList()).test().assertNoErrors();
    }

    @Test
    public void validate_notStartingSlashPath() {
        Domain domain = getValidDomain();
        domain.setPath("test");

        // '/' should be automatically append.
        domainValidator.validate(domain, emptyList()).test().assertNoErrors();
    }

    @Test
    public void validate_invalidName() {
        Domain domain = getValidDomain();
        domain.setName("Invalid/Name");

        domainValidator.validate(domain, emptyList()).test().assertError(InvalidDomainException.class);
    }

    @Test
    public void validate_vhostModeRequired() {
        Domain domain = getValidDomain();
        domain.setVhostMode(false);

        domainValidator.validate(domain, singletonList("constraint.gravitee.io")).test().assertError(InvalidDomainException.class);
    }

    @Test
    public void validate_invalidEmptyVhosts() {
        Domain domain = getValidDomain();
        domain.setVhostMode(true);
        domain.setVhosts(emptyList());

        domainValidator.validate(domain, emptyList()).test().assertError(InvalidDomainException.class);
    }

    @Test
    public void validate_invalidNullVhosts() {
        Domain domain = getValidDomain();
        domain.setVhostMode(true);
        domain.setVhosts(null);

        domainValidator.validate(domain, emptyList()).test().assertError(InvalidDomainException.class);
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

        domainValidator.validate(domain, emptyList()).test().assertError(InvalidDomainException.class);
    }


    @Test
    public void validate_invalidNoVhostFlaggedWithOverrideEntrypoint() {
        Domain domain = getValidDomain();
        domain.setVhostMode(true);
        domain.getVhosts().get(0).setOverrideEntrypoint(false);

        domainValidator.validate(domain, emptyList()).test().assertError(InvalidDomainException.class);
    }

    @Test
    public void validate_vhosts() {
        Domain domain = getValidDomain();
        domain.setVhostMode(true);

        domainValidator.validate(domain, emptyList()).test().assertNoErrors();
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