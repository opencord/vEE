
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
import IHttpPromiseCallbackArg = angular.IHttpPromiseCallbackArg;

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
    dmId: any;
    current: IMicrosemiStatsFormat;
    historic: IMicrosemiStatsFormat[];
    [x: string]: any;
  };
  dmId?: any;
  current?: IMicrosemiStatsFormat;
  historic?: IMicrosemiStatsFormat[];
  [x: string]: any;

}

export interface IMicrosemiChartData {
  labels: string[];
  data: [number[]];
}

export interface IMicrosemiStats {
  getMds(): any;
  getMeps(mdName: string, maName: string): any;
  getDms(mdName: string, maName: string, mepId: string): any;
  get(mdName: string, maName: string, mepId: string, dmId: string): ng.IPromise<any>;
  getAllData(res: IMicrosemiStatsData): any;
}

export interface IMicrosemiMdData {
  mds?: [IMicrosemiMdFormat[]];
  md?: IMicrosemiMdFormat;
}

export interface IMicrosemiMdFormat {
  mdName: string;
  mdNameType: string;
  mdLevel: string;
  mdNumericId: number;
  maList: any[];
}

export interface IMicrosemiMaData {
  ma: IMicrosemiMaFormat[];
}

export interface IMicrosemiMaFormat {
  maName: string;
  maNameType: string;
  maNumericId: number;
  'ccm-interval': string;
  componentList: any[];
  'rmep-list': any[];
}

export interface IMicrosemiMepData {
  meps: [IMicrosemiMepFormat[]];
}

export interface IMicrosemiMepFormat {
  mepId: number;
  deviceId: string;
  port: string;
  direction: string;
  mdName: string;
  maName: string;
  'administrative-state': boolean;
  'cci-enabled': boolean;
  'ccm-ltm-priority': number;
  macAddress: string;
  loopback: any;
  remoteMeps: any[];
  activeMacStatusDefect: boolean;
  activeRdiCcmDefect: boolean;

}

export interface IMicrosemiDmList {
  dms: [IMicrosemiStatsData[]];
}

export class MicrosemiStats implements IMicrosemiStats {

  static $inject = [
    '$q',
    '$http',
    '$log',
    '$base64'
  ];

  private auth = this.$base64.encode('onos:rocks');
  private headers = {
    'Authorization': `Basic ${this.auth}`
  };

  constructor (
    private $q: ng.IQService,
    private $http: ng.IHttpService,
    private $log: ng.ILogService,
    private $base64: any
  ) {

  }

  public getMds() {
    const d = this.$q.defer();
    this.$http.get(`/vtn/onos/cfm/md/`, {'headers': this.headers})
      .then((res: IHttpPromiseCallbackArg<IMicrosemiMdData>) => {
        d.resolve(res.data.mds[0]);
      })
      .catch(err => {
        this.$log.error(`[CFMStatService] Error in GET md: `, err);
      });
    return d.promise;
  }

  public getMeps(mdName: string, maName: string) {
    const d = this.$q.defer();
    this.$http.get(`/vtn/onos/cfm/md/${mdName}/ma/${maName}/mep/`, {'headers': this.headers})
      .then((res: IHttpPromiseCallbackArg<IMicrosemiMepData>) => {
        d.resolve(res.data.meps[0]);
      })
      .catch(err => {
        this.$log.error(`[CFMStatService] Error in GET mep: `, err);
      });
    return d.promise;
  }

  public getDms(mdName : string, maName: string, mepId: string) {
    const d = this.$q.defer();
    this.$http.get(`/vtn/onos/cfm/md/${mdName}/ma/${maName}/mep/${mepId}/dm/`, {'headers': this.headers})
      .then((res: IHttpPromiseCallbackArg<IMicrosemiDmList>) => {
        d.resolve(res.data.dms[0]);
      })
      .catch(err => {
        this.$log.error(`[CFMStatService] Error in GET dm list: `, err);
      });
    return d.promise;
  }

  public get(mdName : string, maName: string, mepId: string, dmId: string) {
    const d = this.$q.defer();

    this.$http.get(`/vtn/onos/cfm/md/${mdName}/ma/${maName}/mep/${mepId}/dm/${dmId}`, {'headers': this.headers})
      .then((res) => {
        d.resolve(res);
      })
      .catch(err => {
        this.$log.error(`[CFMStatService] Error in GET: `, err);
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
    _.forEach(stats.dm.current.frameDelayTwoWayBins.bins, (bin) => {
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
