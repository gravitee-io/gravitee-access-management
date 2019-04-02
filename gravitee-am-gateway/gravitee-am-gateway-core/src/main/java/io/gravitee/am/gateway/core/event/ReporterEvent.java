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
package io.gravitee.am.gateway.core.event;

import io.gravitee.am.model.common.event.Action;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public enum ReporterEvent {

    DEPLOY,
    UPDATE,
    UNDEPLOY;

    public static ReporterEvent actionOf(Action action) {
        ReporterEvent reporterEvent = null;
        switch (action) {
            case CREATE:
                reporterEvent = ReporterEvent.DEPLOY;
                break;
            case UPDATE:
                reporterEvent = ReporterEvent.UPDATE;
                break;
            case DELETE:
                reporterEvent = ReporterEvent.UNDEPLOY;
                break;
        }
        return reporterEvent;
    }
}
