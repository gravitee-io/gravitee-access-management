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
import { ActivatedRoute } from '@angular/router';

import { AppConfig } from '../../../config/app.config';
import { DomainService } from '../../services/domain.service';
import { EnvironmentService } from '../../services/environment.service';
import { UserPreferencesService } from '../../services/user-preferences.service';

@Component({
  selector: 'app-domains',
  templateUrl: './domains.component.html',
  styleUrls: ['./domains.component.scss'],
  standalone: false,
})
export class DomainsComponent implements OnInit {
  private searchValue: string;
  page: any = {};
  title = AppConfig.settings.portalTitle;
  version = AppConfig.settings.version;
  domains = [];
  showPinnedOnly = false;

  constructor(
    private route: ActivatedRoute,
    private domainService: DomainService,
    private environmentService: EnvironmentService,
    private userPreferencesService: UserPreferencesService,
  ) {
    this.page.pageNumber = 0;
    this.page.size = 10;
  }

  ngOnInit() {
    const pagedDomains = this.route.snapshot.data['domains'];
    this.domains = pagedDomains.data;
    this.page.totalElements = pagedDomains.totalCount;
  }

  onSearch(event) {
    this.page.pageNumber = 0;
    this.searchValue = event.target.value;
    this.loadDomains();
  }

  onPinnedOnlyChange() {
    this.page.pageNumber = 0;
    this.loadDomains();
  }

  togglePin(domain: any, event: Event) {
    event.stopPropagation();
    this.userPreferencesService.togglePin(domain.id).subscribe(() => {
      if (this.showPinnedOnly) {
        this.loadDomains();
      }
    });
  }

  toggleDefault(domain: any, event: Event) {
    event.stopPropagation();
    this.userPreferencesService.toggleDefaultDomain(domain.id, this.environmentService.getCurrentEnvironment().id).subscribe();
  }

  isPinned(domainId: string): boolean {
    return this.userPreferencesService.isPinned(domainId);
  }

  isDefault(domainId: string): boolean {
    return this.userPreferencesService.isDefault(domainId);
  }

  loadDomains() {
    if (this.showPinnedOnly) {
      this.loadPinnedDomains();
      return;
    }

    const findDomains = this.searchValue
      ? this.domainService.search('*' + this.searchValue + '*', this.page.pageNumber, this.page.size)
      : this.domainService.findByEnvironment(this.page.pageNumber, this.page.size);

    findDomains.subscribe((pagedDomains) => {
      this.page.totalElements = pagedDomains.totalCount;
      this.domains = pagedDomains.data;
    });
  }

  private loadPinnedDomains() {
    const pinnedIds = this.userPreferencesService.pinnedDomainIds();
    if (pinnedIds.length === 0) {
      this.domains = [];
      this.page.totalElements = 0;
      return;
    }
    this.domainService.findByIds(pinnedIds).subscribe((pagedDomains) => {
      const domains = this.searchValue
        ? pagedDomains.data.filter((domain) => domain.name.toLowerCase().includes(this.searchValue.toLowerCase()))
        : pagedDomains.data;
      this.page.totalElements = domains.length;
      const start = this.page.pageNumber * this.page.size;
      this.domains = domains.slice(start, start + this.page.size);
    });
  }

  setPage(pageInfo) {
    this.page.pageNumber = pageInfo.offset;
    this.loadDomains();
  }

  get isEmpty() {
    return !this.domains || (this.domains.length === 0 && !this.searchValue && !this.showPinnedOnly);
  }
}
