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
import {Component, OnInit} from '@angular/core';
import {ActivatedRoute} from "@angular/router";
import {DialogService} from "../services/dialog.service";
import {SnackbarService} from "../services/snackbar.service";
import {ClientService} from "../services/client.service";
import {DashboardService} from "../services/dashboard.service";

@Component({
  selector: 'app-clients',
  templateUrl: './clients.component.html',
  styleUrls: ['./clients.component.scss']
})
export class ClientsComponent implements OnInit {
  private searchValue: string;
  domainId: string;
  domain: any = {};
  newClientRouterLink: any[] = ['/dashboard', 'clients', 'new'];
  page: any = {};
  pagedClients: any;
  clients: any[];

  constructor(private dialogService: DialogService,
              private snackbarService: SnackbarService,
              private clientService: ClientService,
              private dashboardService: DashboardService,
              private route: ActivatedRoute) {
    this.page.pageNumber = 0;
    this.page.size = 10;
  }

  ngOnInit() {
    this.domain = this.route.snapshot.data['domain'];
    this.pagedClients = this.route.snapshot.data['clients'];
    this.clients = this.pagedClients.data;
    this.page.totalElements = this.pagedClients.totalCount;
    if (this.domain) {
      this.newClientRouterLink = ['/domains', this.domain.id, 'clients', 'new'];
    }
  }

  onSearch(event) {
    this.searchValue = event.target.value;
    this.loadClients();
  }

  setPage(pageInfo) {
    this.page.pageNumber = pageInfo.offset;
    this.loadClients();
  }

  loadClients() {
    let findClients;
    if (this.domain) {
      findClients = (this.searchValue) ?
        this.clientService.search(this.domain.id, this.searchValue + '*', this.page.pageNumber, this.page.size) :
        this.clientService.findByDomain(this.domain.id, this.page.pageNumber, this.page.size);
    } else {
      findClients = (this.searchValue) ?
        this.dashboardService.searchClients(this.searchValue + '*', this.page.pageNumber, this.page.size) :
        this.dashboardService.findClients(null, this.page.pageNumber, this.page.size);
    }
    findClients.subscribe(pagedUsers => {
      this.page.totalElements = pagedUsers.totalCount;
      this.clients = pagedUsers.data;
    });
  }

  get isEmpty() {
    return !this.clients || this.clients.length === 0 && !this.searchValue;
  }
}
