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
package io.gravitee.am.gateway.handler.account.resources.account.util;

public enum AccountRoutes {
    INDEX("/"),
    STATIC_ASSETS("/assets/*"),
    PROFILE("/api/profile"),
    FACTORS("/api/factors"),
    ACTIVITIES("/api/activity"),
    CHANGE_PASSWORD("/api/changePassword"),
    CHANGE_PASSWORD_REDIRECT("/forgotPassword");

    private String route;

    AccountRoutes(String route){
        this.route = route;
    }

    public String getRoute() {
        return route;
    }
}
