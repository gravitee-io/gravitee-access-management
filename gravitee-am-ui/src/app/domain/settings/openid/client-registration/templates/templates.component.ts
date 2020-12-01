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

import {AfterViewInit, Component, OnInit, ViewChild} from "@angular/core";
import {MatPaginator, MatSort, MatTableDataSource} from "@angular/material";
import {ActivatedRoute} from "@angular/router";
import {DomainService} from "../../../../../services/domain.service";
import {SnackbarService} from "../../../../../services/snackbar.service";
import {ApplicationService} from "../../../../../services/application.service";
import * as _ from 'lodash';
import {AuthService} from "../../../../../services/auth.service";

export interface Client {
  id: string;
  clientId: string;
  name: string;
  template: boolean;
}

@Component({
  selector: 'app-openid-client-registration-templates',
  templateUrl: './templates.component.html',
  styleUrls: ['./templates.component.scss']
})

export class ClientRegistrationTemplatesComponent implements OnInit, AfterViewInit {
  domain: any = {};
  dcrIsEnabled: boolean;
  templateIsEnabled: boolean;
  emptyStateMessage: string;
  apps: MatTableDataSource<Client>;
  displayedColumns: string[] = ['name', 'clientId', 'template'];
  readonly: boolean;

  @ViewChild(MatSort, { static: false }) sort: MatSort;
  @ViewChild(MatPaginator, { static: false }) paginator: MatPaginator;

  constructor(private domainService: DomainService,
              private applicationService: ApplicationService,
              private route: ActivatedRoute,
              private snackbarService: SnackbarService,
              private authService: AuthService) {
  }

  ngOnInit() {
    this.domain = this.route.snapshot.data['domain'];
    this.dcrIsEnabled = this.domain.oidc.clientRegistrationSettings.isDynamicClientRegistrationEnabled;
    this.templateIsEnabled = this.domain.oidc.clientRegistrationSettings.isClientTemplateEnabled;
    this.readonly = !this.authService.hasPermissions(['domain_openid_create', 'domain_openid_update']);
    this.initEmptyStateMessage();

    const datasource = _.map(this.route.snapshot.data['apps'].data,  app => <Client>{
      id: app.id, clientId: app.clientId, name: app.name, template: app.template
    });
    this.apps = new MatTableDataSource(datasource);
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
    this.applicationService.patch(this.domain.id, client.id, { 'template' : event.checked}).subscribe(data => {
      this.snackbarService.open('Application updated');
    });
    this.applySort();
  }
}
