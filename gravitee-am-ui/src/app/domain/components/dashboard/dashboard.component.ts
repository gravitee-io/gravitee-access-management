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
import {ActivatedRoute} from '@angular/router';
import {forkJoin, Observable} from "rxjs";
import {map} from "rxjs/operators";
import {AnalyticsService} from '../../../services/analytics.service';
import * as Highcharts from 'highcharts';
import * as moment from 'moment';
import * as _ from 'lodash';

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
  @Input('dashboard') dashboard: any;
  widgets: any[];
  Highcharts: typeof Highcharts = Highcharts;
  domainId: string;
  selectedTimeRange = '1d';
  isLoading: boolean;
  timeRanges: any[] = [
    {
      'id': '1h',
      'name': 'Last hour',
      'value': 1,
      'unit': 'hours',
      'interval' : 1000 * 60
    },
    {
      'id': '12h',
      'name': 'Last 12 hours',
      'value': 12,
      'unit': 'hours',
      'interval' : 1000 * 60 * 60
    },
    {
      'id': '1d',
      'name': 'Today',
      'value': 1,
      'unit': 'days',
      'interval' : 1000 * 60 * 60
    },
    {
      'id': '7d',
      'name': 'This week',
      'value': 1,
      'unit': 'weeks',
      'interval' : 1000 * 60 * 60 * 24
    },
    {
      'id': '30d',
      'name': 'This month',
      'value': 1,
      'unit': 'months',
      'interval' : 1000 * 60 * 60 * 24
    },
    {
      'id': '90d',
      'name': 'Last 90 days',
      'value': 3,
      'unit': 'months',
      'interval' : 1000 * 60 * 60 * 24
    }
  ];

  constructor(private analyticsService: AnalyticsService,
              private route: ActivatedRoute) { }

  ngOnInit() {
    this.domainId = this.route.snapshot.params['domainId'];
    this.fetch();
  }

  search() {
    this.fetch();
  }

  private query(widget): Observable<any> {
    const selectedTimeRange = _.find(this.timeRanges, { id : this.selectedTimeRange });
    const from = moment().subtract(selectedTimeRange.value, selectedTimeRange.unit);
    const to = moment().valueOf();
    const interval = selectedTimeRange.interval;
    return this.analyticsService
      .search(this.domainId, widget.chart.request.type, widget.chart.request.field, interval, from, to, widget.chart.request.size)
      .pipe(map(response => {
        widget.chart.response = response
        return widget;
      }));
  }

  private fetch() {
    const dashboard = Object.assign({}, this.dashboard);
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
