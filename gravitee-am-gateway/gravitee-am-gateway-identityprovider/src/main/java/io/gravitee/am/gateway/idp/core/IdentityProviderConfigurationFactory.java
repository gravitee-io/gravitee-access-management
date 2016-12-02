package io.gravitee.am.gateway.idp.core;

import io.gravitee.am.identityprovider.api.IdentityProviderConfiguration;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface IdentityProviderConfigurationFactory {

    <T extends IdentityProviderConfiguration> T create(Class<T> clazz, String content);
}
