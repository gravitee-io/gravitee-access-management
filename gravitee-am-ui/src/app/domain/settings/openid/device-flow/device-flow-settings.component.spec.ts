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
jest.mock('@gravitee/ui-components/src/lib/utils', () => ({
  deepClone: (x: unknown) => (x !== null && typeof x === 'object' ? JSON.parse(JSON.stringify(x)) : x),
}));

import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { CUSTOM_ELEMENTS_SCHEMA } from '@angular/core';
import { of } from 'rxjs';

import { DomainStoreService } from '../../../../stores/domain.store';
import { DomainService } from '../../../../services/domain.service';
import { SnackbarService } from '../../../../services/snackbar.service';
import { AuthService } from '../../../../services/auth.service';

import { DeviceFlowSettingsComponent } from './device-flow-settings.component';

describe('DeviceFlowSettingsComponent', () => {
  let component: DeviceFlowSettingsComponent;
  let fixture: ComponentFixture<DeviceFlowSettingsComponent>;
  let domainStore: { domain$: any; set: jest.Mock };
  let domainService: { patchOpenidDCRSettings: jest.Mock };
  let snackbarService: { open: jest.Mock };

  function domainWith(deviceFlowSettings: unknown): Record<string, unknown> {
    return { id: 'domain-id', oidc: { deviceFlowSettings } };
  }

  function createComponent(domain: Record<string, unknown>): void {
    domainStore.domain$ = of(domain);
    fixture = TestBed.createComponent(DeviceFlowSettingsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  beforeEach(async () => {
    domainStore = { domain$: of(domainWith(undefined)), set: jest.fn() };
    domainService = { patchOpenidDCRSettings: jest.fn().mockReturnValue(of(domainWith({ enabled: true }))) };
    snackbarService = { open: jest.fn() };

    await TestBed.configureTestingModule({
      imports: [CommonModule, FormsModule],
      declarations: [DeviceFlowSettingsComponent],
      schemas: [CUSTOM_ELEMENTS_SCHEMA],
      providers: [
        { provide: DomainStoreService, useValue: domainStore },
        { provide: DomainService, useValue: domainService },
        { provide: SnackbarService, useValue: snackbarService },
        { provide: AuthService, useValue: { hasPermissions: () => true } },
      ],
    }).compileComponents();
  });

  it('should default to disabled with the standard timings when the domain has no settings', () => {
    createComponent(domainWith(undefined));

    expect(component.isDeviceFlowEnabled()).toBe(false);
    expect(component.domain.oidc.deviceFlowSettings.deviceCodeExpiry).toBe(600);
    expect(component.domain.oidc.deviceFlowSettings.pollingInterval).toBe(5);
  });

  it('should reflect the saved settings when the domain has some', () => {
    createComponent(domainWith({ enabled: true, deviceCodeExpiry: 900, pollingInterval: 10 }));

    expect(component.isDeviceFlowEnabled()).toBe(true);
    expect(component.domain.oidc.deviceFlowSettings.deviceCodeExpiry).toBe(900);
    expect(component.domain.oidc.deviceFlowSettings.pollingInterval).toBe(10);
  });

  it('should mark the form changed when device flow is enabled', () => {
    createComponent(domainWith(undefined));

    component.enableDeviceFlow({ checked: true });

    expect(component.isDeviceFlowEnabled()).toBe(true);
    expect(component.formChanged).toBe(true);
  });

  it('should patch the openid settings and refresh the store on save', () => {
    createComponent(domainWith({ enabled: true, deviceCodeExpiry: 900, pollingInterval: 10 }));
    component.formChanged = true;
    const submitted = component.domain;

    component.save();

    expect(domainService.patchOpenidDCRSettings).toHaveBeenCalledWith('domain-id', submitted);
    expect(submitted.oidc.deviceFlowSettings).toEqual({ enabled: true, deviceCodeExpiry: 900, pollingInterval: 10 });
    expect(domainStore.set).toHaveBeenCalled();
    expect(snackbarService.open).toHaveBeenCalled();
    expect(component.formChanged).toBe(false);
  });

  it('should be read-only without the update permission', async () => {
    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [CommonModule, FormsModule],
      declarations: [DeviceFlowSettingsComponent],
      schemas: [CUSTOM_ELEMENTS_SCHEMA],
      providers: [
        { provide: DomainStoreService, useValue: domainStore },
        { provide: DomainService, useValue: domainService },
        { provide: SnackbarService, useValue: snackbarService },
        { provide: AuthService, useValue: { hasPermissions: () => false } },
      ],
    }).compileComponents();

    createComponent(domainWith(undefined));

    expect(component.editMode).toBe(false);
  });

  describe('validation', () => {
    beforeEach(() => createComponent(domainWith(undefined)));

    it('should accept the default timings', () => {
      expect(component.isValid()).toBe(true);
    });

    it('should reject a zero or negative device code expiry', () => {
      component.domain.oidc.deviceFlowSettings.deviceCodeExpiry = 0;
      expect(component.isValid()).toBe(false);

      component.domain.oidc.deviceFlowSettings.deviceCodeExpiry = -1;
      expect(component.isValid()).toBe(false);
    });

    it('should reject a zero polling interval', () => {
      component.domain.oidc.deviceFlowSettings.pollingInterval = 0;
      expect(component.isValid()).toBe(false);
    });

    it('should reject a polling interval longer than the device code lives', () => {
      component.domain.oidc.deviceFlowSettings.deviceCodeExpiry = 30;
      component.domain.oidc.deviceFlowSettings.pollingInterval = 60;
      expect(component.isValid()).toBe(false);
    });

    it('should reject fractional timings', () => {
      component.domain.oidc.deviceFlowSettings.pollingInterval = 1.5;
      expect(component.isValid()).toBe(false);
    });

    it('should stay valid after a save whose response carries no device flow settings', () => {
      domainService.patchOpenidDCRSettings.mockReturnValue(of({ id: 'domain-id', oidc: {} }));

      component.save();

      expect(component.domain.oidc.deviceFlowSettings).toEqual({ enabled: false, deviceCodeExpiry: 600, pollingInterval: 5 });
      expect(component.isValid()).toBe(true);
    });
  });
});
