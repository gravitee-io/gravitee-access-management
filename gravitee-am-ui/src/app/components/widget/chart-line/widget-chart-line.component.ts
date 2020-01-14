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
import {Component, Input, OnChanges, OnInit, SimpleChanges} from '@angular/core';
import * as Highcharts from 'highcharts';
import * as _ from 'lodash';

@Component({
  selector: 'gv-widget-chart-line',
  templateUrl: './widget-chart-line.component.html',
  styleUrls: ['./widget-chart-line.component.scss']
})
export class WidgetChartLineComponent implements OnInit, OnChanges {
  @Input('Highcharts') Highcharts: typeof Highcharts;
  @Input('chart') chart: any;
  chartOptions;
  constructor() { }

  ngOnInit() {
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes.chart.currentValue && changes.chart.currentValue.response) {
      const response = changes.chart.currentValue.response;
      const plotOptions = {
        series: {
          pointStart: response.timestamp.from,
          pointInterval: response.timestamp.interval,
          marker: {
            enabled: false
          }
        }
      };
      const xAxis = {
        type: 'datetime',
        dateTimeLabelFormats: { // don't display the dummy year
        month: '%e. %b',
          year: '%b'
        }
      };
      const series = _.map(response.values, value => {
        if (value.name.indexOf('failure') !== -1) {
          value['color'] = '#ED561B';
          value['legendIndex'] = 1;
        } else {
          value['zIndex'] = 1;
          value['legendIndex'] = 0;
        }
        return value;
      });
      this.chartOptions = {
        chart: { type: 'spline' },
        xAxis: xAxis,
        plotOptions : plotOptions,
        series : series
      };
    }
  }

}

