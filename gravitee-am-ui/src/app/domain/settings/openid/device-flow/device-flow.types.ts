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

/** Domain-level device authorization grant settings. */
export interface DeviceFlowSettings {
  enabled: boolean;
  deviceCodeExpiry: number;
  pollingInterval: number;
}

/** Per-application override of the domain timings. Absent means the application inherits the domain. */
export interface ApplicationDeviceFlowSettings {
  deviceCodeExpiry: number;
  pollingInterval: number;
}

export const DEVICE_CODE_GRANT_TYPE = 'urn:ietf:params:oauth:grant-type:device_code';

export const DEFAULT_DEVICE_CODE_EXPIRY = 600;
export const DEFAULT_POLLING_INTERVAL = 5;

export const DEVICE_FLOW_TIMING_MIN = 1;

export const DEVICE_FLOW_TIMINGS_ERROR =
  'The device code expiry and the polling interval must be whole numbers of at least one second, and the polling interval must not outlive the device code.';

/**
 * Whether a device code expiry and polling interval pair is usable. Polling more slowly than the
 * code lives would let a device time out without ever having polled.
 */
export function deviceFlowTimingsValid(deviceCodeExpiry: number, pollingInterval: number): boolean {
  return (
    Number.isInteger(deviceCodeExpiry) &&
    deviceCodeExpiry >= DEVICE_FLOW_TIMING_MIN &&
    Number.isInteger(pollingInterval) &&
    pollingInterval >= DEVICE_FLOW_TIMING_MIN &&
    pollingInterval <= deviceCodeExpiry
  );
}

/** Domain settings with defaults applied, for a domain that has never stored any. */
export function effectiveDomainDeviceFlowSettings(domain: any): DeviceFlowSettings {
  const stored = domain?.oidc?.deviceFlowSettings;
  return {
    enabled: stored?.enabled === true,
    deviceCodeExpiry: stored?.deviceCodeExpiry ?? DEFAULT_DEVICE_CODE_EXPIRY,
    pollingInterval: stored?.pollingInterval ?? DEFAULT_POLLING_INTERVAL,
  };
}
