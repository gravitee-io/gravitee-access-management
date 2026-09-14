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
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { CommonModule } from '@angular/common';
import { NO_ERRORS_SCHEMA } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { MatButtonModule } from '@angular/material/button';
import { MatDividerModule } from '@angular/material/divider';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatIconTestingModule } from '@angular/material/icon/testing';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatTableModule } from '@angular/material/table';
import { Observable, of, throwError } from 'rxjs';

import { CrossAppAccessService } from '../../../../services/cross-app-access.service';

import { CrossAppAccessComponent } from './cross-app-access.component';
import {
  CrossAppAccessResourceServerOption,
  DEFAULT_ID_JAG_VALIDITY_SECONDS,
  MAX_RESOURCE_SERVER_LIMIT,
  RESOURCE_SERVER_SEARCH_DEBOUNCE_MS,
} from './cross-app-access.types';

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

describe('CrossAppAccessComponent', () => {
  let component: CrossAppAccessComponent;
  let fixture: ComponentFixture<CrossAppAccessComponent>;
  let listResourceServers: jest.Mock;

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

  it('shouldListEveryResourceServerOnInit', () => {
    createFixture({});
    expect(listResourceServers).toHaveBeenCalledWith('domain-id', '');
    expect(listResourceServers).toHaveBeenCalledWith('domain-id', '', MAX_RESOURCE_SERVER_LIMIT);
  });

  it('shouldWaitForTheDebounceBeforeSearching', fakeAsync(() => {
    createFixture({});
    listResourceServers.mockClear();

    component.searchResourceServers('cal');
    tick(RESOURCE_SERVER_SEARCH_DEBOUNCE_MS - 1);
    expect(listResourceServers).not.toHaveBeenCalled();

    tick(1);
    expect(listResourceServers).toHaveBeenCalledWith('domain-id', 'cal');
  }));

  it('shouldCancelTheInFlightRequestWhenTheSearchTermChanges', fakeAsync(() => {
    createFixture({});
    const cancelled = jest.fn();
    listResourceServers.mockReturnValue(new Observable<CrossAppAccessResourceServerOption[]>(() => cancelled));

    component.searchResourceServers('cal');
    tick(RESOURCE_SERVER_SEARCH_DEBOUNCE_MS);
    component.searchResourceServers('calendar');
    tick(RESOURCE_SERVER_SEARCH_DEBOUNCE_MS);

    expect(cancelled).toHaveBeenCalledTimes(1);
    expect(listResourceServers).toHaveBeenLastCalledWith('domain-id', 'calendar');
  }));

  it('shouldOfferOnlyTheResourceServersTheSearchReturned', fakeAsync(() => {
    createFixture({});
    listResourceServers.mockReturnValue(of([files]));

    component.searchResourceServers('files');
    tick(RESOURCE_SERVER_SEARCH_DEBOUNCE_MS);

    expect(component.availableResourceServers.map((option) => option.resourceServerId)).toEqual(['rs-2']);
  }));

  it('shouldKeepLabellingARowTheSearchNoLongerReturns', fakeAsync(() => {
    createFixture({ crossAppAccessSettings: { enabled: true, resourceServers: [{ resourceServerId: 'rs-1', clientId: 'client-1' }] } });
    listResourceServers.mockReturnValue(of([files]));

    component.searchResourceServers('files');
    tick(RESOURCE_SERVER_SEARCH_DEBOUNCE_MS);

    expect(component.rows[0].option).toEqual(calendar);
  }));

  it('shouldKeepSearchingAfterAFailedLookup', fakeAsync(() => {
    createFixture({});
    listResourceServers.mockReturnValue(throwError(() => new Error('503')));

    component.searchResourceServers('cal');
    tick(RESOURCE_SERVER_SEARCH_DEBOUNCE_MS);
    expect(component.resourceServersUnavailable).toBe(true);

    listResourceServers.mockReturnValue(of([calendar]));
    component.searchResourceServers('calendar');
    tick(RESOURCE_SERVER_SEARCH_DEBOUNCE_MS);

    expect(component.resourceServersUnavailable).toBe(false);
    expect(component.availableResourceServers).toEqual([calendar]);
  }));

  it('shouldStopSearchingOnDestroy', fakeAsync(() => {
    createFixture({});
    listResourceServers.mockClear();

    component.ngOnDestroy();
    component.searchResourceServers('cal');
    tick(RESOURCE_SERVER_SEARCH_DEBOUNCE_MS);

    expect(listResourceServers).not.toHaveBeenCalled();
  }));

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

describe('CrossAppAccessComponent dropdown panel', () => {
  let fixture: ComponentFixture<CrossAppAccessComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [
        CommonModule,
        FormsModule,
        NoopAnimationsModule,
        MatButtonModule,
        MatDividerModule,
        MatFormFieldModule,
        MatIconModule,
        MatIconTestingModule,
        MatInputModule,
        MatProgressSpinnerModule,
        MatSelectModule,
        MatSlideToggleModule,
        MatTableModule,
      ],
      declarations: [CrossAppAccessComponent],
      providers: [{ provide: CrossAppAccessService, useValue: { listResourceServers: () => of([calendar, files]) } }],
    }).compileComponents();

    fixture = TestBed.createComponent(CrossAppAccessComponent);
    fixture.componentInstance.domainId = 'domain-id';
    fixture.componentInstance.oauthSettings = { crossAppAccessSettings: { enabled: true } };
    fixture.detectChanges();
  });

  it('shouldOfferEveryResourceServerInThePanel', () => {
    fixture.nativeElement.querySelector('.mat-mdc-select-trigger').click();
    fixture.detectChanges();

    expect(Array.from(document.querySelectorAll('mat-option')).map((option) => option.textContent.trim())).toEqual([
      'Acme Corp — Acme Calendar (https://calendar.acme.com)',
      'Acme Corp — Acme Files (https://files.acme.com)',
    ]);
  });

  it('shouldCarryThePanelClassTheSearchBarIsStyledThrough', () => {
    fixture.nativeElement.querySelector('.mat-mdc-select-trigger').click();
    fixture.detectChanges();

    const panel = document.querySelector('.mat-mdc-select-panel');
    expect(panel.classList.contains('cross-app-access-panel')).toBe(true);
    expect(panel.querySelector('.resource-server-search-input')).toBeTruthy();
  });

  it('shouldKeepTypingOutOfThePanelButLeaveItsNavigationKeys', () => {
    const stopped = (key: string) => {
      const event = new KeyboardEvent('keydown', { key });
      const stopPropagation = jest.spyOn(event, 'stopPropagation');
      fixture.componentInstance.onSearchKeydown(event);
      return stopPropagation.mock.calls.length > 0;
    };

    expect(stopped(' ')).toBe(true);
    expect(stopped('a')).toBe(true);
    expect(stopped('Escape')).toBe(false);
    expect(stopped('ArrowDown')).toBe(false);
  });

  it('shouldNotLetThePanelSearchFieldShiftTheResourceServerLabel', () => {
    const resourceServerField: HTMLElement = fixture.nativeElement.querySelector('mat-form-field');

    expect(resourceServerField.classList.contains('mat-mdc-form-field-has-icon-prefix')).toBe(false);
    expect(resourceServerField.classList.contains('mat-mdc-form-field-has-icon-suffix')).toBe(false);
  });
});
