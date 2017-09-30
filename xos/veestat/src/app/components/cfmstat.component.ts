
/*
 * Copyright 2017-present Open Networking Foundation

 * Licensed under the Apache License, Version 2.0 (the 'License');
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at

 * http://www.apache.org/licenses/LICENSE-2.0

 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import {IMicrosemiStats, IMicrosemiStatsData} from './stats-service';
import './cfmstat.component.scss';

class CfmStatComponent {
  static $inject = [
    '$interval',
    '$stateParams',
    'MicrosemiStats'
  ];

  public options = {
    animation: {
      duration: 0
    },
    scales: {
      yAxes: [{
        ticks: {
          beginAtZero: true,
          fontColor: '#fff',
        },
        gridLines: {
          color: '#555',
          zeroLineColor: '#888'
        }
      }],
      xAxes: [{
        ticks: {
          beginAtZero: true,
          fontColor: '#fff',
        },
        gridLines: {
          color: '#555'
        }
      }]
    },

  };

  public lineOptions = {
    animation: {
      duration: 0
    },
    scales: {
      yAxes: [{
        ticks: {
          beginAtZero: true,
          callback: val => `${(Math.round(val * 1000) / 1000).toFixed(3)} ms`,
          fontColor: '#fff',
        },
        gridLines: {
          color: '#555',
          zeroLineColor: '#888'
        }
      }],
      xAxes: [{
        ticks: {
          beginAtZero: true,
          fontColor: '#fff',
        },
        gridLines: {
          color: '#555',
        }
      }]
    },
    bezierCurve: false
  };

  public lineChart = {
    series: ['Jitter Avg', 'Jitter Max', 'Jitter Min', 'Delay Avg', 'Delay Max', 'Delay Min'],
    labels: [],
    data: [],
    dataset: []
  };

  public delayBin = {
    series: ['Current Delay Bin'],
    labels: ['0 - 10 ms', '10 - 20 ms', '20 - 37 ms', '> 37 ms'],
    data: [],
    dataset: {
      backgroundColor: '#6f6db9'
    }
  };

  public jitterBin = {
    series: ['Current Jitter Bin'],
    labels: ['0 - 3 ms', '3 - 8 ms', '8 - 100 ms', '> 100 ms'],
    data: [],
    dataset: {
      backgroundColor: '#ffa500'
    }
  };

  public allLineColors = ['#c55d01', '#ffa500', '#8b0000', '#6f6db9', '#add8e6', '#00008b'];
  public lineColors = this.allLineColors;
  public lineFill = [
    {fill: false, borderColor: '#c55d01'},
    {fill: false, borderColor: '#ffa500'},
    {fill: false, borderColor: '#8b0000'},
    {fill: false, borderColor: '#6f6db9'},
    {fill: false, borderColor: '#add8e6'},
    {fill: false, borderColor: '#00008b'}];

  public graphOption = 'both';
  public graphTitle = 'Delay and Jitter Graph';
  public linedata = [];

  constructor(
    private $interval: ng.IIntervalService,
    private $stateParams: ng.ui.IStateParamsService,
    private MicrosemiStats: IMicrosemiStats,
  ) {

  }

  $onInit() {
    const load = () => {
      this.MicrosemiStats.get(this.$stateParams.mdName, this.$stateParams.maName, this.$stateParams.mepId, this.$stateParams.dmId || 1)
        .then((res: IMicrosemiStatsData) => {
          const data = this.MicrosemiStats.getAllData(res);

          this.linedata = data.line.data;
          this.lineChart.labels = data.line.labels;
          this.lineChart.data = data.line.data;
          this.delayBin.data = data.delay.data;
          this.jitterBin.data = data.jitter.data;

        });
    };

    load();

    this.renderGraph();

    this.$interval(() => {
      load();
    }, 1000 * 60);
  }

  renderGraph() : void {
    this.graphTitle = this.graphTitleRender();
    this.graphDisplayRender();
  }

  graphTitleRender() : string {
    let output;
    switch (this.graphOption) {
      default:
      case 'both':
        output = 'Delay and Jitter Graph';
        break;
      case 'delay':
        output = 'Delay Graph';
        break;
      case 'jitter':
        output = 'Jitter Graph';
        break;
    }
    return output;
  }

  graphDisplayRender() : void {
    switch (this.graphOption) {

      default:
      case 'both':
        this.lineChart.series = ['Jitter Avg', 'Jitter Max', 'Jitter Min', 'Delay Avg', 'Delay Max', 'Delay Min'];
        this.lineChart.data = this.linedata;
        this.lineChart.dataset = this.lineFill;
        this.lineColors = this.allLineColors;
        break;

      case 'delay':
        this.lineChart.series = ['Delay Avg', 'Delay Max', 'Delay Min'];
        this.lineChart.data = this.linedata.slice(3);
        this.lineChart.dataset = this.lineFill.slice(3);
        this.lineColors = this.allLineColors.slice(3);
        break;

      case 'jitter':
        this.lineChart.series = ['Jitter Avg', 'Jitter Max', 'Jitter Min'];
        this.lineChart.data = this.linedata.slice(0, 3);
        this.lineChart.dataset = this.lineFill.slice(0, 3);
        this.lineColors = this.allLineColors.slice(0, 3);
        break;
    }
  }

}


export const cfmStatComponent: angular.IComponentOptions = {
  template: require('./cfmstat.component.html'),
  controllerAs: 'vm',
  controller: CfmStatComponent
};
