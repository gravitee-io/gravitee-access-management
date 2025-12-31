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
import io.gravitee.am.service.exception.InvalidVirtualHostException;
import io.gravitee.am.service.validators.path.PathValidatorImpl;
import io.gravitee.am.service.validators.virtualhost.VirtualHostValidator;
import io.gravitee.am.service.validators.virtualhost.VirtualHostValidatorImpl;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
public class VirtualHostValidatorTest {

    private VirtualHostValidator virtualHostValidator;

    @Before
    public void before(){
        virtualHostValidator = new VirtualHostValidatorImpl(new PathValidatorImpl());
    }

    @Test
    public void validate() {

        VirtualHost vhost = getValidVirtualHost();

        virtualHostValidator.validate(vhost, emptyList()).test().assertNoErrors();
    }

    @Test
    public void validate_emptyPath() {

        VirtualHost vhost = getValidVirtualHost();
        vhost.setPath("");

        virtualHostValidator.validate(vhost, emptyList()).test().assertNoErrors();
    }

    @Test
    public void validate_nullPath() {

        VirtualHost vhost = getValidVirtualHost();
        vhost.setPath(null);

        virtualHostValidator.validate(vhost, emptyList()).test().assertNoErrors();
    }

    @Test
    public void validate_multipleSlashesPath() {

        VirtualHost vhost = getValidVirtualHost();
        vhost.setPath("/////test////");

        // Multiple '/' in path should be removed.
        virtualHostValidator.validate(vhost, emptyList()).test().assertNoErrors();
    }

    @Test
    public void validate_notStartingSlashPath() {

        VirtualHost vhost = getValidVirtualHost();
        vhost.setPath("test");

        // '/' should be automatically append.
        virtualHostValidator.validate(vhost, emptyList()).test().assertNoErrors();
    }

    @Test
    public void validate_invalidHost() {

        VirtualHost vhost = getValidVirtualHost();
        vhost.setHost("Invalid/Host");

        virtualHostValidator.validate(vhost, emptyList()).test().assertError(InvalidVirtualHostException.class);
    }

    @Test
    public void validate_invalidEmptyHost() {

        VirtualHost vhost = getValidVirtualHost();
        vhost.setHost("");

        virtualHostValidator.validate(vhost, emptyList()).test().assertError(InvalidVirtualHostException.class);
    }

    @Test
    public void validate_invalidNullHost() {

        VirtualHost vhost = getValidVirtualHost();
        vhost.setHost(null);

        virtualHostValidator.validate(vhost, emptyList()).test().assertError(InvalidVirtualHostException.class);
    }

    @Test
    public void validate_invalidHostPort() {

        VirtualHost vhost = getValidVirtualHost();
        vhost.setHost("locahost:null");

        virtualHostValidator.validate(vhost, emptyList()).test().assertError(InvalidVirtualHostException.class);
    }

    @Test
    public void validate_invalidEmptyHostPort() {

        VirtualHost vhost = getValidVirtualHost();
        vhost.setHost("locahost:");

        virtualHostValidator.validate(vhost, emptyList()).test().assertError(InvalidVirtualHostException.class);
    }

    @Test
    public void validate_invalidHostPortTooBig() {

        VirtualHost vhost = getValidVirtualHost();
        vhost.setHost("locahost:70000");

        virtualHostValidator.validate(vhost, emptyList()).test().assertError(InvalidVirtualHostException.class);
    }

    @Test
    public void validate_noHostWithPort() {

        VirtualHost vhost = getValidVirtualHost();
        vhost.setHost(":8080");

        virtualHostValidator.validate(vhost, emptyList()).test().assertError(InvalidVirtualHostException.class);
    }

    @Test
    public void validate_hostEqualsToDomainConstraint() {

        VirtualHost vhost = getValidVirtualHost();

        virtualHostValidator.validate(vhost, singletonList(vhost.getHost())).test().assertNoErrors();
    }

    @Test
    public void validate_hostSubDomainOfDomainConstraint() {

        VirtualHost vhost = getValidVirtualHost();
        String domainConstraint = vhost.getHost();
        vhost.setHost("level2.level1." + domainConstraint);

        virtualHostValidator.validate(vhost, singletonList(domainConstraint)).test().assertNoErrors();
    }

