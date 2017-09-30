
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
  interFrameDelayVariationTwoWayAvg: string;
  interFrameDelayVariationTwoWayMax: string;
  interFrameDelayVariationTwoWayMin: string;
  interFrameDelayVariationTwoWayBins: IMicrosemiStatsBin;
  FrameDelayTwoWayAvg: string;
  FrameDelayTwoWayMax: string;
  FrameDelayTwoWayMin: string;
  FrameDelayTwoWayBins: IMicrosemiStatsBin;
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
    '$base64'
  ];

  constructor (
    private $q: ng.IQService,
    private $http: ng.IHttpService,
    private $log: ng.ILogService,
    private $base64 :any
  ) {

  }

  public get(vlanId: string, mepId: string, dmId: string) {
    const d = this.$q.defer();

    const mdName = 'Microsemi';
    const maName = `ma-vlan-${vlanId}`;

    const auth = this.$base64.encode('onos:rocks');
    const headers = {
      'Authorization': `Basic ${auth}`,
      'Access-Control-Allow-Origin': '*'
    };

    // `/onos/cfm/md/${mdName}/ma/${maName}/mep/${mepId}/dm/${dmId}`
    this.$http.get(`/vtn/onos/cfm/md/Microsemi/ma/ma-vlan-1/mep/10/dm/1`, {'headers': headers})
      .then((res: IMicrosemiStatsData) => {
        d.resolve(res);
      })
      .catch(err => {
        this.$log.error(`[MicrosemiStats] Error in GET: `, err);
      });
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
      const d_avg = this.getNumericVal(s.FrameDelayRangeTwoWayAvg);
      const d_max = this.getNumericVal(s.FrameDelayRangeTwoWayMax);
      const d_min = this.getNumericVal(s.FrameDelayRangeTwoWayMin);
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
