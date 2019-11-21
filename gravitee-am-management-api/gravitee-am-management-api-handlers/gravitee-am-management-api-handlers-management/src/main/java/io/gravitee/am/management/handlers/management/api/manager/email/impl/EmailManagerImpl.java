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
package io.gravitee.am.management.handlers.management.api.manager.email.impl;

import io.gravitee.am.management.handlers.management.api.manager.email.EmailManager;
import io.gravitee.am.model.Email;
import io.gravitee.am.service.EmailTemplateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class EmailManagerImpl implements EmailManager, InitializingBean {

    private static final Logger logger = LoggerFactory.getLogger(EmailManagerImpl.class);

    @Autowired
    private EmailTemplateService emailTemplateService;

    @Autowired
    private io.gravitee.am.management.service.EmailManager emailManager;

    @Override
    public void afterPropertiesSet() {
        logger.info("Initializing emails");
        List<Email> emails = emailTemplateService.findAll().blockingGet();
        emails.stream().filter(Email::isEnabled).forEach(this::loadEmail);
    }

    private void loadEmail(Email email) {
        emailManager.reloadEmail(email).subscribe();
    }
}
