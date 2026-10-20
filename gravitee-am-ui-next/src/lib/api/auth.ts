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

const RETURN_TO_KEY = 'am.login.return-to';

function consoleUrl(path: string): string {
    return new URL(path, document.baseURI).toString();
}

/**
 * Starts the Management API login chain: `/auth/authorize` sends the browser to the login form, sets the session cookie,
 * then redirects to `login/callback`. The current location is kept so the callback can return to it.
 */
export function redirectToLogin(returnTo = window.location.pathname + window.location.search): void {
    sessionStorage.setItem(RETURN_TO_KEY, returnTo);
    const redirectUri = encodeURIComponent(consoleUrl('login/callback'));
    window.location.assign(`${appConfig().baseURL}/auth/authorize?redirect_uri=${redirectUri}`);
}

export function takeReturnTo(): string {
    const returnTo = sessionStorage.getItem(RETURN_TO_KEY);
    sessionStorage.removeItem(RETURN_TO_KEY);
    return returnTo && !returnTo.startsWith('/login') ? returnTo : '/';
}

export function redirectToLogout(): void {
    const targetUrl = encodeURIComponent(consoleUrl('logout/callback'));
    window.location.assign(`${appConfig().baseURL}/auth/logout?target_url=${targetUrl}`);
}
