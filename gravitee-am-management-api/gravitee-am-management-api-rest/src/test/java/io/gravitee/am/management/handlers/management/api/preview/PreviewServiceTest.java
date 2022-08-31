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
package io.gravitee.am.management.handlers.management.api.preview;

import io.gravitee.am.management.handlers.management.api.authentication.view.TemplateResolver;
import io.gravitee.am.management.handlers.management.api.model.PreviewRequest;
import io.gravitee.am.management.handlers.management.api.model.PreviewResponse;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.Template;
import io.gravitee.am.service.DomainService;
import io.gravitee.am.service.ThemeService;
import io.gravitee.am.service.impl.I18nDictionaryService;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.observers.TestObserver;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.cache.StandardCacheManager;

import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class PreviewServiceTest {

    private static String DOMAIN_ID = "DOMAIN-ID#1";

    @InjectMocks
    private PreviewService previewService = new PreviewService();

    @Mock
    private DomainService domainService;

    private TemplateEngine templateEngine = new TemplateEngine();

    private TemplateResolver templateResolver = new TemplateResolver();

    @Mock
    private ThemeService themeService;

    @Mock
    private I18nDictionaryService i18nDictionaryService;

    @Before
    public void init() throws Exception {
        templateEngine.setCacheManager(new StandardCacheManager());
        templateResolver.setTemplateEngine(templateEngine);
        templateEngine.setTemplateResolvers(Set.of(templateResolver));
        ReflectionTestUtils.setField(previewService, "templateEngine", templateEngine);
        ReflectionTestUtils.setField(previewService, "templateResolver", templateResolver);
    }

    @Test
    public void shouldRenderDomainForm_defaultValues() {
        when(domainService.findById(DOMAIN_ID)).thenReturn(Maybe.just(new Domain()));
        when(themeService.findByReference(ReferenceType.DOMAIN, DOMAIN_ID)).thenReturn(Maybe.empty());
        when(i18nDictionaryService.findAll(any(), any())).thenReturn(Flowable.empty());

        final PreviewRequest previewRequest = new PreviewRequest();
        previewRequest.setContent("<html lang=\"en\" xmlns:th=\"http://www.thymeleaf.org\"><head><style th:if=\"${theme.css}\" th:text=\"${theme.css}\"></style></head><body><span th:text=\"${client.name}\"></span></body></html>");
        previewRequest.setTemplate(Template.LOGIN.template());
        final TestObserver<PreviewResponse> observer = previewService.previewDomainForm(DOMAIN_ID, previewRequest).test();

        observer.awaitTerminalEvent();
        observer.assertNoErrors();
        observer.assertValue(response -> response.getContent() != null && response.getContent().contains("PreviewApp"));
        observer.assertValue(response -> response.getContent() != null && response.getContent().contains(""));
    }

    @Test
    public void shouldNotRenderDomainForm_missingValues() {
        when(domainService.findById(DOMAIN_ID)).thenReturn(Maybe.just(new Domain()));
        when(themeService.findByReference(ReferenceType.DOMAIN, DOMAIN_ID)).thenReturn(Maybe.empty());
        when(i18nDictionaryService.findAll(any(), any())).thenReturn(Flowable.empty());

        final PreviewRequest previewRequest = new PreviewRequest();
        previewRequest.setContent("<html lang=\"en\" xmlns:th=\"http://www.thymeleaf.org\"><body><span th:text=\"${client.unknown}\"></span></body></html>");
        previewRequest.setTemplate(Template.LOGIN.template());
        final TestObserver<PreviewResponse> observer = previewService.previewDomainForm(DOMAIN_ID, previewRequest).test();

        observer.awaitTerminalEvent();
        observer.assertError(PreviewException.class);
    }
}
