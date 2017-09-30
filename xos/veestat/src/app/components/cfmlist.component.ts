
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


import {
  IMicrosemiDmList,
  IMicrosemiMaFormat, IMicrosemiMdFormat, IMicrosemiMepListElement, IMicrosemiStats,
  IMicrosemiStatsData
} from './stats-service';
import * as _ from 'lodash';

class CfmlistComponent {

  static $inject = [
    'MicrosemiStats'
  ];

  public mds: IMicrosemiMdFormat[];
  public mas: IMicrosemiMaFormat[];
  public meps: IMicrosemiMepListElement[];
  public dms: IMicrosemiStatsData[];

  public md: any;
  public ma: any;
  public mdValue: number;
  public maValue: number;
  public mepValue: number;
  //public dmValue: number;
  public dmValue = 1;

  //public displayButton = angular.isDefined(this.dmValue);
  public displayButton = true;

  constructor (
    private MicrosemiStats : IMicrosemiStats,
  ) {}

  $onInit() {
    this.MicrosemiStats.getMds().then((res) => {
      this.mds = res;
    });
  }

  public triggerRefresh(changed: string) {
    switch (changed) {
      case 'md':
        this.meps = [];
        this.maValue = null;
        this.mepValue = null;
        this.getMas(this.mdValue);
        break;
      case 'ma':
        this.mepValue = null;
        this.dmValue = null;
        this.getMeps(this.maValue);
        break;
      case 'mep':
        this.dmValue = null;
        this.getDms(this.mepValue);
        break;
      default:
        break;
    }

  }

  public getMas(mdNumericId: number) {
    this.md = _.find(this.mds, (md) => {
      return md.mdNumericId === Number(mdNumericId);
    });
    this.mas = this.md.maList;
  }

  public getMeps(maNumericId: number) {
    this.ma = _.find(this.mas, (ma) => {
      return ma.maNumericId === Number(maNumericId);
    });
    this.meps = this.ma['rmep-list'];
  }

  public getDms(mepNumericId: number) {
    this.MicrosemiStats.getDms(this.md.mdName, this.ma.maName, String(mepNumericId)).then((res: IMicrosemiStatsData[]) => {
      this.dms = res;
    });
  }

}

export const cfmlistComponent: angular.IComponentOptions = {
  template: require('./cfmlist.component.html'),
  controllerAs: 'vm',
  controller: CfmlistComponent
};
