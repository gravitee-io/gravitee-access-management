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

import com.google.common.net.InternetDomainName;
import io.gravitee.am.common.utils.PathUtils;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.VirtualHost;
import io.gravitee.am.service.exception.InvalidVirtualHostException;
import io.reactivex.Completable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
public class VirtualHostValidator {

    public static Completable validate(VirtualHost vhost, List<String> domainRestrictions) {

        String host = vhost.getHost();

        if (host == null || "".equals(host)) {
            return Completable.error(new InvalidVirtualHostException("Host is required"));
        }

        String[] split = host.split(":");
        String hostWithoutPort = split[0];

        if (!InternetDomainName.isValid(hostWithoutPort)) {
            return Completable.error(new InvalidVirtualHostException("Host [" + hostWithoutPort + "] is invalid"));
        }

        if(!isValidDomainOrSubDomain(hostWithoutPort, domainRestrictions)) {
            return Completable.error(new InvalidVirtualHostException("Host [" + hostWithoutPort + "] must be a subdomain of " + domainRestrictions));
        }

        if (host.contains(":") && split.length < 2) {
            return Completable.error(new InvalidVirtualHostException("Host port for [" + host + "] is invalid"));
        }

        if (split.length > 1) {
            try {
                int port = Integer.parseInt(split[1]);
                if (port < 0 || port > 65535) {
                    return Completable.error(new InvalidVirtualHostException("Host port [" + port + "] is invalid"));
                }
            } catch (NumberFormatException nfe) {
                return Completable.error(new InvalidVirtualHostException("Host port for [" + host + "] is invalid"));
            }
        }

        return PathValidator.validate(vhost.getPath());
    }

    public static Completable validateDomainVhosts(Domain domain, Set<Domain> domains) {

        List<VirtualHost> otherVhosts = domains.stream()
                .filter(d -> !d.getId().equals(domain.getId()))
                .filter(Domain::isVhostMode)
                .flatMap(d -> d.getVhosts().stream())
                .collect(Collectors.toList());

        List<String> otherPaths = domains.stream()
                .filter(d -> !d.getId().equals(domain.getId()))
                .filter(d -> !d.isVhostMode())
                .map(Domain::getPath)
                .collect(Collectors.toList());

        List<String> paths;

        if (domain.isVhostMode()) {
            // Get paths of all other domains on the same host.
            paths = domain.getVhosts().stream()
                    .flatMap(virtualHost -> otherVhosts.stream()
                            .filter(otherVhost -> virtualHost.getHost().equals(otherVhost.getHost())))
                    .map(VirtualHost::getPath).collect(Collectors.toList());

            for (VirtualHost vhost : domain.getVhosts()) {

                List<String> pathsToCheck = paths;

                if(!vhost.getPath().equals("/")) {
                    // Check against other path of domains in context path mode when trying to use '/'.
                    pathsToCheck = new ArrayList<>(paths);
                    pathsToCheck.addAll(otherPaths);
                }

                // Check is the domain context path overlap a path of another domain.
                for (String otherPath : pathsToCheck) {
                    if (overlap(vhost.getPath(), otherPath)) {
                        return Completable.error(new InvalidVirtualHostException("Path [" + vhost.getPath() + "] overlap path defined in another security domain"));
                    }

                    if (overlap(otherPath, vhost.getPath())) {
                        return Completable.error(new InvalidVirtualHostException("Path [" + vhost.getPath() + "] is overlapped by another security domain"));
                    }
                }
            }
        } else {
            // Domain listen on every hosts. Need to check if path overlap (or is overlapped) with all other domain path (including vhost path).
            paths = new ArrayList<>(otherPaths);
            paths.addAll(otherVhosts.stream().map(VirtualHost::getPath).filter(path -> !"/".equals(path)).collect(Collectors.toList()));

            // Check is the domain context path overlap a path of another domain.
            for (String otherPath : paths) {
                if (overlap(domain.getPath(), otherPath)) {
                    return Completable.error(new InvalidVirtualHostException("Path [" + domain.getPath() + "] overlap path defined in another security domain"));
                }

                if (overlap(otherPath, domain.getPath())) {
                    return Completable.error(new InvalidVirtualHostException("Path [" + domain.getPath() + "] is overlapped by another security domain"));
                }
            }
        }

        return Completable.complete();
    }

    private static boolean overlap(String path, String other) {

        String sanitizedPath = PathUtils.sanitize(path);
        String sanitizedOther = PathUtils.sanitize(other);

        sanitizedPath = sanitizedPath.equals("/") ? sanitizedPath : sanitizedPath + "/";
        sanitizedOther = sanitizedOther.equals("/") ? sanitizedOther : sanitizedOther + "/";

        return sanitizedOther.startsWith(sanitizedPath);
    }

    public static boolean isValidDomainOrSubDomain(String domain, List<String> domainRestrictions) {

        boolean isSubDomain = false;

        if (domainRestrictions.isEmpty()) {
            return true;
        }

        for (String domainRestriction : domainRestrictions) {

            InternetDomainName domainIDN = InternetDomainName.from(domain);
            InternetDomainName parentIDN = InternetDomainName.from(domainRestriction);

            if(domainIDN.equals(parentIDN)) {
                return true;
            }

            while (!isSubDomain && domainIDN.hasParent()) {

                isSubDomain = parentIDN.equals(domainIDN);
                domainIDN = domainIDN.parent();
            }

            if (isSubDomain) {
                break;
            }
        }

        return isSubDomain;
    }
}