    @Test
    public void validate_hostSubDomainOfOneOfDomainConstraints() {

        VirtualHost vhost = getValidVirtualHost();
        String domainConstraint = vhost.getHost();
        vhost.setHost("level2.level1." + domainConstraint);

        virtualHostValidator.validate(vhost, Arrays.asList("test.gravitee.io", "other.gravitee.io", domainConstraint)).test().assertNoErrors();;
    }

    @Test
    public void validate_notASubDomain() {

        VirtualHost vhost = getValidVirtualHost();

        virtualHostValidator.validate(vhost, Arrays.asList("test.gravitee.io", "other.gravitee.io")).test().assertError(InvalidVirtualHostException.class);
    }

    @Test
    public void validateDomainPathAgainstVhosts() {

        Domain domain = getValidDomain();

        Domain otherDomain = getValidDomain();
        otherDomain.setId("otherDomain");
        otherDomain.setVhostMode(true);
        otherDomain.getVhosts().clear();
        VirtualHost vhost1 = getValidVirtualHost();
        VirtualHost vhost2 = getValidVirtualHost();
        vhost2.setPath("/validVhostPath2");
        otherDomain.getVhosts().add(vhost1);
        otherDomain.getVhosts().add(vhost2);

        List<Domain> otherDomains = new ArrayList<>();
        otherDomains.add(otherDomain);

        virtualHostValidator.validateDomainVhosts(domain, otherDomains).test().assertNoErrors();
    }

    @Test
    public void validateDomainPathAgainstVhosts_vhostWithSlash() {

        Domain domain = getValidDomain();

        Domain otherDomain = getValidDomain();
        otherDomain.setId("otherDomain");
        otherDomain.setVhostMode(true);

        List<Domain> otherDomains = new ArrayList<>();
        otherDomains.add(otherDomain);
        otherDomain.getVhosts().get(0).setPath("/"); // "/" path in vhost mode should not be considered as overlap.

        virtualHostValidator.validateDomainVhosts(domain, otherDomains).test().assertNoErrors();
    }


    @Test
    public void validateDomainVhosts() {

        Domain domain = getValidDomain();
        domain.setVhostMode(true);

        Domain otherDomain = getValidDomain();
        otherDomain.setId("otherDomain");
        otherDomain.setVhostMode(true);
        otherDomain.getVhosts().clear();
        VirtualHost vhost1 = getValidVirtualHost();
        VirtualHost vhost2 = getValidVirtualHost();
        vhost2.setPath("/validVhostPath2");
        otherDomain.getVhosts().add(vhost1);
        otherDomain.getVhosts().add(vhost2);

        List<Domain> otherDomains = new ArrayList<>();
        otherDomains.add(otherDomain);

        virtualHostValidator.validateDomainVhosts(domain, otherDomains).test().assertNoErrors();
    }

    @Test
    public void validateDomainVhosts_pathOverlap() {

        Domain domain = getValidDomain();

        Domain otherDomain = getValidDomain();
        otherDomain.setId("otherDomain");
        otherDomain.setVhostMode(true);

        List<Domain> otherDomains = new ArrayList<>();
        otherDomains.add(otherDomain);

        virtualHostValidator.validateDomainVhosts(domain, otherDomains).test().assertError(InvalidVirtualHostException.class);;
    }

    @Test
    public void validateDomainVhosts_pathIsOverlapped() {

        Domain domain = getValidDomain();
        domain.setPath(("/test/subPath")); // '/test/subPath' is overlapped by vhost '/test'

        Domain otherDomain = getValidDomain();
        otherDomain.setId("otherDomain");
        otherDomain.setVhostMode(true);

        List<Domain> otherDomains = new ArrayList<>();
        otherDomains.add(otherDomain);

        virtualHostValidator.validateDomainVhosts(domain, otherDomains).test().assertError(InvalidVirtualHostException.class);
    }

    @Test
    public void validateDomainVhosts_vhostPathSlashOverlap() {

        Domain domain = getValidDomain();
        domain.setVhostMode(true);
        domain.getVhosts().get(0).setPath("/"); // same host, "/" path is allowed as fallback

        Domain otherDomain = getValidDomain();
        otherDomain.setId("otherDomain");
        otherDomain.setVhostMode(true);

        List<Domain> otherDomains = new ArrayList<>();
        otherDomains.add(otherDomain);

        // Fallback pattern: "/" can coexist with specific paths like "/test"
        virtualHostValidator.validateDomainVhosts(domain, otherDomains).test().assertNoErrors();
    }

