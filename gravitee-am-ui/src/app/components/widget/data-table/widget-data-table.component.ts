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
import { Component, Input, OnChanges, OnInit, SimpleChanges } from '@angular/core';
import { Router } from '@angular/router';
import * as _ from 'lodash';

@Component({
  selector: 'gv-widget-data-table',
  templateUrl: './widget-data-table.component.html',
  styleUrls: ['./widget-data-table.component.scss'],
})
export class WidgetDataTableComponent implements OnInit, OnChanges {
  @Input('chart') chart: any;
  @Input('domainId') domainId: string;
  rows = [];
  loadingIndicator = true;
  reorderable = true;
  columns = [];
  sorts = [];
  limit = 5;

  constructor(private router: Router) {}

  ngOnInit() {
    this.columns = _.map(this.chart.columns, (column) => ({ name: column }));
    this.limit = this.chart.paging;
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes.chart.currentValue && changes.chart.currentValue.response) {
      const response = changes.chart.currentValue.response.values;
      const metadata = changes.chart.currentValue.response.metadata;
      const field = this.chart.request.field;
      const columnName = this.chart.columns[0];
      const columnValue = this.chart.columns[1];
      this.sorts = [{ prop: columnValue, dir: 'desc' }];
      this.rows = Object.keys(response).map((key) => {
        const obj = {};
        obj[columnName] = this.getColumnName(key, metadata);
        obj[columnValue] = response[key];
        obj.name = obj[columnName];
        obj.value = obj[columnValue];
        obj.deleted = this.resourceDeleted(key, metadata);
        obj.link = this.getResourceLink(key, metadata, field);
        return obj;
      });
      setTimeout(() => {
        this.loadingIndicator = false;
      });
    }
  }

  goTo(routerLink) {
    // needed to trick reuse route strategy, skipLocationChange to avoid /dummy to go into history
    this.router.navigateByUrl('/dummy', { skipLocationChange: true }).then(() => this.router.navigate(routerLink));
  }

  private getColumnName(key, metadata): string {
    if (!metadata && !metadata[key]) {
      return key;
    } else {
      return metadata[key].name;
    }
  }

  private getResourceLink(key, metadata, field) {
    if ('application' === field) {
      return ['/domains', metadata[key].domain, 'applications', key];
    }
    return ['.'];
  }

  private resourceDeleted(key, metadata) {
    if (!metadata || !metadata[key]) {
      return true;
    }
    return metadata[key].deleted;
  }
}
