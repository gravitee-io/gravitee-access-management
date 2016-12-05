package io.gravitee.am.gateway.handler.oauth2.view;

import io.gravitee.am.definition.oauth2.OAuth2Domain;
import io.gravitee.common.spring.factory.AbstractAutowiringFactoryBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.templateresolver.FileTemplateResolver;
import org.thymeleaf.templateresolver.ITemplateResolver;

import java.io.File;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ThymeleafTemplateResolverFactory extends AbstractAutowiringFactoryBean<ITemplateResolver> {

    private final Logger logger = LoggerFactory.getLogger(ThymeleafTemplateResolverFactory.class);

    @Autowired
    private OAuth2Domain domain;

    @Override
    protected ITemplateResolver doCreateInstance() throws Exception {
        if (domain.getTemplate() == null || domain.getTemplate().getPath() == null) {
            logger.debug("View templating has not been overridden with custom view, returning default views.");
            return defaultTemplateResolver();
        }

        ITemplateResolver resolver = overrideTemplateResolver();

        return (resolver != null) ? resolver : defaultTemplateResolver();
    }

    private ITemplateResolver overrideTemplateResolver() {
        logger.info("Loading custom template from {}", domain.getTemplate().getPath());
        String templatePath = domain.getTemplate().getPath();

        if (new File(templatePath).exists()) {
            FileTemplateResolver templateResolver = new FileTemplateResolver();
            templateResolver.setPrefix(domain.getTemplate().getPath());
            templateResolver.setSuffix(".html");
            templateResolver.setTemplateMode("HTML");
            return templateResolver;
        }

        logger.warn("Custom template {} does not exist, skipping.", domain.getTemplate().getPath());
        return null;
    }

    private ITemplateResolver defaultTemplateResolver() {
        ClassLoaderTemplateResolver templateResolver = new ClassLoaderTemplateResolver();
        templateResolver.setPrefix("/views/");
        templateResolver.setSuffix(".html");
        templateResolver.setTemplateMode("HTML");
        return templateResolver;
    }

    @Override
    public Class<?> getObjectType() {
        return ITemplateResolver.class;
    }
}
