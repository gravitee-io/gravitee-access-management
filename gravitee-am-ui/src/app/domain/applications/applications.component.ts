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
import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Subject, Subscription } from 'rxjs';
import { debounceTime, distinctUntilChanged, finalize, switchMap } from 'rxjs/operators';

import { ApplicationService, NON_AGENT_APPLICATION_TYPES } from '../../services/application.service';
import { CursorResponse, NgxTablePageInfo } from '../../utils/cursor';

@Component({
  selector: 'app-applications',
  templateUrl: './applications.component.html',
  styleUrls: ['./applications.component.scss'],
  standalone: false,
})
export class ApplicationsComponent implements OnInit, OnDestroy {
  private searchSubject = new Subject<string>();
  private loadSubject = new Subject<{ cursor?: string }>();
  private searchSubscription: Subscription;
  private loadSubscription: Subscription;
  applications: any[];
  private searchValue: string;
  domainId: string;
  loadingApplications = false;
  page = {
    totalElements: 0,
    pageNumber: 0,
    size: 10,
  };
  nextCursor: string;

  sorts = [{ prop: 'updatedAt', dir: 'desc' }];

  constructor(
    private applicationService: ApplicationService,
    private route: ActivatedRoute,
  ) {
    this.page.pageNumber = 0;
    this.page.size = 10;
  }

  ngOnInit() {
    this.domainId = this.route.snapshot.data['domain']?.id;
    this.loadSubscription = this.loadSubject
      .pipe(
        switchMap(({ cursor }) => {
          this.loadingApplications = true;
          return this.getAppsRequest(cursor).pipe(finalize(() => (this.loadingApplications = false)));
        }),
      )
      .subscribe((pagedApps) => this.applyPage(pagedApps));

    this.searchSubscription = this.searchSubject.pipe(debounceTime(400), distinctUntilChanged()).subscribe((value) => {
      this.page.pageNumber = 0;
      this.nextCursor = undefined;
      this.searchValue = value;
      this.loadApps();
    });

    this.loadApps();
  }

  ngOnDestroy() {
    this.searchSubscription?.unsubscribe();
    this.loadSubscription?.unsubscribe();
  }

  onSearch(event) {
    this.searchSubject.next(event?.target?.value);
  }

  loadApps() {
    this.loadSubject.next({});
  }

  loadAppsWithCursor(cursor: string) {
    this.loadSubject.next({ cursor });
  }

  setPage(pageInfo: NgxTablePageInfo) {
    if (this.page.pageNumber + 1 === pageInfo.offset && this.nextCursor) {
      this.page.pageNumber = pageInfo.offset;
      this.loadAppsWithCursor(this.nextCursor);
    } else {
      this.page.pageNumber = pageInfo.offset;
      this.loadApps();
    }
  }

  get isEmpty() {
    return !this.applications || (this.applications.length === 0 && !this.searchValue);
  }

  get showTable() {
    return !this.isEmpty || this.loadingApplications;
  }

  setSort($event: any) {
    const sort = $event?.sorts?.[0];
    if (!sort) {
      return;
    }
    this.sorts = [sort];
    this.page.pageNumber = 0;
    this.nextCursor = undefined;
    this.loadApps();
  }

  private getAppsRequest(cursor?: string) {
    if (cursor) {
      return this.applicationService.cursorNext(cursor);
    }

    const query = this.searchValue ? '*' + this.searchValue + '*' : undefined;
    return this.applicationService.cursorSearch(
      this.domainId,
      this.page.size,
      this.page.pageNumber,
      this.sorts[0],
      query,
      NON_AGENT_APPLICATION_TYPES,
    );
  }

  private applyPage(pagedApps: CursorResponse) {
    this.page.totalElements = pagedApps.totalCount;
    this.applications = pagedApps.data;
    this.nextCursor = pagedApps.nextCursor;
    this.page.pageNumber = pagedApps.page;
  }
}
