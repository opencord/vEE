
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


from core.models import User, ServiceInstanceLink
from services.vee.models import VEEServiceInstance

from xosresource import XOSResource
from serviceinstance import XOSServiceInstance

class XOSVEEServiceInstance(XOSServiceInstance):
    provides = "tosca.nodes.VEEServiceInstance"
    xos_model = VEEServiceInstance
    copyin_props = ["service_specific_id", "s_tag", "c_tag"]

