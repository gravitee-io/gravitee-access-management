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
import { Component, ElementRef, OnInit, ViewChild } from '@angular/core';
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
  @ViewChild('searchInput') searchInput: ElementRef;

  applications: any[];
  private searchValue: string;
  private isLoading = false;
  domainId: string;
  page: any = {};
  selectedTypes: string[] = [];

  applicationTypes = [
    { name: 'Web', type: 'WEB', icon: 'language' },
    { name: 'Single-Page App', type: 'BROWSER', icon: 'web' },
    { name: 'Native', type: 'NATIVE', icon: 'devices_other' },
    { name: 'Agentic Application', type: 'AGENT', icon: 'memory' },
    { name: 'Backend to Backend', type: 'SERVICE', icon: 'storage' },
    { name: 'Resource Server', type: 'RESOURCE_SERVER', icon: 'folder_shared' },
  ];

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
    const pagedApps = this.route.snapshot.data['applications'];
    this.applications = pagedApps.data;
    this.page.totalElements = pagedApps.totalCount;

    const typeParams = this.route.snapshot.queryParamMap.getAll('type');
    if (typeParams.length > 0) {
      const validTypes = this.applicationTypes.map(at => at.type);
      this.selectedTypes = typeParams.filter(t => validTypes.includes(t));
      if (this.selectedTypes.length > 0) {
        this.loadApps();
      }
    }
  }

  onSearch(event) {
    this.page.pageNumber = 0;
    this.searchValue = event.target.value;
    this.loadApps();
  }

  onTypeFilterChange() {
    this.page.pageNumber = 0;
    this.updateUrlState();
    this.loadApps();
  }

  clearAllFilters() {
    this.selectedTypes = [];
    this.searchValue = undefined;
    if (this.searchInput) {
      this.searchInput.nativeElement.value = '';
    }
    this.page.pageNumber = 0;
    this.updateUrlState();
    this.loadApps();
  }

  get hasActiveFilters(): boolean {
    return this.selectedTypes.length > 0 || !!this.searchValue;
  }

  loadApps() {
    this.isLoading = true;
    const types = this.selectedTypes.length > 0 ? this.selectedTypes : undefined;
    const findApps = this.searchValue
      ? this.applicationService.search(this.domainId, '*' + this.searchValue + '*', types)
      : this.applicationService.findByDomain(this.domainId, this.page.pageNumber, this.page.size, types);

    findApps.subscribe(pagedApps => {
      this.page.totalElements = pagedApps.totalCount;
      this.applications = pagedApps.data;
      this.isLoading = false;
    });
  }

  setPage(pageInfo) {
    this.page.pageNumber = pageInfo.offset;
    this.loadApps();
  }

  get isEmpty() {
    return !this.isLoading && (!this.applications || (this.applications.length === 0 && !this.searchValue && this.selectedTypes.length === 0));
  }

  private updateUrlState() {
    const queryParams: any = {};
    if (this.selectedTypes.length > 0) {
      queryParams.type = this.selectedTypes;
    }
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams,
      queryParamsHandling: '',
      replaceUrl: true,
    });
  }
}
