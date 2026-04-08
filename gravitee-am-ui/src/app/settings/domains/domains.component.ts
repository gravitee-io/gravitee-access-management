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

import { AppConfig } from '../../../config/app.config';
import { DomainService } from '../../services/domain.service';

@Component({
  selector: 'app-domains',
  templateUrl: './domains.component.html',
  styleUrls: ['./domains.component.scss'],
  standalone: false,
})
export class DomainsComponent implements OnInit {
  private searchValue: string;
  title = AppConfig.settings.portalTitle;
  version = AppConfig.settings.version;
  domains = [];

  pageSize = 10;
  totalCount = 0;
  hasNext = false;
  hasPrevious = false;
  private nextCursor: string | null = null;
  private cursorStack: string[] = [];
  private currentSort: string = 'name';

  constructor(private domainService: DomainService) {}

  ngOnInit() {
    this.loadDomains();
  }

  onSearch(event) {
    this.searchValue = event.target.value;
    this.resetPagination();
    this.loadDomains();
  }

  loadDomains() {
    const currentCursor = this.cursorStack.length > 0 ? this.cursorStack[this.cursorStack.length - 1] : undefined;

    const findDomains = this.searchValue
      ? this.domainService.searchCursor('*' + this.searchValue + '*', this.pageSize, currentCursor, this.currentSort)
      : this.domainService.findByEnvironmentCursor(this.pageSize, currentCursor, this.currentSort);

    findDomains.subscribe({
      next: (cursorPage) => {
        this.domains = cursorPage.data;
        this.nextCursor = cursorPage.nextCursor;
        this.hasNext = cursorPage.hasNext;
        this.hasPrevious = this.cursorStack.length > 0;
        if (cursorPage.totalCount !== undefined) {
          this.totalCount = cursorPage.totalCount;
        }
      },
      error: (err: unknown) => {
        console.error('Failed to load domains:', err);
      },
    });
  }

  onSort(event) {
    const sortField = event.sorts[0];
    const prop = sortField.prop === 'name' ? 'name' : 'updatedAt';
    this.currentSort = sortField.dir === 'desc' ? '-' + prop : prop;
    this.resetPagination();
    this.loadDomains();
  }

  nextPage() {
    if (this.nextCursor) {
      this.cursorStack.push(this.nextCursor);
      this.loadDomains();
    }
  }

  previousPage() {
    if (this.cursorStack.length > 0) {
      this.cursorStack.pop();
      this.loadDomains();
    }
  }

  private resetPagination() {
    this.cursorStack = [];
    this.nextCursor = null;
    this.hasNext = false;
    this.hasPrevious = false;
  }

  get isEmpty() {
    return !this.domains || (this.domains.length === 0 && !this.searchValue);
  }
}
