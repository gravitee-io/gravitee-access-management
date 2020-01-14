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

@Component({
  selector: 'gv-widget-chart-pie',
  templateUrl: './widget-chart-pie.component.html',
  styleUrls: ['./widget-chart-pie.component.scss']
})
export class WidgetChartPieComponent implements OnInit, OnChanges {
  @Input('Highcharts') Highcharts: typeof Highcharts;
  @Input('chart') chart: any;
  chartOptions: Highcharts.Options;

  constructor() { }

  ngOnInit() {
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes.chart.currentValue && changes.chart.currentValue.response) {
      let title = {};
      const response = changes.chart.currentValue.response;
      const series = [];
      const series0 = {type: 'pie', name: '', colorByPoint: true, data: []};
      series.push(series0);
      if (response.values && Object.keys(response.values).length > 0) {
        series0['data'] = Object.keys(response.values).map(key => ([key, response.values[key]]));
      } else {
        // set empty message
        const chartTitle =  `<div style="text-align: center;"><p>No data to display</p></div>`;
        title = {
          useHTML: true,
          text: chartTitle,
          align: 'center',
          verticalAlign: 'middle'
        };
      }
      const plotOptions = {
        pie: {
          allowPointSelect: true,
          cursor: 'pointer',
          dataLabels: {
            enabled: true,
            format: '<b>{point.name}</b>: {point.percentage:.1f} %'
          }
        }
      };
      this.chartOptions = {
        title : title,
        chart: {
          plotBackgroundColor: null,
          plotBorderWidth: null,
          plotShadow: false,
          type: 'pie'
        },
        plotOptions : plotOptions,
        series : series
      };
    }
  }
}