    @Test
    public void validateDomainVhosts_vhostPathSlashIsOverlapped() {

        Domain domain = getValidDomain();
        domain.setVhostMode(true);

        Domain otherDomain = getValidDomain();
        otherDomain.setId("otherDomain");
        otherDomain.setVhostMode(true);
        otherDomain.getVhosts().get(0).setPath("/"); // same host, "/" path is allowed as fallback

        List<Domain> otherDomains = new ArrayList<>();
        otherDomains.add(otherDomain);

        // Fallback pattern: a specific path "/test" can coexist with "/" from another domain
        virtualHostValidator.validateDomainVhosts(domain, otherDomains).test().assertNoErrors();
    }

    @Test
    public void validateDomainVhosts_twoDomainsBothWithSlashPath() {

        Domain domain = getValidDomain();
        domain.setVhostMode(true);
        domain.getVhosts().get(0).setPath("/"); // First domain with "/" path

        Domain otherDomain = getValidDomain();
        otherDomain.setId("otherDomain");
        otherDomain.setVhostMode(true);
        otherDomain.getVhosts().get(0).setPath("/"); // The second domain also with "/" path

        List<Domain> otherDomains = new ArrayList<>();
        otherDomains.add(otherDomain);

        // Two domains with "/" on the same host is still not allowed (exact duplicate)
        virtualHostValidator.validateDomainVhosts(domain, otherDomains).test().assertError(InvalidVirtualHostException.class);
    }

    @Test
    public void validateDomainVhosts_nonSlashPathOverlapStillBlocked() {

        Domain domain = getValidDomain();
        domain.setVhostMode(true);
        domain.getVhosts().get(0).setPath("/api"); // Path /api

        Domain otherDomain = getValidDomain();
        otherDomain.setId("otherDomain");
        otherDomain.setVhostMode(true);
        otherDomain.getVhosts().get(0).setPath("/api/v1"); // Path /api/v1 overlaps with /api

        List<Domain> otherDomains = new ArrayList<>();
        otherDomains.add(otherDomain);

        // Non-slash path overlaps are still blocked (only "/" fallback pattern is allowed)
        virtualHostValidator.validateDomainVhosts(domain, otherDomains).test().assertError(InvalidVirtualHostException.class);
    }

    @Test
    public void validateDomainVhosts_multipleDomainsDifferentSpecificPathsWithSlashFallback() {

        Domain domain1 = getValidDomain();
        domain1.setId("domain1");
        domain1.setVhostMode(true);
        domain1.getVhosts().get(0).setPath("/api");

        Domain domain2 = getValidDomain();
        domain2.setId("domain2");
        domain2.setVhostMode(true);
        domain2.getVhosts().get(0).setPath("/auth");

        Domain domainFallback = getValidDomain();
        domainFallback.setId("domainFallback");
        domainFallback.setVhostMode(true);
        domainFallback.getVhosts().get(0).setPath("/"); // Fallback domain

        List<Domain> otherDomains = new ArrayList<>();
        otherDomains.add(domain1);
        otherDomains.add(domain2);

        // Fallback domain with "/" should work with multiple specific paths
        virtualHostValidator.validateDomainVhosts(domainFallback, otherDomains).test().assertNoErrors();
    }

    private VirtualHost getValidVirtualHost() {

        VirtualHost vhost = new VirtualHost();
        vhost.setHost("valid.host.gravitee.io");
        vhost.setPath("/validVhostPath");

        return vhost;
    }

    private Domain getValidDomain() {

        Domain domain = new Domain();
        domain.setName("Domain Test");
        domain.setPath("/test");
        domain.setVhostMode(false);

        ArrayList<VirtualHost> vhosts = new ArrayList<>();
        VirtualHost vhost = new VirtualHost();
        vhost.setHost("valid.host.gravitee.io");
        vhost.setPath("/test");
        vhosts.add(vhost);

        domain.setVhosts(vhosts);

        return domain;
    }
}
