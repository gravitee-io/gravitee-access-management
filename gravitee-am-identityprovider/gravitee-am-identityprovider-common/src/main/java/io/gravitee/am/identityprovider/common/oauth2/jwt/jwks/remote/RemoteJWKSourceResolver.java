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
package io.gravitee.am.identityprovider.common.oauth2.jwt.jwks.remote;

import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.util.DefaultResourceRetriever;
import com.nimbusds.jose.util.health.HealthStatus;
import io.gravitee.am.identityprovider.api.oidc.jwt.JWKSourceResolver;
import lombok.extern.slf4j.Slf4j;

import java.net.URL;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Slf4j
public class RemoteJWKSourceResolver<C extends SecurityContext> implements JWKSourceResolver<C> {

    private final String url;
    private int connectionTimeout;
    private int readTimeout;

    public RemoteJWKSourceResolver(String url, int connectionTimeout, int readTimeout) {
        this.url = url;
        this.connectionTimeout = connectionTimeout;
        this.readTimeout = readTimeout;
    }

    public RemoteJWKSourceResolver(String url) {
        this.url = url;
    }

    @Override
    @SuppressWarnings("unchecked")
    public JWKSource<C> resolve() {
        DefaultResourceRetriever retriever;
        if (connectionTimeout == 0 || readTimeout == 0) {
            retriever = new DefaultResourceRetriever();
        } else {
            retriever = new DefaultResourceRetriever(connectionTimeout, readTimeout);
        }
        try {
            return (JWKSource<C>) JWKSourceBuilder.create(new URL(url), retriever)
                    .outageTolerant(true)
                    .retrying(true)
                    .healthReporting((healthReport) -> {
                        if (HealthStatus.HEALTHY.equals(healthReport.getHealthStatus())) {
                            log.debug("JWK Source healthy at {}: {}", healthReport.getTimestamp(), healthReport.getHealthStatus());
                            return;
                        }
                        log.warn("JWK Source degraded at {}: ", healthReport.getTimestamp(), healthReport.getException());
                    })
                    .build();

        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }
}
