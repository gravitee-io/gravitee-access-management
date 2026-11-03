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
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { MatAutocompleteModule, MatAutocompleteSelectedEvent } from '@angular/material/autocomplete';
import { MatChipsModule } from '@angular/material/chips';
import { ReactiveFormsModule } from '@angular/forms';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';

import { AddAuthorizationDetailTypeComponent, AddAuthorizationDetailTypeData } from './add-authorization-detail-type.component';

describe('AddAuthorizationDetailTypeComponent', () => {
  const types = [
    { type: 'account_information', name: 'Accounts' },
    { type: 'Payment_Initiation', name: 'Payments' },
  ];
  let fixture: ComponentFixture<AddAuthorizationDetailTypeComponent>;
  let component: AddAuthorizationDetailTypeComponent;

  async function create(data: AddAuthorizationDetailTypeData) {
    await TestBed.configureTestingModule({
      imports: [CommonModule, ReactiveFormsModule, MatAutocompleteModule, MatChipsModule, NoopAnimationsModule],
      declarations: [AddAuthorizationDetailTypeComponent],
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
        { provide: MAT_DIALOG_DATA, useValue: data },
        { provide: MatDialogRef, useValue: { close: jest.fn() } },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(AddAuthorizationDetailTypeComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  function select(type: any) {
    component.onSelectionChanged({ option: { value: type } } as MatAutocompleteSelectedEvent);
  }

  it('should search by type, ignoring case', async () => {
    await create({ types, loadFailed: false, domainHasTypes: true, createLink: null });

    component.typeCtrl.setValue('payment');

    expect(component.filteredTypes).toEqual([types[1]]);
  });

  it('should pick several types and stop offering the picked ones', async () => {
    await create({ types, loadFailed: false, domainHasTypes: true, createLink: null });
    component.typeInput = { nativeElement: { value: '' } } as any;

    select(types[0]);
    select(types[1]);

    expect(component.selectedTypes).toEqual(types);
    expect(component.filteredTypes).toEqual([]);
  });

  it('should explain where types are created when the domain has none', async () => {
    await create({ types: [], loadFailed: false, domainHasTypes: false, createLink: ['/environments'] });

    expect(fixture.nativeElement.textContent).toContain('This domain has no authorization detail types');
    expect(fixture.nativeElement.querySelector('a').textContent).toContain('OAuth 2.0 > RAR');
  });

  it('should name the RAR menu without a link for users who may not create types', async () => {
    await create({ types: [], loadFailed: false, domainHasTypes: false, createLink: null });

    expect(fixture.nativeElement.querySelector('a')).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('OAuth 2.0 > RAR');
  });

  it('should say so when every type is already enabled', async () => {
    await create({ types: [], loadFailed: false, domainHasTypes: true, createLink: null });

    expect(fixture.nativeElement.textContent).toContain('already enabled on this application');
  });

  it('should say the types could not be loaded rather than that the domain has none', async () => {
    await create({ types: [], loadFailed: true, domainHasTypes: false, createLink: null });

    expect(fixture.nativeElement.textContent).toContain('could not be loaded');
    expect(fixture.nativeElement.textContent).not.toContain('This domain has no authorization detail types');
  });
});
