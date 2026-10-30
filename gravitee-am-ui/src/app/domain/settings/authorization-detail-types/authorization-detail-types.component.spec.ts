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
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of, throwError } from 'rxjs';

import { AuthService } from '../../../services/auth.service';
import { AuthorizationDetailTypeService } from '../../../services/authorization-detail-type.service';
import { DialogService } from '../../../services/dialog.service';
import { SnackbarService } from '../../../services/snackbar.service';

import { AuthorizationDetailTypesComponent } from './authorization-detail-types.component';

describe('AuthorizationDetailTypesComponent', () => {
  const emptyPage = { totalCount: 0, data: [], page: 0 };
  const firstPage = { totalCount: 11, data: [{ id: 'id-1', type: 'a', discovery: false }], nextCursor: '/next', page: 0 };
  const defaultSort = { prop: 'type', dir: 'asc' };

  let component: AuthorizationDetailTypesComponent;
  let fixture: ComponentFixture<AuthorizationDetailTypesComponent>;
  let service: { list: jest.Mock; next: jest.Mock; patch: jest.Mock; delete: jest.Mock };
  let dialogService: { confirm: jest.Mock };
  let snackbarService: { open: jest.Mock };

  beforeEach(async () => {
    service = {
      list: jest.fn().mockReturnValue(of(firstPage)),
      next: jest.fn().mockReturnValue(of({ ...firstPage, nextCursor: undefined, page: 1 })),
      patch: jest.fn().mockReturnValue(of({})),
      delete: jest.fn().mockReturnValue(of(null)),
    };
    dialogService = { confirm: jest.fn().mockReturnValue(of(true)) };
    snackbarService = { open: jest.fn() };

    TestBed.configureTestingModule({
      declarations: [AuthorizationDetailTypesComponent],
      providers: [
        { provide: AuthorizationDetailTypeService, useValue: service },
        { provide: DialogService, useValue: dialogService },
        { provide: SnackbarService, useValue: snackbarService },
        { provide: AuthService, useValue: { hasPermissions: () => true } },
        { provide: ActivatedRoute, useValue: { snapshot: { data: { domain: { id: 'domain-id' } } } } },
      ],
      schemas: [NO_ERRORS_SCHEMA],
      teardown: { destroyAfterEach: false },
    }).compileComponents();

    fixture = TestBed.createComponent(AuthorizationDetailTypesComponent);
    component = fixture.componentInstance;
  });

  afterEach(() => fixture?.destroy());

  it('should load the first page sorted by type ascending', () => {
    fixture.detectChanges();

    expect(service.list).toHaveBeenCalledWith('domain-id', 10, 0, defaultSort, undefined);
    expect(component.showTable).toBe(true);
  });

  it('should show the empty state when the domain has no types', () => {
    service.list.mockReturnValue(of(emptyPage));

    fixture.detectChanges();

    expect(component.isEmpty).toBe(true);
    expect(component.showTable).toBe(false);
  });

  it('should show an empty list when a search matches nothing', fakeAsync(() => {
    fixture.detectChanges();
    service.list.mockReturnValue(of(emptyPage));

    component.onSearch({ target: { value: 'nothing' } });
    tick(400);

    expect(component.isEmpty).toBe(false);
    expect(component.showTable).toBe(true);
    expect(component.authorizationDetailTypes).toEqual([]);
  }));

  it('should search with wildcards and start again from the first page', fakeAsync(() => {
    fixture.detectChanges();
    component.page.pageNumber = 3;

    component.onSearch({ target: { value: 'pay' } });
    tick(400);

    expect(service.list).toHaveBeenLastCalledWith('domain-id', 10, 0, defaultSort, '*pay*');
  }));

  it('should follow nextCursor to the next page', () => {
    fixture.detectChanges();

    component.setPage({ offset: 1, count: 11, pageSize: 10, limit: 10 });

    expect(service.next).toHaveBeenCalledWith('/next');
    expect(service.list).toHaveBeenCalledTimes(1);
  });

  it('should request a page number for any page but the next', () => {
    fixture.detectChanges();
    component.page.pageNumber = 3;

    component.setPage({ offset: 2, count: 41, pageSize: 10, limit: 10 });

    expect(service.next).not.toHaveBeenCalled();
    expect(service.list).toHaveBeenLastCalledWith('domain-id', 10, 2, defaultSort, undefined);
  });

  it('should start again from the first page when the sort changes', () => {
    fixture.detectChanges();
    component.page.pageNumber = 3;

    component.setSort({ sorts: [{ prop: 'name', dir: 'desc' }] });

    expect(service.list).toHaveBeenLastCalledWith('domain-id', 10, 0, { prop: 'name', dir: 'desc' }, undefined);
  });

  it('should patch only discovery and confirm', () => {
    fixture.detectChanges();
    const row = { id: 'id-1', discovery: false };

    component.toggleDiscovery(row, { checked: true, source: {} });

    expect(service.patch).toHaveBeenCalledWith('domain-id', 'id-1', { discovery: true });
    expect(row.discovery).toBe(true);
    expect(snackbarService.open).toHaveBeenCalledWith('Authorization detail type updated');
  });

  it('should put the toggle back when the patch fails', () => {
    fixture.detectChanges();
    service.patch.mockReturnValue(throwError(() => ({ status: 500, error: { message: 'failed' } })));
    const row = { id: 'id-1', discovery: false };
    const source = { checked: true };

    component.toggleDiscovery(row, { checked: true, source });

    expect(source.checked).toBe(false);
    expect(row.discovery).toBe(false);
    expect(snackbarService.open).toHaveBeenCalledWith('failed');
  });

  it('should keep loading after a failed request', () => {
    fixture.detectChanges();
    service.next.mockReturnValue(throwError(() => new Error('failed')));
    component.setPage({ offset: 1, count: 11, pageSize: 10, limit: 10 });

    component.setPage({ offset: 0, count: 11, pageSize: 10, limit: 10 });

    expect(service.list).toHaveBeenCalledTimes(2);
  });

  it('should not delete when the confirmation is cancelled', () => {
    fixture.detectChanges();
    dialogService.confirm.mockReturnValue(of(false));

    component.delete('id-1', new Event('click'));

    expect(service.delete).not.toHaveBeenCalled();
    expect(service.list).toHaveBeenCalledTimes(1);
  });

  it('should delete, confirm and refresh the list', () => {
    fixture.detectChanges();

    component.delete('id-1', new Event('click'));

    expect(service.delete).toHaveBeenCalledWith('domain-id', 'id-1');
    expect(snackbarService.open).toHaveBeenCalledWith('Authorization detail type deleted');
    expect(service.list).toHaveBeenCalledTimes(2);
  });

  it('should show the empty state after deleting the last type', () => {
    fixture.detectChanges();
    service.list.mockReturnValue(of(emptyPage));

    component.delete('id-1', new Event('click'));

    expect(component.isEmpty).toBe(true);
  });

  it('should go back a page after deleting the only row of a later page', () => {
    service.list.mockReturnValue(of({ ...firstPage, page: 2 }));
    fixture.detectChanges();

    component.delete('id-1', new Event('click'));

    expect(service.list).toHaveBeenLastCalledWith('domain-id', 10, 1, defaultSort, undefined);
  });
});
