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
package io.gravitee.am.factor.api;

import io.gravitee.am.model.User;
import io.gravitee.am.model.oidc.Client;
import org.springframework.context.ApplicationContext;

import java.util.Map;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class FactorContext {
    public static final String KEY_ENROLLED_FACTOR = "enrolledFactor";
    public static final String KEY_RECOVERY_FACTOR = "recoveryFactor";
    public static final String KEY_CODE = "code";
    public static final String KEY_CLIENT = "client";
    public static final String KEY_USER = "user";
    public static final String KEY_REQUEST = "request";

    private final ApplicationContext appContext;
    private final Map<String, Object> data;

    public FactorContext(ApplicationContext appContext, Map<String, Object> data) {
        this.appContext = appContext;
        this.data = data;
    }

    public <T> T getComponent(Class<T> componentClass) {
        return appContext.getBean(componentClass);
    }

    public Map<String, Object> getData() {
        return data;
    }

    public <T> T getData(String key, Class<T> type) {
        if (data != null) {
            return type.cast(getData().get(key));
        }
        return null;
    }

    public <V> void registerData(String key, V value) {
        this.data.put(key, value);
    }

    public Client getClient() {
        return getData(KEY_CLIENT, Client.class);
    }

    public User getUser() {
        return getData(KEY_USER, User.class);
    }

    public Map<String, Object> getTemplateValues() {
        return getData();
    }
}

