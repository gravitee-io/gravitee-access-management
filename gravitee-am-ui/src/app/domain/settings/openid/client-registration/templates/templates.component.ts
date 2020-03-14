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
import {DomainService} from "../../../../../services/domain.service";
import {ActivatedRoute} from "@angular/router";
import {SnackbarService} from "../../../../../services/snackbar.service";
import {MatPaginator, MatSort, MatTableDataSource} from "@angular/material";
import * as _ from 'lodash';
import {ClientService} from "../../../../../services/client.service";

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

  clients: MatTableDataSource<Client>;
  displayedColumns: string[] = ['clientId', 'clientName', 'template'];

  @ViewChild(MatSort) sort: MatSort;
  @ViewChild(MatPaginator) paginator: MatPaginator;

  constructor(private domainService: DomainService, private clientService: ClientService,
              private route: ActivatedRoute, private snackbarService: SnackbarService) {
  }

  ngOnInit() {
    this.domain = this.route.snapshot.data['domain'];
    this.dcrIsEnabled = this.domain.oidc.clientRegistrationSettings.isDynamicClientRegistrationEnabled;
    this.templateIsEnabled = this.domain.oidc.clientRegistrationSettings.isClientTemplateEnabled;
    this.initEmptyStateMessage();

    const pagedClients = this.route.snapshot.data['clients'];
    const datasource = _.map(pagedClients.data,  client => <Client>{
      id: client.id, clientId: client.clientId, name: client.clientName, template: client.template
    });
    this.clients = new MatTableDataSource(datasource);
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
    this.clients.paginator = this.paginator;
  }

  /** Apply sort on table */
  applySort() {
    this.clients.sort = this.sort;
  }

  /** Apply filter on table */
  applyFilter(filterValue: string) {
    this.clients.filter = filterValue.trim().toLowerCase();
  }

  applyChange(client, event) {
    client.template = event.checked;
    this.clientService.patchTemplate(this.domain.id, client.id, event.checked).subscribe(data => {
      this.snackbarService.open("Client updated");
    });
    this.applySort();
  }
}
