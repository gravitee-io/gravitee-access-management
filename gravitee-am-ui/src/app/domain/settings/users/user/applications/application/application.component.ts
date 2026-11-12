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
import { Component, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { EMPTY, Subject, Subscription } from 'rxjs';
import { catchError, debounceTime, distinctUntilChanged, filter, finalize, switchMap, tap } from 'rxjs/operators';
import { find } from 'lodash';
import { DatatableComponent } from '@swimlane/ngx-datatable';

import { SnackbarService } from '../../../../../../services/snackbar.service';
import { DialogService } from '../../../../../../services/dialog.service';
import { UserService } from '../../../../../../services/user.service';
import { AuthService } from '../../../../../../services/auth.service';
import { CursorResponse, NgxTablePageInfo } from '../../../../../../utils/cursor';

@Component({
  selector: 'app-user-application',
  templateUrl: './application.component.html',
  styleUrls: ['./application.component.scss'],
  standalone: false,
})
export class UserApplicationComponent implements OnInit, OnDestroy {
  private domainId: string;
  private userId: string;
  private detailsLoadSubject = new Subject<{ cursor?: string }>();
  private detailsTypeFilterSubject = new Subject<string>();
  private detailsSubscriptions: Subscription[] = [];
  private detailsTypeFilter: string;
  application: any;
  applicationDisplayName: string;
  clientId: string;
  consents: any[];
  authorizationDetailConsents: any[] = [];
  hasAuthorizationDetailConsents = false;
  authorizationDetailConsentsLoaded = false;
  detailsLoading = false;
  detailsPage = {
    totalElements: 0,
    pageNumber: 0,
    size: 10,
  };
  detailsNextCursor: string;
  detailsSorts = [{ prop: 'updatedAt', dir: 'desc' }];
  canRevoke: boolean;
  detailEditorOptions = {
    lineWrapping: true,
    lineNumbers: true,
    readOnly: true,
    mode: 'application/json',
    foldGutter: true,
    gutters: ['CodeMirror-linenumbers', 'CodeMirror-foldgutter'],
  };

  @ViewChild('authorizationDetailsTable')
  authorizationDetailsTable: DatatableComponent;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private snackbarService: SnackbarService,
    private dialogService: DialogService,
    private userService: UserService,
    private authService: AuthService,
  ) {}

  ngOnInit() {
    this.application = this.route.snapshot.data['application'];
    this.domainId = this.route.snapshot.data['domain']?.id;
    this.userId = this.route.snapshot.params['userId'];
    this.consents = this.route.snapshot.data['consents'] ?? [];
    this.clientId = this.route.snapshot.queryParamMap.get('clientId');
    this.canRevoke = this.authService.hasPermissions(['domain_user_update']);
    this.applicationDisplayName = this.resolveApplicationDisplayName();

    this.detailsSubscriptions.push(
      this.detailsLoadSubject
        .pipe(
          switchMap(({ cursor }) => {
            this.detailsLoading = true;
            return this.detailsRequest(cursor).pipe(
              catchError(() => EMPTY),
              finalize(() => (this.detailsLoading = false)),
            );
          }),
        )
        .subscribe((page) => this.applyDetailsPage(page)),
      this.detailsTypeFilterSubject.pipe(debounceTime(400), distinctUntilChanged()).subscribe((type) => {
        this.detailsTypeFilter = type || undefined;
        this.restartDetails();
      }),
    );
    this.loadDetails();
  }

  ngOnDestroy() {
    this.detailsSubscriptions.forEach((subscription) => subscription.unsubscribe());
  }

  private resolveApplicationDisplayName(): string {
    const fromConsent = (this.consents?.[0] ?? this.authorizationDetailConsents?.[0])?.clientEntity?.name;
    if (fromConsent) {
      return fromConsent;
    }
    if (this.application?.name) {
      return this.application.name;
    }
    return this.clientId ?? '';
  }

  revokeApplication(event) {
    event.preventDefault();
    this.dialogService
      .confirm('Revoke access', 'Are you sure you want to revoke application access ?')
      .pipe(
        filter((res) => res),
        switchMap(() => this.userService.revokeConsents(this.domainId, this.userId, this.clientId)),
        tap(() => {
          this.snackbarService.open('Access for application ' + this.applicationDisplayName + ' revoked');
          this.router.navigate(['..'], { relativeTo: this.route });
        }),
      )
      .subscribe();
  }

  revokeConsent(event, consent) {
    event.preventDefault();
    this.dialogService
      .confirm('Revoke access', 'Are you sure you want to revoke this permission ?')
      .pipe(
        filter((res) => res),
        switchMap(() => this.userService.revokeConsent(this.domainId, this.userId, consent.id)),
        tap(() => {
          this.snackbarService.open('Permission ' + consent.scopeEntity.name + ' revoked');
          if (this.consents.length === 1 && !this.hasAuthorizationDetailConsents) {
            this.router.navigate(['..'], { relativeTo: this.route });
          } else {
            this.loadConsents();
          }
        }),
      )
      .subscribe();
  }

  loadConsents() {
    this.userService.consents(this.domainId, this.userId, null).subscribe((consents) => {
      this.consents = consents.filter((consent) => consent.clientId === this.clientId);
      this.consents = [...this.consents];
      this.applicationDisplayName = this.resolveApplicationDisplayName();
    });
  }

  get showScopeConsents() {
    return this.consents.length > 0 || (this.authorizationDetailConsentsLoaded && !this.hasAuthorizationDetailConsents);
  }

  onDetailsTypeFilter(event) {
    this.detailsTypeFilterSubject.next(event?.target?.value?.trim());
  }

  setDetailsPage(pageInfo: NgxTablePageInfo) {
    const cursor = this.detailsPage.pageNumber + 1 === pageInfo.offset ? this.detailsNextCursor : undefined;
    this.detailsPage.pageNumber = pageInfo.offset;
    this.loadDetails(cursor);
  }

  setDetailsSort($event: any) {
    const sort = $event?.sorts?.[0];
    if (!sort) {
      return;
    }
    this.detailsSorts = [sort];
    this.restartDetails();
  }

  private restartDetails() {
    this.detailsPage.pageNumber = 0;
    this.detailsNextCursor = undefined;
    this.loadDetails();
  }

  private loadDetails(cursor?: string) {
    this.detailsLoadSubject.next({ cursor });
  }

  private detailsRequest(cursor?: string) {
    if (cursor) {
      return this.userService.nextAuthorizationDetailConsents(cursor);
    }
    return this.userService.authorizationDetailConsents(
      this.domainId,
      this.userId,
      { clientId: this.clientId, type: this.detailsTypeFilter ? '*' + this.detailsTypeFilter + '*' : undefined },
      this.detailsPage.size,
      this.detailsPage.pageNumber,
      this.detailsSorts[0],
    );
  }

  private applyDetailsPage(page: CursorResponse) {
    this.authorizationDetailConsents = page.data;
    this.detailsPage.totalElements = page.totalCount;
    this.detailsPage.pageNumber = page.page;
    this.detailsNextCursor = page.nextCursor;
    if (!this.detailsTypeFilter) {
      this.hasAuthorizationDetailConsents = page.totalCount > 0;
    }
    this.authorizationDetailConsentsLoaded = true;
    this.applicationDisplayName = this.resolveApplicationDisplayName();
  }

  toggleExpandRow(row) {
    this.authorizationDetailsTable.rowDetail.toggleExpandRow(row);
  }

  formatDetail(row) {
    return JSON.stringify(row.detail, null, 2);
  }

  canRevokeAccess() {
    return find(this.consents, { status: 'approved' });
  }

  getRowClass(row) {
    return {
      'row-disabled': row.status !== 'approved',
    };
  }
}
