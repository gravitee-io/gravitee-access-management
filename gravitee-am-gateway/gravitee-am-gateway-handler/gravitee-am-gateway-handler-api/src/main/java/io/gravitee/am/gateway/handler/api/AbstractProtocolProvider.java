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
package io.gravitee.am.gateway.handler.api;

import io.gravitee.common.service.AbstractService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

import java.io.Closeable;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public abstract class AbstractProtocolProvider extends AbstractService<ProtocolProvider> implements ProtocolProvider {

    protected final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        stopApplicationContext();
    }

    private void stopApplicationContext() {
        if (applicationContext instanceof Closeable confCtx) {
            try {
                confCtx.close();
            } catch (Exception e) {
                logger.error("\t An error occurs while stopping the application context for protocol {}", this.getClass().getSimpleName(), e);
            }
        }
    }
}