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
import { Component, Input, OnChanges, SimpleChanges } from '@angular/core';
import * as Highcharts from 'highcharts';

import { Chart } from '../widget.model';

@Component({
  selector: 'gv-widget-chart-gauge',
  templateUrl: './widget-chart-gauge.component.html',
  styleUrls: ['./widget-chart-gauge.component.scss'],
})
export class WidgetChartGaugeComponent implements OnChanges {
  @Input() Highcharts: typeof Highcharts;
  @Input() chart: Chart;
  chartOptions: Highcharts.Options;

  ngOnChanges(changes: SimpleChanges): void {
    if (changes.chart.currentValue?.response) {
      const response = changes.chart.currentValue.response;
      const max = response.values['total'];
      const current = Object.keys(response.values)
        .filter((key) => key !== 'total')
        .map((key) => response.values[key])[0];
      const title = Object.keys(response.values).filter((key) => key !== 'total')[0];
      const percentage = (current / max) * 100;
      const chartTitle =
        max === undefined || max === 0
          ? `<div style="text-align: center;"><p>No data to display</p></div>`
          : `<div style="text-align: center;">
          <p>${title}</p>
          <p>${percentage.toFixed(1)}% <small style="font-size: 65%">(${current}/${max})</small></p>
         </div>`;
      this.chartOptions = {
        title: {
          useHTML: true,
          text: chartTitle,
          align: 'center',
          verticalAlign: 'middle',
        },
        tooltip: {
          enabled: false,
        },
        yAxis: {
          min: 0,
          max: max,
          lineWidth: 0,
          tickPositions: [],
        },
        plotOptions: {
          pie: {
            dataLabels: {
              enabled: false,
            },
            startAngle: 0,
            endAngle: 360,
            center: ['50%', '50%'],
            colors: ['#4CAF50', '#F2F2F2'],
            linecap: 'round',
          },
        },
        series: [
          {
            type: 'pie',
            innerSize: '85%',
            data: [
              ['', current],
              ['', max - current],
            ],
          },
        ],
      };
    }
  }
}
