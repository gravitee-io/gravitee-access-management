/*
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
import { appConfig } from '../../config/app-config';

function consoleUrl(path: string): string {
    return new URL(path, document.baseURI).toString();
}

/** The Management API login chain sets the session cookie, then returns to `login/callback`. */
export function redirectToLogin(): void {
    const redirectUri = encodeURIComponent(consoleUrl('login/callback'));
    window.location.assign(`${appConfig().baseURL}/auth/authorize?redirect_uri=${redirectUri}`);
}

export function redirectToLogout(): void {
    const targetUrl = encodeURIComponent(consoleUrl('logout/callback'));
    window.location.assign(`${appConfig().baseURL}/auth/logout?target_url=${targetUrl}`);
}
