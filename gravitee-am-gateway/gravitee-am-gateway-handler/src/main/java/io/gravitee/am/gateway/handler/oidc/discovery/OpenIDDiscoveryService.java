package io.gravitee.am.gateway.handler.oidc.discovery;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface OpenIDDiscoveryService {

    OpenIDProviderMetadata getConfiguration();
}
