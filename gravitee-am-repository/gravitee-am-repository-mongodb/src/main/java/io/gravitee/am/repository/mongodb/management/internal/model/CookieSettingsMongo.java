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

package io.gravitee.am.repository.mongodb.management.internal.model;

import io.gravitee.am.model.CookieSettings;
import io.gravitee.am.model.SessionSettings;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class CookieSettingsMongo {

    private boolean inherited;

    private SessionSettingsMongo session;

    public boolean isInherited() {
        return inherited;
    }

    public void setInherited(boolean inherited) {
        this.inherited = inherited;
    }

    public SessionSettingsMongo getSession() {
        return session;
    }

    public void setSession(SessionSettingsMongo session) {
        this.session = session;
    }

    public CookieSettings convert() {
        var cookieSettings = new CookieSettings();
        if (this.getSession() != null) {
            var sessionSettings = new SessionSettings();
            sessionSettings.setPersistent(this.getSession().isPersistent());
            cookieSettings.setSession(sessionSettings);
        }
        cookieSettings.setInherited(this.isInherited());
        return cookieSettings;
    }

    public static CookieSettingsMongo convert(CookieSettings cookieSettings) {
        if (cookieSettings == null) {
            return null;
        }
        var cookieSettingsMongo = new CookieSettingsMongo();
        var sessionSettings = getSessionSettingsMongo(cookieSettings.getSession());
        cookieSettingsMongo.setInherited(cookieSettings.isInherited());
        cookieSettingsMongo.setSession(sessionSettings);
        return cookieSettingsMongo;
    }

    private static SessionSettingsMongo getSessionSettingsMongo(SessionSettings sessionSettings) {
        if (sessionSettings == null) {
            return null;
        }
        final SessionSettingsMongo sessionSettingsMongo = new SessionSettingsMongo();
        sessionSettingsMongo.setPersistent(sessionSettings.isPersistent());
        return sessionSettingsMongo;
    }
}
