package io.gravitee.am.gateway.handler.openid.discovery.impl;

import io.gravitee.am.gateway.handler.openid.discovery.OpenIDProviderMetadata;
import io.gravitee.am.gateway.handler.openid.discovery.OpenIDDiscoveryService;

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
