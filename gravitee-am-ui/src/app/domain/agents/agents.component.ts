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

import { AGENT_APPLICATION_TYPES, ApplicationService } from '../../services/application.service';
import { SnackbarService } from '../../services/snackbar.service';
import { CursorResponse, NgxTablePageInfo } from '../../utils/cursor';

@Component({
  selector: 'app-agents',
  templateUrl: './agents.component.html',
  styleUrls: ['./agents.component.scss'],
  standalone: false,
})
export class AgentsComponent implements OnInit, OnDestroy {
  private searchSubject = new Subject<string>();
  private loadSubject = new Subject<{ cursor?: string }>();
  private searchSubscription: Subscription;
  private loadSubscription: Subscription;
  agents: any[];
  private searchValue: string;
  domainId: string;
  loadingAgents = false;
  page = {
    totalElements: 0,
    pageNumber: 0,
    size: 10,
  };
  nextCursor: string;

  sorts = [{ prop: 'updatedAt', dir: 'desc' }];

  constructor(
    private applicationService: ApplicationService,
    private snackbarService: SnackbarService,
    private route: ActivatedRoute,
  ) {}

  ngOnInit() {
    this.domainId = this.route.snapshot.data['domain']?.id;
    this.loadSubscription = this.loadSubject
      .pipe(
        switchMap(({ cursor }) => {
          this.loadingAgents = true;
          return this.getAgentsRequest(cursor).pipe(finalize(() => (this.loadingAgents = false)));
        }),
      )
      .subscribe({
        next: (pagedAgents) => this.applyPage(pagedAgents),
        error: (err: unknown) => {
          const message = (err as { error?: { message?: string } })?.error?.message ?? 'Unable to load agents';
          this.snackbarService.open(message);
        },
      });

    this.searchSubscription = this.searchSubject.pipe(debounceTime(400), distinctUntilChanged()).subscribe((value) => {
      this.page.pageNumber = 0;
      this.nextCursor = undefined;
      this.searchValue = value;
      this.loadAgents();
    });

    this.loadAgents();
  }

  ngOnDestroy() {
    this.searchSubscription?.unsubscribe();
    this.loadSubscription?.unsubscribe();
  }

  onSearch(event) {
    this.searchSubject.next(event?.target?.value);
  }

  loadAgents() {
    this.loadSubject.next({});
  }

  loadAgentsWithCursor(cursor: string) {
    this.loadSubject.next({ cursor });
  }

  setPage(pageInfo: NgxTablePageInfo) {
    if (this.page.pageNumber + 1 === pageInfo.offset && this.nextCursor) {
      this.page.pageNumber = pageInfo.offset;
      this.loadAgentsWithCursor(this.nextCursor);
    } else {
      this.page.pageNumber = pageInfo.offset;
      this.loadAgents();
    }
  }

  get isEmpty() {
    return !this.agents || (this.agents.length === 0 && !this.searchValue);
  }

  get showTable() {
    return !this.isEmpty || this.loadingAgents;
  }

  setSort($event: any) {
    const sort = $event?.sorts?.[0];
    if (!sort) {
      return;
    }
    this.sorts = [sort];
    this.page.pageNumber = 0;
    this.nextCursor = undefined;
    this.loadAgents();
  }

  private getAgentsRequest(cursor?: string) {
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
      AGENT_APPLICATION_TYPES,
    );
  }

  private applyPage(pagedAgents: CursorResponse) {
    this.page.totalElements = pagedAgents.totalCount;
    this.agents = pagedAgents.data;
    this.nextCursor = pagedAgents.nextCursor;
    this.page.pageNumber = pagedAgents.page;
  }
}
