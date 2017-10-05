
# Copyright 2017-present Open Networking Foundation
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.


from synchronizers.new_base.ansible_helper import run_template_ssh #if needed
from synchronizers.new_base.SyncInstanceUsingAnsible import SyncInstanceUsingAnsible
from synchronizers.new_base.syncstep import SyncStep
from synchronizers.new_base.modelaccessor import *

class SyncVEEService(SyncStep):
    provides=[VRouterTenant]
    observes = VEEService
    requested_interval=30
    playbook='sync_host.yaml'

    def get_vee_onos_service(self):
        return None

    def get_node_tag(self, node, tagname):
        return None

    def fetch_pending(self, deleted):

        return []

    def map_sync_inputs(self, vroutertenant):

        return []

    def map_sync_outputs(self, controller_image, res):
        pass
