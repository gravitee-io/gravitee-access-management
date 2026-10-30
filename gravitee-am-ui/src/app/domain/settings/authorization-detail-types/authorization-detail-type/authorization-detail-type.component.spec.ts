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
import { NO_ERRORS_SCHEMA } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { MatDialog } from '@angular/material/dialog';
import { ActivatedRoute, Router } from '@angular/router';
import { of, throwError } from 'rxjs';

import { AuthService } from '../../../../services/auth.service';
import { AuthorizationDetailTypeService } from '../../../../services/authorization-detail-type.service';
import { DialogService } from '../../../../services/dialog.service';
import { SnackbarService } from '../../../../services/snackbar.service';

import { AuthorizationDetailTypeComponent } from './authorization-detail-type.component';

describe('AuthorizationDetailTypeComponent', () => {
  const stored = { id: 'id-1', type: 'payment', name: 'Payment', description: 'd', schema: '{}', discovery: false, expiresIn: 60 };
  let route: any;
  let component: AuthorizationDetailTypeComponent;
  let fixture: ComponentFixture<AuthorizationDetailTypeComponent>;
  let service: { patch: jest.Mock; delete: jest.Mock };
  let dialogService: { confirm: jest.Mock };
  let router: { navigate: jest.Mock };
  let hasPermissions: jest.Mock;
  let dialog: { open: jest.Mock };

  beforeEach(async () => {
    route = { snapshot: { data: { domain: { id: 'domain-id' }, authorizationDetailType: stored } } };
    service = {
      patch: jest.fn().mockImplementation((_d, _i, patch) => of({ ...stored, ...patch })),
      delete: jest.fn().mockReturnValue(of(null)),
    };
    dialogService = { confirm: jest.fn().mockReturnValue(of(true)) };
    router = { navigate: jest.fn() };
    hasPermissions = jest.fn().mockReturnValue(true);
    dialog = { open: jest.fn() };

    TestBed.configureTestingModule({
      imports: [FormsModule],
      declarations: [AuthorizationDetailTypeComponent],
      providers: [
        { provide: AuthorizationDetailTypeService, useValue: service },
        { provide: DialogService, useValue: dialogService },
        { provide: SnackbarService, useValue: { open: jest.fn() } },
        { provide: AuthService, useValue: { hasPermissions } },
        { provide: MatDialog, useValue: dialog },
        { provide: Router, useValue: router },
        { provide: ActivatedRoute, useValue: route },
      ],
      schemas: [NO_ERRORS_SCHEMA],
      teardown: { destroyAfterEach: false },
    }).compileComponents();

    fixture = TestBed.createComponent(AuthorizationDetailTypeComponent);
    component = fixture.componentInstance;
  });

  afterEach(() => fixture?.destroy());

  it('should have nothing to save until a field changes', () => {
    component.ngOnInit();

    expect(component.hasChanges).toBe(false);

    component.authorizationDetailType.name = 'Renamed';

    expect(component.hasChanges).toBe(true);
  });

  it('should have nothing to save when a change is undone', () => {
    component.ngOnInit();

    component.authorizationDetailType.name = 'Renamed';
    component.authorizationDetailType.name = stored.name;

    expect(component.hasChanges).toBe(false);
  });

  it('should send only the fields that changed', () => {
    component.ngOnInit();
    component.authorizationDetailType.name = 'Renamed';
    component.authorizationDetailType.discovery = true;

    component.update();

    expect(service.patch).toHaveBeenCalledWith('domain-id', 'id-1', { name: 'Renamed', discovery: true });
  });

  it('should clear the optional fields left empty with null', () => {
    component.ngOnInit();
    component.authorizationDetailType.description = '';
    component.authorizationDetailType.schema = '';
    component.clearExpiry();

    component.update();

    expect(service.patch).toHaveBeenCalledWith('domain-id', 'id-1', { description: null, schema: null, expiresIn: null });
  });

  it('should not see a change in fields the type never had', () => {
    route.snapshot.data.authorizationDetailType = { id: 'id-1', type: 'payment', name: 'Payment', discovery: false };
    component.ngOnInit();

    component.authorizationDetailType.description = '';

    expect(component.hasChanges).toBe(false);
  });

  it('should send a new duration in seconds', () => {
    component.ngOnInit();
    component.expiresIn = 2;
    component.unitTime = 'minutes';

    component.update();

    expect(service.patch).toHaveBeenCalledWith('domain-id', 'id-1', { expiresIn: 120 });
  });

  it('should not see a change in a duration equal to the current one', () => {
    component.ngOnInit();
    component.expiresIn = 1;
    component.unitTime = 'minutes';

    expect(component.hasChanges).toBe(false);
  });

  it('should compare against the saved type after saving', () => {
    component.ngOnInit();
    component.expiresIn = 2;
    component.unitTime = 'minutes';

    component.update();

    expect(component.hasChanges).toBe(false);
    expect(component.expiresIn).toBeNull();
    expect(component.authorizationDetailType.expiresIn).toEqual(120);
  });

  it('should allow saving a duration equal to the current one after clearing it', () => {
    component.ngOnInit();

    component.clearExpiry();
    component.expiresIn = 1;
    component.unitTime = 'minutes';
    expect(component.hasChanges).toBe(true);

    component.update();
    expect(service.patch).toHaveBeenCalledWith('domain-id', 'id-1', { expiresIn: 60 });
    expect(component.hasChanges).toBe(false);
  });

  it('should show why the server refused until the form changes', () => {
    service.patch.mockReturnValue(throwError(() => ({ status: 400, error: { message: 'Schema root must declare a type' } })));
    component.ngOnInit();

    component.authorizationDetailType.schema = '{"a":1}';
    component.update();
    expect(component.saveError).toBe('Schema root must declare a type');

    component.authorizationDetailType.schema = '{"a":2}';
    expect(component.saveError).toBeNull();
  });

  it('should drop the save error once a save succeeds', () => {
    service.patch.mockReturnValueOnce(throwError(() => ({ status: 500, error: {} })));
    component.ngOnInit();

    component.authorizationDetailType.name = 'Renamed';
    component.update();
    expect(component.saveError).toBe('Server error');

    component.update();
    expect(component.saveError).toBeNull();
  });

  it('should open the schema example as read-only without the update permission', () => {
    hasPermissions.mockReturnValue(false);
    dialog.open.mockReturnValue({ afterClosed: () => of(undefined) });
    component.ngOnInit();

    component.openSchemaExample();

    expect(dialog.open.mock.calls[0][1].data).toEqual({ type: 'payment', hasSchema: true, editable: false });
    expect(component.authorizationDetailType.schema).toBe('{}');
  });

  it('should make the editor read-only without the update permission', () => {
    hasPermissions.mockImplementation(([permission]) => permission !== 'domain_authorization_detail_type_update');

    component.ngOnInit();

    expect(component.editMode).toBe(false);
    expect(component.deleteMode).toBe(true);
    expect(component.schemaEditorOptions.readOnly).toBe(true);
  });

  it('should delete and return to the list', () => {
    component.ngOnInit();

    component.delete(new Event('click'));

    expect(service.delete).toHaveBeenCalledWith('domain-id', 'id-1');
    expect(router.navigate).toHaveBeenCalledWith(['..'], { relativeTo: route });
  });

  it('should not delete when the confirmation is cancelled', () => {
    component.ngOnInit();
    dialogService.confirm.mockReturnValue(of(false));

    component.delete(new Event('click'));

    expect(service.delete).not.toHaveBeenCalled();
  });
});
