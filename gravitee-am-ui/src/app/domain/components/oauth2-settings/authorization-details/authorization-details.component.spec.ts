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
import { MatDialog } from '@angular/material/dialog';
import { of } from 'rxjs';

import { AuthService } from '../../../../services/auth.service';

import { AuthorizationDetailsComponent, toDuration } from './authorization-details.component';

describe('AuthorizationDetailsComponent', () => {
  const domainTypes = [
    { type: 'account_information', name: 'Accounts' },
    { type: 'payment_initiation', name: 'Payments', expiresIn: 7200 },
    { type: 'tax_data', name: 'Tax' },
  ];
  let fixture: ComponentFixture<AuthorizationDetailsComponent>;
  let component: AuthorizationDetailsComponent;
  let dialog: { open: jest.Mock };
  let hasPermissions: jest.Mock;
  let emitted: any[];

  function create(enabled: any[], readonly = false) {
    fixture = TestBed.createComponent(AuthorizationDetailsComponent);
    component = fixture.componentInstance;
    component.oauthSettings = { authorizationDetailTypes: enabled };
    component.authorizationDetailTypes = domainTypes;
    component.domain = { id: 'domain-1', referenceId: 'env-1' };
    component.readonly = readonly;
    emitted = [];
    component.settingsChange.subscribe((settings) => emitted.push(settings.authorizationDetailTypes));
    fixture.detectChanges();
  }

  function lastEmitted() {
    return emitted[emitted.length - 1];
  }

  function inputEvent(value: string): Event {
    return { target: { value } } as unknown as Event;
  }

  beforeEach(async () => {
    dialog = { open: jest.fn() };
    hasPermissions = jest.fn().mockReturnValue(true);

    await TestBed.configureTestingModule({
      imports: [CommonModule],
      declarations: [AuthorizationDetailsComponent],
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
        { provide: MatDialog, useValue: dialog },
        { provide: AuthService, useValue: { hasPermissions } },
      ],
    }).compileComponents();
  });

  it('should list enabled types with their name and override', () => {
    create([{ type: 'payment_initiation', expiresIn: 172800 }, { type: 'tax_data' }]);

    expect(component.rows).toEqual([
      { type: 'payment_initiation', name: 'Payments', typeExpiresIn: 7200, expiresIn: 2, unitTime: 'days' },
      { type: 'tax_data', name: 'Tax', typeExpiresIn: undefined },
    ]);
  });

  it('should keep an enabled type missing from the domain list so that saving does not drop it', () => {
    create([{ type: 'unknown' }, { type: 'tax_data' }]);

    component.remove(
      component.rows.find((r) => r.type === 'tax_data'),
      new Event('click'),
    );

    expect(lastEmitted()).toEqual([{ type: 'unknown' }]);
  });

  it('should offer only the types not yet enabled in the picker', () => {
    create([{ type: 'payment_initiation' }]);
    dialog.open.mockReturnValue({ afterClosed: () => of(undefined) });

    component.add(new Event('click'));

    const data = dialog.open.mock.calls[0][1].data;
    expect(data.types.map((t) => t.type)).toEqual(['account_information', 'tax_data']);
    expect(data.loadFailed).toBe(false);
    expect(data.domainHasTypes).toBe(true);
    expect(data.createLink).toEqual(['/environments', 'env-1', 'domains', 'domain-1', 'settings', 'authorization-detail-types']);
  });

  it('should withhold the link to the domain types from users who may not create them', () => {
    hasPermissions.mockReturnValue(false);
    create([]);
    component.authorizationDetailTypes = [];
    dialog.open.mockReturnValue({ afterClosed: () => of(undefined) });

    component.add(new Event('click'));

    expect(dialog.open.mock.calls[0][1].data).toEqual({ types: [], loadFailed: false, domainHasTypes: false, createLink: null });
  });

  it('should tell the picker when the domain types could not be loaded', () => {
    create([{ type: 'payment_initiation' }]);
    component.authorizationDetailTypes = null;
    dialog.open.mockReturnValue({ afterClosed: () => of(undefined) });

    component.add(new Event('click'));

    expect(dialog.open.mock.calls[0][1].data).toMatchObject({ types: [], loadFailed: true, domainHasTypes: false });
  });

  it('should enable every picked type at once', () => {
    create([]);
    dialog.open.mockReturnValue({ afterClosed: () => of([domainTypes[0], domainTypes[2]]) });

    component.add(new Event('click'));

    expect(lastEmitted()).toEqual([{ type: 'account_information' }, { type: 'tax_data' }]);
  });

  it('should make a removed type available in the picker again', () => {
    create([{ type: 'payment_initiation' }]);
    dialog.open.mockReturnValue({ afterClosed: () => of(undefined) });

    component.remove(component.rows[0], new Event('click'));
    component.add(new Event('click'));

    expect(lastEmitted()).toEqual([]);
    expect(dialog.open.mock.calls[0][1].data.types).toHaveLength(3);
  });

  it('should convert an override to seconds, defaulting the unit to that of the type', () => {
    create([{ type: 'payment_initiation' }]);

    component.onExpiresIn(component.rows[0], inputEvent('3'));

    expect(component.rows[0].unitTime).toBe('hours');
    expect(lastEmitted()).toEqual([{ type: 'payment_initiation', expiresIn: 10800 }]);
  });

  it('should remove the override when cleared', () => {
    create([{ type: 'payment_initiation', expiresIn: 60 }]);

    component.clearExpiry(component.rows[0], new Event('click'));

    expect(component.hasExpiry(component.rows[0])).toBe(false);
    expect(lastEmitted()).toEqual([{ type: 'payment_initiation' }]);
  });

  it('should flag an override below 1 second', () => {
    create([{ type: 'tax_data' }]);

    component.onExpiresIn(component.rows[0], inputEvent('0'));

    expect(component.isExpiryInvalid(component.rows[0])).toBe(true);
    expect(lastEmitted()).toEqual([{ type: 'tax_data', expiresIn: 0 }]);
  });

  it("should show the type's own expiry as placeholder, or Duration and Unit", () => {
    create([{ type: 'payment_initiation' }, { type: 'tax_data' }]);

    expect(component.durationPlaceholder(component.rows[0])).toBe('2');
    expect(component.unitPlaceholder(component.rows[0])).toBe('HOURS');
    expect(component.durationPlaceholder(component.rows[1])).toBe('Duration');
    expect(component.unitPlaceholder(component.rows[1])).toBe('Unit');
  });

  it('should hide the add action when read-only', () => {
    create([{ type: 'payment_initiation' }], true);

    expect(fixture.nativeElement.textContent).not.toContain('ADD TYPES');
  });

  it('should express seconds in the largest unit that holds them exactly', () => {
    expect(toDuration(5400)).toEqual({ value: 90, unit: 'minutes' });
    expect(toDuration(604800)).toEqual({ value: 1, unit: 'weeks' });
    expect(toDuration(259200)).toEqual({ value: 3, unit: 'days' });
    expect(toDuration(2592000)).toEqual({ value: 1, unit: 'months' });
    expect(toDuration(7)).toEqual({ value: 7, unit: 'seconds' });
  });
});
