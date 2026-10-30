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
import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { EMPTY, Subject, Subscription } from 'rxjs';
import { catchError, debounceTime, distinctUntilChanged, filter, finalize, switchMap, tap } from 'rxjs/operators';

import { AuthService } from '../../../services/auth.service';
import { AuthorizationDetailTypeService } from '../../../services/authorization-detail-type.service';
import { DialogService } from '../../../services/dialog.service';
import { SnackbarService } from '../../../services/snackbar.service';
import { CursorResponse, NgxTablePageInfo } from '../../../utils/cursor';

import { saveErrorMessage } from './authorization-detail-type.validation';

@Component({
  selector: 'app-authorization-detail-types',
  templateUrl: './authorization-detail-types.component.html',
  styleUrls: ['./authorization-detail-types.component.scss'],
  standalone: false,
})
export class AuthorizationDetailTypesComponent implements OnInit, OnDestroy {
  private searchSubject = new Subject<string>();
  private loadSubject = new Subject<{ cursor?: string }>();
  private searchSubscription: Subscription;
  private loadSubscription: Subscription;
  private searchValue: string;
  private hasLoaded = false;
  private domainId: string;
  authorizationDetailTypes: any[] = [];
  loading = false;
  canEdit: boolean;
  canDelete: boolean;
  page = {
    totalElements: 0,
    pageNumber: 0,
    size: 10,
  };
  nextCursor: string;
  sorts = [{ prop: 'type', dir: 'asc' }];

  constructor(
    private authorizationDetailTypeService: AuthorizationDetailTypeService,
    private dialogService: DialogService,
    private snackbarService: SnackbarService,
    private authService: AuthService,
    private route: ActivatedRoute,
  ) {}

  ngOnInit() {
    this.domainId = this.route.snapshot.data['domain']?.id;
    this.canEdit = this.authService.hasPermissions(['domain_authorization_detail_type_update']);
    this.canDelete = this.authService.hasPermissions(['domain_authorization_detail_type_delete']);

    this.loadSubscription = this.loadSubject
      .pipe(
        switchMap(({ cursor }) => {
          this.loading = true;
          return this.listRequest(cursor).pipe(
            catchError(() => EMPTY),
            finalize(() => (this.loading = false)),
          );
        }),
      )
      .subscribe((page) => this.applyPage(page));

    this.searchSubscription = this.searchSubject.pipe(debounceTime(400), distinctUntilChanged()).subscribe((value) => {
      this.searchValue = value;
      this.restart();
    });

    this.load();
  }

  ngOnDestroy() {
    this.searchSubscription?.unsubscribe();
    this.loadSubscription?.unsubscribe();
  }

  onSearch(event) {
    this.searchSubject.next(event?.target?.value?.trim());
  }

  setPage(pageInfo: NgxTablePageInfo) {
    const cursor = this.page.pageNumber + 1 === pageInfo.offset ? this.nextCursor : undefined;
    this.page.pageNumber = pageInfo.offset;
    this.load(cursor);
  }

  setSort($event: any) {
    const sort = $event?.sorts?.[0];
    if (!sort) {
      return;
    }
    this.sorts = [sort];
    this.restart();
  }

  get isEmpty() {
    return this.hasLoaded && !this.searchValue && this.authorizationDetailTypes.length === 0;
  }

  get showTable() {
    return this.hasLoaded && !this.isEmpty;
  }

  toggleDiscovery(row: any, event: any) {
    this.authorizationDetailTypeService.patch(this.domainId, row.id, { discovery: event.checked }).subscribe({
      next: () => {
        row.discovery = event.checked;
        this.snackbarService.open('Authorization detail type updated');
      },
      error: (error: unknown) => {
        event.source.checked = row.discovery;
        this.snackbarService.open(saveErrorMessage(error));
      },
    });
  }

  delete(id: string, event: Event) {
    event.preventDefault();
    this.dialogService
      .confirm('Delete Authorization Detail Type', 'Are you sure you want to delete this authorization detail type?')
      .pipe(
        filter((confirmed) => confirmed),
        switchMap(() => this.authorizationDetailTypeService.delete(this.domainId, id)),
        tap(() => {
          this.snackbarService.open('Authorization detail type deleted');
          if (this.authorizationDetailTypes.length === 1 && this.page.pageNumber > 0) {
            this.page.pageNumber--;
          }
          this.load();
        }),
      )
      .subscribe();
  }

  private restart() {
    this.page.pageNumber = 0;
    this.nextCursor = undefined;
    this.load();
  }

  private load(cursor?: string) {
    this.loadSubject.next({ cursor });
  }

  private listRequest(cursor?: string) {
    if (cursor) {
      return this.authorizationDetailTypeService.next(cursor);
    }
    const query = this.searchValue ? '*' + this.searchValue + '*' : undefined;
    return this.authorizationDetailTypeService.list(this.domainId, this.page.size, this.page.pageNumber, this.sorts[0], query);
  }

  private applyPage(page: CursorResponse) {
    this.authorizationDetailTypes = page.data;
    this.page.totalElements = page.totalCount;
    this.page.pageNumber = page.page;
    this.nextCursor = page.nextCursor;
    this.hasLoaded = true;
  }
}
