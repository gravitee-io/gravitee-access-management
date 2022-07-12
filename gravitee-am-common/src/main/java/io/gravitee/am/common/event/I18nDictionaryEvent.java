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
package io.gravitee.am.common.event;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public enum I18nDictionaryEvent {

    DEPLOY,
    UPDATE,
    UNDEPLOY;

    public static I18nDictionaryEvent actionOf(Action action) {
        I18nDictionaryEvent roleEvent = null;
        switch (action) {
            case CREATE:
                roleEvent = I18nDictionaryEvent.DEPLOY;
                break;
            case UPDATE:
                roleEvent = I18nDictionaryEvent.UPDATE;
                break;
            case DELETE:
                roleEvent = I18nDictionaryEvent.UNDEPLOY;
                break;
            default:
                throw new IllegalArgumentException(String.format("Action %s not supported for I18DictionaryEvent", action));
        }
        return roleEvent;
    }
}
