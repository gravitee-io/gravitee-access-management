package io.gravitee.am.gateway.idp.core.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.gateway.idp.core.IdentityProviderConfigurationFactory;
import io.gravitee.am.identityprovider.api.IdentityProviderConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class IdentityProviderConfigurationFactoryImpl implements IdentityProviderConfigurationFactory {

    private final Logger logger = LoggerFactory.getLogger(IdentityProviderConfigurationFactoryImpl.class);

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public <T extends IdentityProviderConfiguration> T create(Class<T> clazz, String content) {
        logger.debug("Create a new instance of identity provider configuration for class: {}", clazz.getName());

        try {
            return objectMapper.readValue(content, clazz);
        } catch (IOException ioe) {
            logger.error("Unable to create an identity provider configuration", ioe);
            return null;
        }
    }
}
