from synchronizers.base.syncstep import *
from synchronizers.base.ansible_helper import *
from services.vee.models import VEEService

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
