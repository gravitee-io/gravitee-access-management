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
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CommonModule } from '@angular/common';
import { NO_ERRORS_SCHEMA } from '@angular/core';
import { of, throwError } from 'rxjs';

import { CrossAppAccessService } from '../../../../services/cross-app-access.service';

import { CrossAppAccessComponent } from './cross-app-access.component';
import { CrossAppAccessResourceServerOption, DEFAULT_ID_JAG_VALIDITY_SECONDS } from './cross-app-access.types';

describe('CrossAppAccessComponent', () => {
  let component: CrossAppAccessComponent;
  let fixture: ComponentFixture<CrossAppAccessComponent>;
  let listResourceServers: jest.Mock;

  const calendar: CrossAppAccessResourceServerOption = {
    trustDomainId: 'td-1',
    trustDomainName: 'Acme Corp',
    resourceServerId: 'rs-1',
    name: 'Acme Calendar',
    resource: 'https://calendar.acme.com',
  };
  const files: CrossAppAccessResourceServerOption = {
    trustDomainId: 'td-1',
    trustDomainName: 'Acme Corp',
    resourceServerId: 'rs-2',
    name: 'Acme Files',
    resource: 'https://files.acme.com',
  };

  function createFixture(oauthSettings: Record<string, unknown>): void {
    fixture = TestBed.createComponent(CrossAppAccessComponent);
    component = fixture.componentInstance;
    component.domainId = 'domain-id';
    component.oauthSettings = oauthSettings;
    fixture.detectChanges();
  }

  beforeEach(async () => {
    listResourceServers = jest.fn().mockReturnValue(of([calendar, files]));

    await TestBed.configureTestingModule({
      imports: [CommonModule],
      declarations: [CrossAppAccessComponent],
      schemas: [NO_ERRORS_SCHEMA],
      providers: [{ provide: CrossAppAccessService, useValue: { listResourceServers } }],
    }).compileComponents();
  });

  it('shouldReadAbsentSettingsAsDisabled', () => {
    createFixture({});
    expect(component.isEnabled()).toBe(false);
  });

  it('shouldLabelResourceServerWithTrustDomainNameAndResource', () => {
    createFixture({});
    expect(component.label(calendar)).toBe('Acme Corp — Acme Calendar (https://calendar.acme.com)');
  });

  it('shouldDefaultIdJagValidityWhenEnabling', () => {
    createFixture({});
    component.enableCrossAppAccess({ checked: true });
    expect(component.oauthSettings.idJagValiditySeconds).toBe(DEFAULT_ID_JAG_VALIDITY_SECONDS);
  });

  it('shouldNotOverwriteIdJagValidityWhenEnabling', () => {
    createFixture({ idJagValiditySeconds: 60 });
    component.enableCrossAppAccess({ checked: true });
    expect(component.oauthSettings.idJagValiditySeconds).toBe(60);
  });

  it('shouldKeepMappingsWhenDisabling', () => {
    createFixture({ crossAppAccessSettings: { enabled: true, resourceServers: [{ resourceServerId: 'rs-1', clientId: 'client-1' }] } });
    component.enableCrossAppAccess({ checked: false });
    expect(component.isEnabled()).toBe(false);
    expect(component.oauthSettings.crossAppAccessSettings.resourceServers).toHaveLength(1);
  });

  it('shouldRejectNewMappingWithoutClientId', () => {
    createFixture({});
    component.newMapping = { resourceServerId: 'rs-1', clientId: '   ' };
    expect(component.isNewMappingValid()).toBe(false);
  });

  it('shouldRejectNewMappingWithoutResourceServer', () => {
    createFixture({});
    component.newMapping = { resourceServerId: '', clientId: 'client-1' };
    expect(component.isNewMappingValid()).toBe(false);
  });

  it('shouldAddMappingWithTrustDomainIdOfSelectedResourceServer', () => {
    createFixture({});
    component.newMapping = { resourceServerId: 'rs-1', clientId: ' client-1 ' };
    component.addMapping();
    expect(component.oauthSettings.crossAppAccessSettings.resourceServers).toEqual([
      { trustDomainId: 'td-1', resourceServerId: 'rs-1', clientId: 'client-1' },
    ]);
    expect(component.newMapping).toEqual({ resourceServerId: '', clientId: '' });
  });

  it('shouldNotOfferResourceServerAlreadyMapped', () => {
    createFixture({ crossAppAccessSettings: { enabled: true, resourceServers: [{ resourceServerId: 'rs-1', clientId: 'client-1' }] } });
    expect(component.availableResourceServers.map((option) => option.resourceServerId)).toEqual(['rs-2']);
  });

  it('shouldRemoveMappingByIndex', () => {
    createFixture({
      crossAppAccessSettings: {
        enabled: true,
        resourceServers: [
          { resourceServerId: 'rs-1', clientId: 'client-1' },
          { resourceServerId: 'rs-2', clientId: 'client-2' },
        ],
      },
    });
    component.removeMapping(0);
    expect(component.oauthSettings.crossAppAccessSettings.resourceServers).toEqual([{ resourceServerId: 'rs-2', clientId: 'client-2' }]);
  });

  it('shouldResolveRowAgainstLoadedResourceServers', () => {
    createFixture({ crossAppAccessSettings: { enabled: true, resourceServers: [{ resourceServerId: 'rs-1', clientId: 'client-1' }] } });
    expect(component.rows[0].option).toEqual(calendar);
  });

  it('shouldLeaveRowUnresolvedWhenResourceServerNoLongerExists', () => {
    createFixture({ crossAppAccessSettings: { enabled: true, resourceServers: [{ resourceServerId: 'rs-gone', clientId: 'client-1' }] } });
    expect(component.rows[0].option).toBeUndefined();
    expect(component.resourceServersUnavailable).toBe(false);
  });

  it('shouldFlagResourceServersUnavailableWhenListingFails', () => {
    listResourceServers.mockReturnValue(throwError(() => new Error('403')));
    createFixture({ crossAppAccessSettings: { enabled: true, resourceServers: [{ resourceServerId: 'rs-1', clientId: 'client-1' }] } });
    expect(component.resourceServersUnavailable).toBe(true);
    expect(component.rows).toHaveLength(1);
  });

  it('shouldEmitSettingsChangeOnEveryEdit', () => {
    createFixture({});
    const emitted = jest.fn();
    component.settingsChange.subscribe(emitted);
    component.enableCrossAppAccess({ checked: true });
    component.newMapping = { resourceServerId: 'rs-1', clientId: 'client-1' };
    component.addMapping();
    component.removeMapping(0);
    expect(emitted).toHaveBeenCalledTimes(3);
  });
});
