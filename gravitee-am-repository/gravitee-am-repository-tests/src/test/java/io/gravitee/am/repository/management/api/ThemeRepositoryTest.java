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
package io.gravitee.am.repository.management.api;

import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.Theme;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.repository.management.AbstractManagementTest;
import io.reactivex.observers.TestObserver;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ThemeRepositoryTest extends AbstractManagementTest {
    public static final String DOMAIN_ID = "domain#1";
    public static final String APP_ID = "app#1";

    @Autowired
    private ThemeRepository themeRepository;

    private Theme buildTheme(ReferenceType type, String refId) {
        Theme theme = new Theme();
        theme.setSecondaryButtonColorHex("#FFFFFF");
        theme.setPrimaryButtonColorHex("#EEEEEE");
        theme.setPrimaryTextColorHex("#000000");
        theme.setReferenceType(type);
        theme.setReferenceId(refId);
        theme.setCss("css");
        theme.setLogoUrl("http://logo");
        theme.setFaviconUrl("http://fav");
        theme.setLogoWidth(222);
        return theme;
    }

    @Test
    public void testFindById() throws TechnicalException {
        Theme theme = buildTheme(ReferenceType.DOMAIN, DOMAIN_ID);
        Theme themeCreated = themeRepository.create(theme).blockingGet();

        TestObserver<Theme> testObserver = themeRepository.findById(themeCreated.getId()).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(d -> d.getReferenceId().equals(DOMAIN_ID));
        testObserver.assertValue(d -> d.getCss().equals(theme.getCss()));
        testObserver.assertValue(d -> d.getFaviconUrl().equals(theme.getFaviconUrl()));
        testObserver.assertValue(d -> d.getLogoUrl().equals(theme.getLogoUrl()));
        testObserver.assertValue(d -> d.getSecondaryButtonColorHex().equals(theme.getSecondaryButtonColorHex()));
        testObserver.assertValue(d -> d.getPrimaryButtonColorHex().equals(theme.getPrimaryButtonColorHex()));
        testObserver.assertValue(d -> d.getPrimaryTextColorHex().equals(theme.getPrimaryTextColorHex()));
        testObserver.assertValue(d -> d.getLogoWidth() == theme.getLogoWidth());
    }

    @Test
    public void testNotFoundById() throws TechnicalException {
        themeRepository.findById("unknown").test().assertEmpty();
    }

    @Test
    public void testFindByReference_Domain() throws TechnicalException {
        themeRepository.create(buildTheme(ReferenceType.DOMAIN, DOMAIN_ID)).blockingGet();
        themeRepository.create(buildTheme(ReferenceType.APPLICATION, APP_ID)).blockingGet();

        TestObserver<Theme> testObserver = themeRepository.findByReference(ReferenceType.DOMAIN, DOMAIN_ID).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(d -> d.getReferenceId().equals(DOMAIN_ID));
    }

    @Test
    public void testFindByReference_App() throws TechnicalException {
        themeRepository.create(buildTheme(ReferenceType.DOMAIN, DOMAIN_ID)).blockingGet();
        themeRepository.create(buildTheme(ReferenceType.APPLICATION, APP_ID)).blockingGet();

        TestObserver<Theme> testObserver = themeRepository.findByReference(ReferenceType.APPLICATION, APP_ID).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(d -> d.getReferenceId().equals(APP_ID));
    }

    @Test
    public void testFindByReference_NotFound() throws TechnicalException {
        final TestObserver<Theme> testObserver = themeRepository.findByReference(ReferenceType.APPLICATION, "unknown").test();
        testObserver.awaitTerminalEvent();
        testObserver.assertNoValues();
    }

    @Test
    public void testCreate() throws TechnicalException {
        Theme theme = buildTheme(ReferenceType.DOMAIN, DOMAIN_ID);
        TestObserver<Theme> testObserver = themeRepository.create(theme).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(d -> d.getId() != null);
        testObserver.assertValue(d -> d.getReferenceId().equals(DOMAIN_ID));
        testObserver.assertValue(d -> d.getCss().equals(theme.getCss()));
        testObserver.assertValue(d -> d.getFaviconUrl().equals(theme.getFaviconUrl()));
        testObserver.assertValue(d -> d.getLogoUrl().equals(theme.getLogoUrl()));
        testObserver.assertValue(d -> d.getSecondaryButtonColorHex().equals(theme.getSecondaryButtonColorHex()));
        testObserver.assertValue(d -> d.getPrimaryButtonColorHex().equals(theme.getPrimaryButtonColorHex()));
        testObserver.assertValue(d -> d.getPrimaryTextColorHex().equals(theme.getPrimaryTextColorHex()));
        testObserver.assertValue(d -> d.getLogoWidth() == theme.getLogoWidth());
    }

    @Test
    public void testUpdate() throws TechnicalException {
        Theme theme = buildTheme(ReferenceType.DOMAIN, DOMAIN_ID);
        Theme themeCreated = themeRepository.create(theme).blockingGet();

        // update tag
        Theme updateTheme = new Theme();
        updateTheme.setId(themeCreated.getId());
        updateTheme.setSecondaryButtonColorHex("#FFAAFF");
        updateTheme.setPrimaryButtonColorHex("#EEAAEE");
        updateTheme.setPrimaryTextColorHex("#00AA00");
        updateTheme.setReferenceType(themeCreated.getReferenceType());
        updateTheme.setReferenceId(themeCreated.getReferenceId());
        updateTheme.setCss("css2");
        updateTheme.setLogoUrl("http://logo2");
        updateTheme.setFaviconUrl("http://fav2");
        updateTheme.setLogoWidth(224);

        TestObserver<Theme> testObserver = themeRepository.update(updateTheme).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(d -> d.getReferenceId().equals(DOMAIN_ID));
        testObserver.assertValue(d -> d.getCss().equals(updateTheme.getCss()));
        testObserver.assertValue(d -> d.getFaviconUrl().equals(updateTheme.getFaviconUrl()));
        testObserver.assertValue(d -> d.getLogoUrl().equals(updateTheme.getLogoUrl()));
        testObserver.assertValue(d -> d.getSecondaryButtonColorHex().equals(updateTheme.getSecondaryButtonColorHex()));
        testObserver.assertValue(d -> d.getPrimaryButtonColorHex().equals(updateTheme.getPrimaryButtonColorHex()));
        testObserver.assertValue(d -> d.getPrimaryTextColorHex().equals(updateTheme.getPrimaryTextColorHex()));
        testObserver.assertValue(d -> d.getLogoWidth() == updateTheme.getLogoWidth());

    }

    @Test
    public void testDelete() throws TechnicalException {
        Theme theme = buildTheme(ReferenceType.DOMAIN, DOMAIN_ID);
        Theme themeCreated = themeRepository.create(theme).blockingGet();
        TestObserver<Theme> testObserver = themeRepository.findById(themeCreated.getId()).test();
        testObserver.awaitTerminalEvent();
        testObserver.assertComplete();
        testObserver.assertNoErrors();

        TestObserver testObserver1 = themeRepository.delete(themeCreated.getId()).test();
        testObserver1.awaitTerminalEvent();

        themeRepository.findById(themeCreated.getId()).test().assertEmpty();
    }

}
