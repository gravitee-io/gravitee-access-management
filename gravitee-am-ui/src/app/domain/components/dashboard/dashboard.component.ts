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
import {Component, Input, OnInit} from '@angular/core';
import {forkJoin, Observable} from "rxjs";
import {map} from "rxjs/operators";
import {AnalyticsService} from '../../../services/analytics.service';
import * as Highcharts from 'highcharts';
import moment from 'moment';
import * as _ from 'lodash';
import {Widget} from '../../../components/widget/widget.model';
import {isNil} from 'lodash';
import { availableTimeRanges, defaultTimeRangeId } from '../../../utils/time-range-utils';

export interface DashboardData {
  widgets: Widget[]
}

Highcharts.setOptions({
  credits: {
    enabled: false
  },
  title: {
    text: null
  },
  colors: ['#058DC7', '#50B432', '#ED561B', '#DDDF00', '#24CBE5', '#64E572', '#FF9655', '#FFF263', '#6AF9C4']
});

@Component({
  selector: 'gv-dashboard',
  templateUrl: './dashboard.component.html',
  styleUrls: ['./dashboard.component.scss']
})
export class DashboardComponent implements OnInit {
  @Input('dashboard') dashboard: DashboardData;
  widgets: Widget[];
  Highcharts: typeof Highcharts = Highcharts;

  @Input()
  domainId: string;

  @Input()
  applicationId?: string;

  isLoading: boolean;

  readonly timeRanges = availableTimeRanges;
  selectedTimeRange = defaultTimeRangeId;

  constructor(private analyticsService: AnalyticsService) { }

  ngOnInit() {
    this.fetch();
  }

  search() {
    this.fetch();
  }

  private query(widget): Observable<Widget> {
    const selectedTimeRange = _.find(this.timeRanges, { id : this.selectedTimeRange });

    const analyticsQuery = {
      type: widget.chart.request.type,
      field: widget.chart.request.field,
      from: moment().subtract(selectedTimeRange.value, selectedTimeRange.unit).valueOf(),
      to: moment().valueOf(),
      interval: selectedTimeRange.interval,
      size: widget.chart.request.size
    }

    const result$ =
      isNil(this.applicationId)
        ? this.analyticsService
          .search(this.domainId, analyticsQuery)
        : this.analyticsService
          .searchApplicationAnalytics(this.domainId, this.applicationId, analyticsQuery)


    return result$.pipe(map(response => {
      widget.chart.response = response
      return widget;
    }));
  }

  private fetch() {
    const dashboard: DashboardData = Object.assign({}, this.dashboard);
    this.widgets = [];
    this.isLoading = true;
    forkJoin(_.map(dashboard.widgets, widget => this.query(widget)))
      .subscribe(widgets => {
        this.widgets = [...widgets];
        setTimeout(() => {
          this.isLoading = false;
        }, 1500);
      });
  }
}
