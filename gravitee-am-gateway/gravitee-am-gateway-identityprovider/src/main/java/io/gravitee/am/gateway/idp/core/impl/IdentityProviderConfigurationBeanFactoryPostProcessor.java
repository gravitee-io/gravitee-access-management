package io.gravitee.am.gateway.idp.core.impl;

import io.gravitee.am.identityprovider.api.IdentityProviderConfiguration;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class IdentityProviderConfigurationBeanFactoryPostProcessor implements BeanFactoryPostProcessor {

    private final IdentityProviderConfiguration configuration;

    IdentityProviderConfigurationBeanFactoryPostProcessor(IdentityProviderConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory configurableListableBeanFactory) throws BeansException {
        DefaultListableBeanFactory beanFactory = (DefaultListableBeanFactory) configurableListableBeanFactory;
        beanFactory.registerSingleton("configuration", configuration);
    }
}
