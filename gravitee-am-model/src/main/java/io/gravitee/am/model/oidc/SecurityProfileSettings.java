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
package io.gravitee.am.model.oidc;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class SecurityProfileSettings {

    /**
     * Apply the standard Financial-grade API security profile (version 1.0).
     */
    private boolean enablePlainFapi;

    /**
     * Apply the standard Open Banking Brasil Financial-grade API Security Profile 1.0 (version 1.0).
     */
    private boolean enableFapiBrazil;

    public boolean isEnablePlainFapi() {
        return enablePlainFapi;
    }

    public void setEnablePlainFapi(boolean enablePlainFapi) {
        this.enablePlainFapi = enablePlainFapi;
    }

    public boolean isEnableFapiBrazil() {
        return enableFapiBrazil;
    }

    public void setEnableFapiBrazil(boolean enableFapiBrazil) {
        this.enableFapiBrazil = enableFapiBrazil;
    }

    public static SecurityProfileSettings defaultSettings() {
        //By default, all boolean are set to false.
        return new SecurityProfileSettings();
    }
}
