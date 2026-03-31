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

/** Default password used by buildCreateAndTestUser() — no trailing '!' */
export const API_USER_PASSWORD = 'SomeP@ssw0rd';

/** Password that satisfies AM Console password policy (uppercase, lowercase, digit, special, 10+ chars) */
export const UI_USER_PASSWORD = 'SomeP@ssw0rd!';

/** Default admin username */
export const ADMIN_USERNAME = 'admin';

/** Default admin password */
export const ADMIN_PASSWORD = 'adminadmin';

/** Regex matching a valid JWT (three Base64url segments separated by dots) */
export const JWT_FORMAT = /^[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+$/;

/** Regex matching a valid OAuth authorization code (Base64url, no dots) */
export const AUTH_CODE_FORMAT = /^[A-Za-z0-9_-]+$/;

/* ------------------------------------------------------------------ */
/*  Timeout constants                                                  */
/*  Only define constants that DIFFER from playwright.config.ts        */
/*  defaults (navigationTimeout: 30s, actionTimeout: 15s,              */
/*  expect.timeout: 15s, test timeout: 60s). Omit timeout options      */
/*  on waitForURL/expect calls when the config default is sufficient.  */
/* ------------------------------------------------------------------ */

/** Brief wait — consent page detection, element appearance checks (shorter than 15s expect default) */
export const BRIEF_TIMEOUT = 5_000;

/** Extended test timeout for multi-phase flows (longer than 60s test default) */
export const MULTI_PHASE_TEST_TIMEOUT = 120_000;

/** Mock factor verification code used in MFA gateway tests */
export const MOCK_MFA_CODE = '1234';

/* ------------------------------------------------------------------ */
/*  Gateway domain theme (AM-2172 Playwright)                          */
/* ------------------------------------------------------------------ */

/** External logo URL so the login page is not the default gravitee-logo.svg asset. */
export const GATEWAY_THEME_LOGO_URL = 'https://www.gravitee.io/favicon.ico';

/** Distinct from default gateway orange (--primary-background-color: #DA3B00 in main.css). */
export const GATEWAY_THEME_PRIMARY_BUTTON_HEX = '#1a237e';
export const GATEWAY_THEME_PRIMARY_TEXT_HEX = '#ffffff';

/** Computed background for .button.primary when --primary-background-color is GATEWAY_THEME_PRIMARY_BUTTON_HEX. */
export const GATEWAY_THEME_PRIMARY_BUTTON_RGB = 'rgb(26, 35, 126)';

/** Visible marker injected into domain LOGIN form HTML (AM-2193). */
export const AM2193_LOGIN_FORM_MARKER_TEXT = 'AM-2193 Playwright login form override';

/** Prepended to reset-password email subject to assert template override (AM-2200). */
export const AM2200_RESET_EMAIL_SUBJECT_MARKER = '[PW-AM-2200]';

/** Wait for password reset to propagate before OAuth password grant / browser login (matches Jest reset flow). */
export const RESET_PASSWORD_GATEWAY_SETTLE_MS = 2_000;

/** New password set on gateway reset-password form; distinct from {@link API_USER_PASSWORD} (AM-2196). */
export const FORGOT_PASSWORD_NEW_PASSWORD_AFTER_RESET = 'V@l1dNewP@ss99';
