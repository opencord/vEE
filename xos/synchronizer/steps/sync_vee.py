    import os
import base64
from collections import defaultdict
from django.db.models import F, Q
from xos.config import Config
from synchronizers.base.syncstep import *
from core.models import Controller
from core.models import Image, ControllerImages
from xos.logger import observer_logger as logger
from synchronizers.base.ansible_helper import *
from services.vrouter.models import VRouterTenant
from services.onos.models import ONOSService
from services.fabric.models import FabricService
import json

class SyncVRouterTenant(SyncStep):
    provides=[VRouterTenant]
    observes = VRouterTenant
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
