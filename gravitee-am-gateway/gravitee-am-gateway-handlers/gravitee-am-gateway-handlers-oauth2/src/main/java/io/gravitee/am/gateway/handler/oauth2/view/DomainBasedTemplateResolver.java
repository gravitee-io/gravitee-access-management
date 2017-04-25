package io.gravitee.am.gateway.handler.oauth2.view;

import io.gravitee.am.model.Domain;
import org.springframework.beans.factory.annotation.Autowired;
import org.thymeleaf.IEngineConfiguration;
import org.thymeleaf.templateresolver.StringTemplateResolver;
import org.thymeleaf.templateresource.ITemplateResource;
import org.thymeleaf.templateresource.StringTemplateResource;

import java.util.Map;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class DomainBasedTemplateResolver extends StringTemplateResolver {

    @Autowired
    private Domain domain;

    public DomainBasedTemplateResolver() {
        setTemplateMode("HTML");
    }

    @Override
    protected ITemplateResource computeTemplateResource(IEngineConfiguration configuration, String ownerTemplate, String template, Map<String, Object> templateResolutionAttributes) {
        return new StringTemplateResource(domain.getLoginForm().getContent());
    }
}
