
/*
 * Copyright 2017-present Open Networking Foundation

 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at

 * http://www.apache.org/licenses/LICENSE-2.0

 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import * as _ from 'lodash';

export interface IMicrosemiStatsBinData {
  lowerLimit: string;
  count: number;
}

export interface IMicrosemiStatsBin {
  bins: IMicrosemiStatsBinData[];
}

export interface IMicrosemiStatsFormat {
  startTime?: string;
  finishTime?: string;
  interFrameDelayVariationTwoWayAvg: string;
  interFrameDelayVariationTwoWayMax: string;
  interFrameDelayVariationTwoWayMin: string;
  interFrameDelayVariationTwoWayBins: IMicrosemiStatsBin;
  frameDelayTwoWayAvg: string;
  frameDelayTwoWayMax: string;
  frameDelayTwoWayMin: string;
  frameDelayTwoWayBins: IMicrosemiStatsBin;
  [x: string]: any;
}

export interface IMicrosemiStatsData {
  dm: {
    current: IMicrosemiStatsFormat;
    historic: IMicrosemiStatsFormat[];
    [x: string]: any;
  };
}

export interface IMicrosemiChartData {
  labels: string[];
  data: [number[]];
}

export interface IMicrosemiStats {
  get(vlanId: string, deviceName: string, dmId: string): ng.IPromise<any>;
  getAllData(res: IMicrosemiStatsData): any;
}

export class MicrosemiStats implements IMicrosemiStats {

  static $inject = [
    '$q',
    '$http',
    '$log',
  ];

  constructor (
    private $q: ng.IQService,
    private $http: ng.IHttpService,
    private $log: ng.ILogService,
  ) {

  }

  public get(vlanId: string, deviceName: string, dmId: string) {
    const d = this.$q.defer();

    const mdName = 'Microsemi';
    const maName = `ma-vlan-${vlanId}`;
    const mepId = deviceName;

    // `/onos/onos-app-cfm/md/${mdName}/ma/${maName}/mep/${mepId}/dm/${dmId}`

    this.$http.get(`http://localhost:3003`)
      .then((res: IMicrosemiStatsData) => {
        d.resolve(res);
      })
      .catch(err => {
        this.$log.error(`[MicrosemiStats] Error in GET: `, err);
      });
    // comment as needed
    return d.promise;
  }

  public getAllData(res: IMicrosemiStatsData) {
    const data = res['data'];
    const line = this.parseDataForLine(data);
    const delay = this.parseDelayBin(data);
    const jitter = this.parseJitterBin(data);
    return {
      line,
      delay,
      jitter
    };
  }

  private getNumericVal(val: string): number {
    if (!val) {
      return 0;
    }
    val = val.replace('PT', '');
    val = val.replace('S', '');
    return parseFloat(val) * 1000;
  }

  private formatDate(string: string): string {
    const date = new Date(string);
    return `${date.getHours()}:${date.getMinutes()}:${date.getSeconds()}`;
  }

  private parseDelayBin(stats: IMicrosemiStatsData) {
    const labels: number[] = [];
    const data: number[] = [];
    _.forEach(stats.dm.current.FrameDelayRangeTwoWayBins.bins, (bin) => {
      labels.push(this.getNumericVal(bin.lowerLimit));
      data.push(bin.count);
    });
    return {
      labels,
      data
    };
  }

  private parseJitterBin(stats: IMicrosemiStatsData) {
    const labels: number[] = [];
    const data: number[] = [];
    _.forEach(stats.dm.current.interFrameDelayVariationTwoWayBins.bins, (bin) => {
      labels.push(this.getNumericVal(bin.lowerLimit));
      data.push(bin.count);
    });
    return {
      labels,
      data
    };
  }

  private parseDataForLine(data: IMicrosemiStatsData): IMicrosemiChartData {
    let stats = angular.copy(data.dm.historic);
    stats.unshift(data.dm.current);

    const jitterAvg: number[] = [];
    const jitterMax: number[] = [];
    const jitterMin: number[] = [];
    const delayAvg: number[] = [];
    const delayMax: number[] = [];
    const delayMin: number[] = [];
    const labels: string[] = [];

    _.forEach(stats, (s: IMicrosemiStatsFormat) => {
      const j_avg = this.getNumericVal(s.interFrameDelayVariationTwoWayAvg);
      const j_max = this.getNumericVal(s.interFrameDelayVariationTwoWayMax);
      const j_min = this.getNumericVal(s.interFrameDelayVariationTwoWayMin);
      const d_avg = this.getNumericVal(s.frameDelayTwoWayAvg);
      const d_max = this.getNumericVal(s.frameDelayTwoWayMax);
      const d_min = this.getNumericVal(s.frameDelayTwoWayMin);
      jitterAvg.push(j_avg);
      jitterMax.push(j_max);
      jitterMin.push(j_min);
      delayAvg.push(d_avg);
      delayMax.push(d_max);
      delayMin.push(d_min);
      labels.push(s.endTime ? this.formatDate(s.endTime) : 'current');
    });

    return {
      labels: labels.reverse(),
      data: [
        jitterAvg.reverse(),
        jitterMax.reverse(),
        jitterMin.reverse(),
        delayAvg.reverse(),
        delayMax.reverse(),
        delayMin.reverse(),
      ]
    };
  }

}
