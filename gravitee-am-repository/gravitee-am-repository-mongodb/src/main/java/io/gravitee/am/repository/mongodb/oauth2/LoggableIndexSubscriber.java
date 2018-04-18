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
package io.gravitee.am.repository.mongodb.oauth2;

import io.reactivex.subscribers.DefaultSubscriber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class LoggableIndexSubscriber extends DefaultSubscriber<String> {

    private final Logger logger = LoggerFactory.getLogger(LoggableIndexSubscriber.class);

    @Override
    public void onNext(String value) {
        logger.debug("Created an index named : " + value);
    }

    @Override
    public void onError(Throwable throwable) {
        logger.error("Error occurs during indexing", throwable);
    }

    @Override
    public void onComplete() {
        logger.debug("Index creation complete");
    }
}
