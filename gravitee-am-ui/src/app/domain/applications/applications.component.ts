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
import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';

import { DialogService } from '../../services/dialog.service';
import { SnackbarService } from '../../services/snackbar.service';
import { ApplicationService } from '../../services/application.service';

@Component({
  selector: 'app-applications',
  templateUrl: './applications.component.html',
  styleUrls: ['./applications.component.scss'],
  standalone: false,
})
export class ApplicationsComponent implements OnInit {
  applications: any[];
  private searchValue: string;
  domainId: string;
  page: any = {};
  private filter: any;
  isMcpRoute: boolean = false;

  constructor(
    private dialogService: DialogService,
    private snackbarService: SnackbarService,
    private applicationService: ApplicationService,
    private route: ActivatedRoute,
    private router: Router,
  ) {
    this.page.pageNumber = 0;
    this.page.size = 10;
  }

  ngOnInit() {
    this.domainId = this.route.snapshot.data['domain']?.id;
    this.filter = this.route.snapshot.data['filter'];
    const pagedApps = this.route.snapshot.data['applications'];
    this.applications = this.applyFilter(pagedApps.data);
    this.page.totalElements = pagedApps.totalCount;
    
    // Check if this is an MCP route
    const currentUrl = this.router.url;
    this.isMcpRoute = currentUrl.endsWith('/mcp') || currentUrl.endsWith('/mcp/');
  }

  onSearch(event) {
    this.page.pageNumber = 0;
    this.searchValue = event.target.value;
    this.loadApps();
  }

  loadApps() {
    const findApps = this.searchValue
      ? this.applicationService.search(this.domainId, '*' + this.searchValue + '*')
      : this.applicationService.findByDomain(this.domainId, this.page.pageNumber, this.page.size);

    findApps.subscribe((pagedApps) => {
      this.page.totalElements = pagedApps.totalCount;
      this.applications = this.applyFilter(pagedApps.data);
    });
  }

  setPage(pageInfo) {
    this.page.pageNumber = pageInfo.offset;
    this.loadApps();
  }

  get isEmpty() {
    return !this.applications || (this.applications.length === 0 && !this.searchValue);
  }

  getToolCount(application: any): number {
    console.log('application', application);
    if (application?.toolCount) {
      return application.toolCount;
    }
    return 0;
  }

  private applyFilter(applications: any[]): any[] {
    // Check if the current URL ends with 'mcp'
    const currentUrl = this.router.url;
    const isMcpRoute = currentUrl.endsWith('/mcp') || currentUrl.endsWith('/mcp/');
    

    if (isMcpRoute) {
      // Filter to show only MCP applications
      return applications.filter(app => app.type === 'mcp');
    } else {
      // Filter out MCP applications when not on MCP route
      return applications.filter(app => app.type !== 'mcp');
    }

    // If there's a route data filter, apply it
    if (this.filter && this.filter.only && Array.isArray(this.filter.only)) {
      return applications.filter(app => {
        return this.filter.only.includes(app.type);
      });
    }

    // No filtering, return all applications
    return applications;
  }
}
