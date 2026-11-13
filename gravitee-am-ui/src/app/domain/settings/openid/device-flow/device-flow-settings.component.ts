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
import { Component, OnInit } from '@angular/core';
import { deepClone } from '@gravitee/ui-components/src/lib/utils';

import { AuthService } from '../../../../services/auth.service';
import { DomainService } from '../../../../services/domain.service';
import { SnackbarService } from '../../../../services/snackbar.service';
import { DomainStoreService } from '../../../../stores/domain.store';

import {
  DEFAULT_DEVICE_CODE_EXPIRY,
  DEFAULT_POLLING_INTERVAL,
  DEVICE_FLOW_TIMINGS_ERROR,
  DEVICE_FLOW_TIMING_MIN,
  deviceFlowTimingsValid,
} from './device-flow.types';

@Component({
  selector: 'app-oidc-device-flow-settings',
  templateUrl: './device-flow-settings.component.html',
  styleUrls: ['./device-flow-settings.component.scss'],
  standalone: false,
})
export class DeviceFlowSettingsComponent implements OnInit {
  domainId: string;
  domain: any = {};
  formChanged = false;
  editMode: boolean;

  readonly DEVICE_FLOW_TIMING_MIN = DEVICE_FLOW_TIMING_MIN;
  readonly DEVICE_FLOW_TIMINGS_ERROR = DEVICE_FLOW_TIMINGS_ERROR;

  constructor(
    private domainService: DomainService,
    private snackbarService: SnackbarService,
    private authService: AuthService,
    private domainStore: DomainStoreService,
  ) {}

  ngOnInit() {
    this.domainStore.domain$.subscribe((domain) => (this.domain = deepClone(domain)));
    this.domainId = this.domain.id;
    this.editMode = this.authService.hasPermissions(['domain_openid_update']);
    this.seedSettings();
  }

  save() {
    this.domainService.patchOpenidDCRSettings(this.domainId, this.domain).subscribe((data) => {
      this.domainStore.set(data);
      this.domain = data;
      this.seedSettings();
      this.formChanged = false;
      this.snackbarService.open('Device Flow configuration updated');
    });
  }

  private seedSettings() {
    if (!this.domain.oidc) {
      this.domain.oidc = {};
    }
    if (!this.domain.oidc.deviceFlowSettings) {
      this.domain.oidc.deviceFlowSettings = {
        enabled: false,
        deviceCodeExpiry: DEFAULT_DEVICE_CODE_EXPIRY,
        pollingInterval: DEFAULT_POLLING_INTERVAL,
      };
    }
  }

  enableDeviceFlow(event) {
    this.domain.oidc.deviceFlowSettings.enabled = event.checked;
    this.formChanged = true;
  }

  isDeviceFlowEnabled(): boolean {
    return this.domain.oidc?.deviceFlowSettings?.enabled === true;
  }

  isValid(): boolean {
    const settings = this.domain.oidc?.deviceFlowSettings;
    if (!settings) {
      return false;
    }
    return deviceFlowTimingsValid(Number(settings.deviceCodeExpiry), Number(settings.pollingInterval));
  }

  modelChanged(): void {
    this.formChanged = true;
  }
}
