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

  pageSize = 10;
  totalCount = 0;
  hasNext = false;
  hasPrevious = false;
  private nextCursor: string | null = null;
  private cursorStack: string[] = [];
  private currentSort: string = '-updatedAt';

  constructor(
    private dialogService: DialogService,
    private snackbarService: SnackbarService,
    private applicationService: ApplicationService,
    private route: ActivatedRoute,
  ) {}

  ngOnInit() {
    this.domainId = this.route.snapshot.data['domain']?.id;
    this.loadApps();
  }

  onSearch(event) {
    this.searchValue = event.target.value;
    this.resetPagination();
    this.loadApps();
  }

  loadApps() {
    const currentCursor = this.cursorStack.length > 0 ? this.cursorStack[this.cursorStack.length - 1] : undefined;

    const findApps = this.searchValue
      ? this.applicationService.searchCursor(this.domainId, '*' + this.searchValue + '*', this.pageSize, currentCursor, this.currentSort)
      : this.applicationService.findByDomainCursor(this.domainId, this.pageSize, currentCursor, this.currentSort);

    findApps.subscribe((cursorPage) => {
      this.applications = cursorPage.data;
      this.nextCursor = cursorPage.nextCursor;
      this.hasNext = cursorPage.hasNext;
      this.hasPrevious = this.cursorStack.length > 0;
      if (cursorPage.totalCount !== undefined) {
        this.totalCount = cursorPage.totalCount;
      }
    });
  }

  onSort(event) {
    const sortField = event.sorts[0];
    const prop = sortField.prop === 'name' ? 'name' : 'updatedAt';
    this.currentSort = sortField.dir === 'desc' ? '-' + prop : prop;
    this.resetPagination();
    this.loadApps();
  }

  nextPage() {
    if (this.nextCursor) {
      this.cursorStack.push(this.nextCursor);
      this.loadApps();
    }
  }

  previousPage() {
    if (this.cursorStack.length > 0) {
      this.cursorStack.pop();
      this.loadApps();
    }
  }

  private resetPagination() {
    this.cursorStack = [];
    this.nextCursor = null;
    this.hasNext = false;
    this.hasPrevious = false;
  }

  get isEmpty() {
    return !this.applications || (this.applications.length === 0 && !this.searchValue);
  }
}
