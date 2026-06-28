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
import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';
import { NO_ERRORS_SCHEMA } from '@angular/core';
import { of, throwError } from 'rxjs';

import { OrganizationService } from '../../../../../../../../services/organization.service';
import { ProviderService } from '../../../../../../../../services/provider.service';
import { SnackbarService } from '../../../../../../../../services/snackbar.service';

import { DeviceNotifierCreationStep2Component } from './step2.component';

describe('DeviceNotifierCreationStep2Component', () => {
  let component: DeviceNotifierCreationStep2Component;
  let fixture: ComponentFixture<DeviceNotifierCreationStep2Component>;
  let organizationServiceStub: Partial<OrganizationService>;
  let providerServiceStub: Partial<ProviderService>;
  let snackbarServiceStub: Partial<SnackbarService>;

  beforeEach(waitForAsync(() => {
    organizationServiceStub = {
      deviceNotifierSchema: jest.fn().mockReturnValue(of({})),
    };
    providerServiceStub = {
      findByDomain: jest.fn().mockReturnValue(of([])),
    };
    snackbarServiceStub = {
      open: jest.fn(),
    };

    TestBed.configureTestingModule({
      declarations: [DeviceNotifierCreationStep2Component],
      providers: [
        { provide: OrganizationService, useValue: organizationServiceStub },
        { provide: ProviderService, useValue: providerServiceStub },
        { provide: SnackbarService, useValue: snackbarServiceStub },
      ],
      schemas: [NO_ERRORS_SCHEMA],
      teardown: { destroyAfterEach: false },
    }).compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(DeviceNotifierCreationStep2Component);
    component = fixture.componentInstance;
    component.deviceNotifier = { type: 'http-am-authdevice-notifier' };
    component.domainId = 'domain-1';
  });

  it('should create', () => {
    fixture.detectChanges();
    expect(component).toBeTruthy();
  });

  describe('IdP dynamic-source injection', () => {
    it('injects enum and titleMap into a graviteeIdentityProvider property after init', () => {
      const idps = [
        { id: 'idp-1', name: 'Auth0' },
        { id: 'idp-2', name: 'Keycloak' },
      ];
      (providerServiceStub.findByDomain as jest.Mock).mockReturnValue(of(idps));
      (organizationServiceStub.deviceNotifierSchema as jest.Mock).mockReturnValue(
        of({
          id: 'http-am-authdevice-notifier',
          properties: {
            identityProviderId: { widget: 'graviteeIdentityProvider', type: 'string' },
            endpointUrl: { type: 'string' },
          },
        }),
      );

      fixture.detectChanges(); // triggers ngOnInit

      expect(providerServiceStub.findByDomain).toHaveBeenCalledWith('domain-1');
      const prop = component.deviceNotifierSchema.properties['identityProviderId'];
      expect(prop.enum).toEqual(['idp-1', 'idp-2']);
      expect(prop['x-schema-form']).toEqual({
        type: 'select',
        titleMap: { 'idp-1': 'Auth0', 'idp-2': 'Keycloak' },
      });
    });

    it('marks the property readonly when the IdP list is empty', () => {
      (providerServiceStub.findByDomain as jest.Mock).mockReturnValue(of([]));
      (organizationServiceStub.deviceNotifierSchema as jest.Mock).mockReturnValue(
        of({
          id: 'http-am-authdevice-notifier',
          properties: {
            identityProviderId: { widget: 'graviteeIdentityProvider', type: 'string' },
          },
        }),
      );

      fixture.detectChanges();

      const prop = component.deviceNotifierSchema.properties['identityProviderId'];
      expect(prop['readonly']).toBe(true);
      expect(prop.enum).toBeUndefined();
    });

    it('surfaces and logs an IdP-load failure, then falls back to a readonly picker', () => {
      const consoleError = jest.spyOn(console, 'error').mockImplementation(() => undefined);
      (providerServiceStub.findByDomain as jest.Mock).mockReturnValue(throwError(() => new Error('network')));
      (organizationServiceStub.deviceNotifierSchema as jest.Mock).mockReturnValue(
        of({
          id: 'http-am-authdevice-notifier',
          properties: {
            identityProviderId: { widget: 'graviteeIdentityProvider', type: 'string' },
          },
        }),
      );

      fixture.detectChanges();

      // The failure is surfaced (distinct from a genuinely-empty domain) and logged...
      expect(snackbarServiceStub.open).toHaveBeenCalledWith('Unable to load identity providers');
      expect(consoleError).toHaveBeenCalled();
      // ...but the form still renders: the picker falls back to readonly rather than crashing.
      const prop = component.deviceNotifierSchema.properties['identityProviderId'];
      expect(prop['readonly']).toBe(true);
      consoleError.mockRestore();
    });

    it('surfaces and logs a schema-load failure instead of rendering a blank step', () => {
      const consoleError = jest.spyOn(console, 'error').mockImplementation(() => undefined);
      (providerServiceStub.findByDomain as jest.Mock).mockReturnValue(of([]));
      (organizationServiceStub.deviceNotifierSchema as jest.Mock).mockReturnValue(throwError(() => new Error('boom')));

      fixture.detectChanges();

      expect(snackbarServiceStub.open).toHaveBeenCalledWith('Unable to load the device notifier configuration');
      expect(consoleError).toHaveBeenCalled();
      consoleError.mockRestore();
    });
  });
});
