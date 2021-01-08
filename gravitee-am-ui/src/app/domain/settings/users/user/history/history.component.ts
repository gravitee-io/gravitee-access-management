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
import { Component, OnInit, ViewChild } from '@angular/core';
import moment from 'moment';
import { AuditService } from '../../../../../services/audit.service';
import { Observable } from 'rxjs';
import { OrganizationService } from '../../../../../services/organization.service';
import { DatatableComponent } from '@swimlane/ngx-datatable';
import { availableTimeRanges, defaultTimeRangeId } from '../../../../../utils/time-range-utils';
import { find } from 'lodash';
import { ActivatedRoute } from '@angular/router';

@Component({
  selector: 'app-history',
  templateUrl: './history.component.html',
  styleUrls: ['./history.component.scss'],
})
export class UserHistoryComponent implements OnInit {
  @ViewChild('auditsTable', { static: true })
  table: DatatableComponent;

  eventType: string;
  eventTypes$: Observable<string[]>;
  eventStatus: string;
  startDate: string;
  endDate: string;
  displayReset = false;
  selectedTimeRange = defaultTimeRangeId;
  readonly timeRanges = availableTimeRanges;
  page = { pageNumber: 0, size: 10, totalElements: 0 };
  audits: any[];
  loadingIndicator: boolean;
  requiredReadPermission = 'domain_audit_read';
  config = { lineWrapping: true, lineNumbers: true, readOnly: true, mode: 'application/json' };
  user: any;

  private startDateChanged = false;
  private endDateChanged = false;
  private domainId: string;

  constructor(private readonly auditService: AuditService,
              private readonly organizationService: OrganizationService,
              private readonly route: ActivatedRoute) {
  }

  ngOnInit() {
    this.domainId = this.route.snapshot.parent.parent.parent.params['domainId'];
    this.user = this.route.snapshot.parent.data['user'];

    this.eventTypes$ = this.organizationService.auditEventTypes();
    this.search();
  }

  search() {
    this.page.pageNumber = 0;
    this.searchAudits();
  }

  updateForm() {
    this.displayReset = true;
  }

  startDateChange() {
    this.startDateChanged = true;
    this.updateForm();
  }

  endDateChange() {
    this.endDateChanged = true;
    this.updateForm();
  }

  resetForm() {
    this.page.pageNumber = 0;
    this.eventType = null;
    this.eventStatus = null;
    this.startDateChanged = false;
    this.startDate = null;
    this.endDate = null;
    this.endDateChanged = false;
    this.displayReset = false;
    this.searchAudits();
  }

  refresh() {
    this.searchAudits();
  }
  setPage(pageInfo: { offset: number }) {
    this.page.pageNumber = pageInfo.offset;
    this.searchAudits();
  }

  toggleExpandRow(row: any) {
    this.table.rowDetail.toggleExpandRow(row);
  }

  auditDetails(row) {
    if (row.outcome.message) {
      if (row.outcome.status === 'SUCCESS') {
        return JSON.stringify(JSON.parse(row.outcome.message), null, '  ');
      } else {
        return row.outcome.message;
      }
    } else {
      return row.type + ' success';
    }
  }

  private searchAudits() {
    const selectedTimeRange = find(this.timeRanges, { id: this.selectedTimeRange });
    const from = this.startDateChanged ? moment(this.startDate).valueOf() : moment().subtract(selectedTimeRange.value, selectedTimeRange.unit).valueOf();
    const to = this.endDateChanged ? moment(this.endDate).valueOf() : moment().valueOf();
    this.loadingIndicator = true;
    const organizationContext = false;
    this.auditService.search(this.domainId, this.page.pageNumber, this.page.size, this.eventType, this.eventStatus, this.user.username, from, to, organizationContext).subscribe(pagedAudits => {
      this.page.totalElements = pagedAudits.totalCount;
      this.audits = pagedAudits.data;
      this.loadingIndicator = false;
    });
  }

  displayClientName() {
    return this.user.applicationEntity != null ? this.user.applicationEntity.name : this.user.client;
  }

  accountLocked() {
    return !this.user.accountNonLocked && this.user.accountLockedUntil > new Date();
  }
}
