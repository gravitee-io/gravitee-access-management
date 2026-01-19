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
import { MatDialog } from '@angular/material/dialog';
import { of, throwError } from 'rxjs';
import { CUSTOM_ELEMENTS_SCHEMA } from '@angular/core';

import { SnackbarService } from '../../services/snackbar.service';
import { ClientSecret, ClientSecretService } from '../../services/client-secret.service';

import { ClientSecretsManagementComponent } from './client-secrets-management.component';

describe('ClientSecretsManagementComponent', () => {
  let component: ClientSecretsManagementComponent;
  let fixture: ComponentFixture<ClientSecretsManagementComponent>;
  let clientSecretServiceSpy: any;
  let snackbarServiceSpy: any;
  let matDialogSpy: any;

  const mockSecrets: ClientSecret[] = [
    { id: '1', name: 'Leaking Secret', value: 'secret-value-1' },
    { id: '2', name: 'Another Secret', value: 'secret-value-2' },
  ];

  beforeEach(async () => {
    clientSecretServiceSpy = jasmine.createSpyObj('ClientSecretService', ['list', 'create', 'renew', 'delete']);
    snackbarServiceSpy = jasmine.createSpyObj('SnackbarService', ['open']);
    matDialogSpy = jasmine.createSpyObj('MatDialog', ['open']);

    await TestBed.configureTestingModule({
      declarations: [ClientSecretsManagementComponent],
      providers: [
        { provide: ClientSecretService, useValue: clientSecretServiceSpy },
        { provide: SnackbarService, useValue: snackbarServiceSpy },
        { provide: MatDialog, useValue: matDialogSpy },
      ],
      schemas: [CUSTOM_ELEMENTS_SCHEMA],
    }).compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(ClientSecretsManagementComponent);
    component = fixture.componentInstance;
    component.service = clientSecretServiceSpy;
    component.domainId = 'domain-id';
    component.parentId = 'parent-id';
    component.clientId = 'client-id';

    clientSecretServiceSpy.list.and.returnValue(of(mockSecrets));
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should load secrets on init', () => {
    expect(clientSecretServiceSpy.list).toHaveBeenCalledWith('domain-id', 'parent-id');
    expect(component.clientSecrets.length).toBe(2);
    expect(component.clientSecrets[0].name).toBe('Leaking Secret');
  });

  it('should handle error when loading secrets', () => {
    clientSecretServiceSpy.list.and.returnValue(throwError(() => new Error('Error')));
    component.loadSecrets();
    expect(snackbarServiceSpy.open).toHaveBeenCalledWith('Error fetching client secrets');
  });

  describe('openNewSecret', () => {
    it('should open dialog and create secret on success', () => {
      const dialogRefSpyObj = jasmine.createSpyObj('MatDialogRef', ['afterClosed', 'close']);
      dialogRefSpyObj.afterClosed.and.returnValue(of('New Secret Description'));

      const copyDialogRefSpyObj = jasmine.createSpyObj('MatDialogRef', ['afterClosed', 'close']);
      copyDialogRefSpyObj.afterClosed.and.returnValue(of(true));

      matDialogSpy.open.and.returnValues(dialogRefSpyObj, copyDialogRefSpyObj);

      const newSecret: ClientSecret = { id: '3', name: 'New Secret Description', value: 'new-value' };
      clientSecretServiceSpy.create.and.returnValue(of({ value: newSecret } as any)); // Adjusting for service return type
      clientSecretServiceSpy.list.and.returnValue(of([...mockSecrets, newSecret]));

      const event = new MouseEvent('click');
      spyOn(event, 'preventDefault');

      component.openNewSecret(event);

      expect(matDialogSpy.open).toHaveBeenCalledTimes(2); // New Dialog + Copy Dialog
      expect(clientSecretServiceSpy.create).toHaveBeenCalledWith('domain-id', 'parent-id', 'New Secret Description');
      expect(clientSecretServiceSpy.list).toHaveBeenCalled();
      expect(component.clientSecrets.length).toBe(3);
    });
  });

  describe('deleteSecret', () => {
    it('should open dialog and delete secret on confirmation', () => {
      const secretToDelete = mockSecrets[0];
      const dialogRefSpyObj = jasmine.createSpyObj('MatDialogRef', ['afterClosed', 'close']);
      dialogRefSpyObj.afterClosed.and.returnValue(of('delete'));

      matDialogSpy.open.and.returnValue(dialogRefSpyObj);
      clientSecretServiceSpy.delete.and.returnValue(of(void 0));
      clientSecretServiceSpy.list.and.returnValue(of([mockSecrets[1]]));

      const event = new MouseEvent('click');
      spyOn(event, 'preventDefault');

      component.deleteSecret(secretToDelete, event);

      expect(matDialogSpy.open).toHaveBeenCalled();
      expect(clientSecretServiceSpy.delete).toHaveBeenCalledWith('domain-id', 'parent-id', secretToDelete.id);
      expect(clientSecretServiceSpy.list).toHaveBeenCalled();
      expect(component.clientSecrets.length).toBe(1);
      expect(snackbarServiceSpy.open).toHaveBeenCalledWith(`Secret ${secretToDelete.name} deleted`);
    });
  });

  describe('renewSecret', () => {
    it('should open dialog and renew secret on confirmation', () => {
      const secretToRenew = mockSecrets[0];
      const dialogRefSpyObj = jasmine.createSpyObj('MatDialogRef', ['afterClosed', 'close']);
      dialogRefSpyObj.afterClosed.and.returnValue(of('renew'));

      const copyDialogRefSpyObj = jasmine.createSpyObj('MatDialogRef', ['afterClosed', 'close']);
      copyDialogRefSpyObj.afterClosed.and.returnValue(of(true));

      matDialogSpy.open.and.returnValues(dialogRefSpyObj, copyDialogRefSpyObj);

      const renewedSecret: ClientSecret = { ...secretToRenew, value: 'renewed-value' };
      clientSecretServiceSpy.renew.and.returnValue(of({ value: renewedSecret } as any));
      clientSecretServiceSpy.list.and.returnValue(of([renewedSecret, mockSecrets[1]]));

      const event = new MouseEvent('click');
      spyOn(event, 'preventDefault');

      component.renewSecret(secretToRenew, event);

      expect(matDialogSpy.open).toHaveBeenCalledTimes(2); // Renew Dialog + Copy Dialog
      expect(clientSecretServiceSpy.renew).toHaveBeenCalledWith('domain-id', 'parent-id', secretToRenew.id);
      expect(clientSecretServiceSpy.list).toHaveBeenCalled();
    });
  });

  describe('openSettings', () => {
    it('should emit settingsClicked event', () => {
      spyOn(component.settingsClicked, 'emit');
      component.showSettings = true;
      const event = new MouseEvent('click');
      spyOn(event, 'preventDefault');

      component.openSettings(event);

      expect(component.settingsClicked.emit).toHaveBeenCalledWith(event);
    });

    it('should not emit settingsClicked event if showSettings is false', () => {
      spyOn(component.settingsClicked, 'emit');
      component.showSettings = false;
      const event = new MouseEvent('click');
      spyOn(event, 'preventDefault');

      component.openSettings(event);

      expect(component.settingsClicked.emit).not.toHaveBeenCalled();
    });
  });
});
