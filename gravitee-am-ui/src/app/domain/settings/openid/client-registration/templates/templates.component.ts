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

import { AfterViewInit, Component, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { MatPaginator } from '@angular/material/paginator';
import { MatSort } from '@angular/material/sort';
import { MatTableDataSource } from '@angular/material/table';
import { ActivatedRoute } from '@angular/router';
import { map } from 'lodash';
import { Subject, takeUntil } from 'rxjs';

import { SnackbarService } from '../../../../../services/snackbar.service';
import { ApplicationService } from '../../../../../services/application.service';
import { AuthService } from '../../../../../services/auth.service';
import { DomainStoreService } from '../../../../../stores/domain.store';

export interface Client {
  id: string;
  clientId: string;
  name: string;
  template: boolean;
}

@Component({
  selector: 'app-openid-client-registration-templates',
  templateUrl: './templates.component.html',
  styleUrls: ['./templates.component.scss'],
  standalone: false,
})
export class ClientRegistrationTemplatesComponent implements OnInit, AfterViewInit, OnDestroy {
  domain: any = {};
  dcrIsEnabled: boolean;
  templateIsEnabled: boolean;
  emptyStateMessage: string;
  apps: MatTableDataSource<Client>;
  displayedColumns: string[] = ['name', 'clientId', 'template'];
  readonly: boolean;

  private destroy$ = new Subject<void>();

  @ViewChild(MatSort) sort: MatSort;
  @ViewChild(MatPaginator) paginator: MatPaginator;

  constructor(
    private applicationService: ApplicationService,
    private route: ActivatedRoute,
    private snackbarService: SnackbarService,
    private authService: AuthService,
    private domainStore: DomainStoreService,
  ) {}

  ngOnInit() {
    this.domainStore.domain$.pipe(takeUntil(this.destroy$)).subscribe((domain) => {
      this.domain = domain;
      this.dcrIsEnabled = this.domain.oidc.clientRegistrationSettings.isDynamicClientRegistrationEnabled;
      this.templateIsEnabled = this.domain.oidc.clientRegistrationSettings.isClientTemplateEnabled;
      this.initEmptyStateMessage();
    });
    this.readonly = !this.authService.hasPermissions(['domain_openid_create', 'domain_openid_update']);

    const datasource = map(
      this.route.snapshot.data['apps'].data,
      (app) =>
        <Client>{
          id: app.id,
          clientId: app.clientId,
          name: app.name,
          template: app.template,
        },
    );
    this.apps = new MatTableDataSource(datasource);
  }

  ngOnDestroy() {
    this.destroy$.next();
    this.destroy$.complete();
  }

  ngAfterViewInit() {
    this.applySort();
    this.applyPagination();
  }

  initEmptyStateMessage() {
    if (!this.templateIsEnabled) {
      this.emptyStateMessage = 'Dynamic Client Registration Templates is disabled';
    } else if (!this.dcrIsEnabled) {
      this.emptyStateMessage = 'Openid Connect Dynamic Client Registration is disabled.';
    }
  }

  /** Apply pagination on table */
  applyPagination() {
    this.apps.paginator = this.paginator;
  }

  /** Apply sort on table */
  applySort() {
    this.apps.sort = this.sort;
  }

  /** Apply filter on table */
  applyFilter(filterValue: string) {
    this.apps.filter = filterValue.trim().toLowerCase();
  }

  applyChange(client, event) {
    client.template = event.checked;
    this.applicationService.patch(this.domain.id, client.id, { template: event.checked }).subscribe(() => {
      this.snackbarService.open('Application updated');
    });
    this.applySort();
  }
}
