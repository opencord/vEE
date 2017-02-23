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
            backend_network_label:
                type: string
                required: false
                description: Label that matches network used to connect HPC and BBS services.
            dns_servers:
                type: string
                required: false
            node_label:
                type: string
                required: false

