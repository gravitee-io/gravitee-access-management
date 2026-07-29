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
package io.gravitee.am.service.impl.application;

import io.gravitee.am.common.env.CloudProperties;
import io.gravitee.am.common.utils.PathUtils;
import io.gravitee.am.common.web.UriBuilder;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Entrypoint;
import io.gravitee.am.model.VirtualHost;
import io.gravitee.am.plugins.dataplane.core.DataPlaneRegistry;
import io.gravitee.am.repository.management.api.DomainRepository;
import io.gravitee.am.service.DomainReadService;
import io.gravitee.am.service.EntryPointManager;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.vertx.core.MultiMap;
import lombok.CustomLog;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Optional.ofNullable;

@CustomLog
@Component
public class DomainReadServiceImpl implements DomainReadService {
    private static final Pattern SCHEME_PATTERN = Pattern.compile("^(https?://).*$");

    private final String gatewayUrl;
    private final DomainRepository domainRepository;
    private final DataPlaneRegistry dataPlaneRegistry;
    private final EntryPointManager entryPointManager;
    private final Environment springEnvironment;

    public DomainReadServiceImpl(@Lazy DomainRepository domainRepository,
                                 @Lazy DataPlaneRegistry dataPlaneRegistry,
                                 @Lazy EntryPointManager entryPointManager,
                                 Environment springEnvironment,
                                 @Value("${gateway.url:http://localhost:8092}") String gatewayUrl) {
        this.dataPlaneRegistry = dataPlaneRegistry;
        this.domainRepository = domainRepository;
        this.entryPointManager = entryPointManager;
        this.springEnvironment = springEnvironment;
        this.gatewayUrl = gatewayUrl;
    }


    @Override
    public Maybe<Domain> findById(String id) {
        log.debug("Find domain by ID: {}", id);
        return domainRepository.findById(id)
                .onErrorResumeNext(ex -> {
                    log.error("An error occurred while trying to find a domain using its ID: {}", id, ex);
                    return Maybe.error(new TechnicalManagementException(
                            String.format("An error occurred while trying to find a domain using its ID: %s", id), ex));
                });
    }

    @Override
    public Flowable<Domain> listAll() {
        log.debug("List all domains");
        return domainRepository.findAll()
                .onErrorResumeNext(ex -> {
                    log.error("An error occurred while trying to list all domains", ex);
                    return Flowable.error(new TechnicalManagementException("An error occurred while trying to list all domains", ex));
                });
    }

    @Override
    public String buildUrl(Domain domain, String path, MultiMap queryParams, String requestOrigin) {
        String entryPoint = resolveEntryPoint(domain, requestOrigin);

        if (entryPoint != null && entryPoint.endsWith("/")) {
            entryPoint = entryPoint.substring(0, entryPoint.length() - 1);
        }

        String uri = null;

        if (domain.isVhostMode()) {
            // Try to generate uri using defined virtual hosts.
            Matcher matcher = SCHEME_PATTERN.matcher(entryPoint);
            String scheme = "http";
            if (matcher.matches()) {
                scheme = matcher.group(1);
            }

            for (VirtualHost vhost : domain.getVhosts()) {
                if (vhost.isOverrideEntrypoint()) {
                    uri = scheme + vhost.getHost() + vhost.getPath() + path;
                    break;
                }
            }
        }

        if (uri == null) {
            uri = entryPoint + PathUtils.sanitize(domain.getPath() + path);
        }

        if (queryParams != null && !queryParams.isEmpty()) {
            uri = UriBuilder.fromURIString(uri).parameters(queryParams).buildString();
        }

        return uri;
    }

    // In managed cloud the environment's entrypoint is this domain's gateway hostname; everywhere else,
    // and until Cockpit has synced one, the data plane url stands.
    private String resolveEntryPoint(Domain domain, String requestOrigin) {
        if (CloudProperties.isManagedCloudEnabled(springEnvironment)) {
            Optional<String> entrypointUrl = matchingEntrypoint(domain, requestOrigin)
                    .or(() -> entryPointManager.findPrimaryByEnvironmentId(domain.getReferenceId()))
                    .map(Entrypoint::getUrl);
            if (entrypointUrl.isPresent()) {
                return entrypointUrl.get();
            }
        }
        return ofNullable(dataPlaneRegistry.getDescription(domain).gatewayUrl()).orElse(gatewayUrl);
    }

    /**
     * The environment entrypoint the user actually reached us on, so an environment with several hosts
     * mails links back to the one in the address bar rather than an arbitrary pick.
     * <p>
     * Only ever returns a stored entrypoint, never the caller's string: an unrecognised origin is a
     * forged {@code Host} header away from mailing a password-reset token to somebody else's domain.
     */
    private Optional<Entrypoint> matchingEntrypoint(Domain domain, String requestOrigin) {
        if (requestOrigin == null || requestOrigin.isBlank()) {
            return Optional.empty();
        }
        return entryPointManager.findAllByEnvironmentId(domain.getReferenceId()).stream()
                .filter(entrypoint -> sameOrigin(entrypoint.getUrl(), requestOrigin))
                .findFirst();
    }

    private static boolean sameOrigin(String entrypointUrl, String requestOrigin) {
        return entrypointUrl != null && stripTrailingSlash(entrypointUrl).equalsIgnoreCase(stripTrailingSlash(requestOrigin));
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

}
