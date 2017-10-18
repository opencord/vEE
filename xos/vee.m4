
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


tosca_definitions_version: tosca_simple_yaml_1_0

# compile this with "m4 vee.m4 > vee.yaml"

# include macros
include(macros.m4)

node_types:

    tosca.nodes.VEEService:
        description: >
            CORD: The vEE Service.
        derived_from: tosca.nodes.Root
        capabilities:
            xos_base_service_caps
        properties:
            xos_base_props
            xos_base_service_props

    tosca.nodes.VEEServiceInstance:
        derived_from: tosca.nodes.Root
        description: >
            A VEE Service Instance.
        properties:
            xos_base_tenant_props
            s_tag:
                type: integer
                required: false
            c_tag:
                type: integer
                required: false
