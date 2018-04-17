package io.gravitee.am.gateway.handler.oidc.discovery.impl;

import io.gravitee.am.gateway.handler.oidc.discovery.OpenIDDiscoveryService;
import io.gravitee.am.gateway.handler.oidc.discovery.OpenIDProviderMetadata;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class OpenIDDiscoveryServiceImpl implements OpenIDDiscoveryService {

    @Override
    public OpenIDProviderMetadata getConfiguration() {
        return new OpenIDProviderMetadata();
    }
}
