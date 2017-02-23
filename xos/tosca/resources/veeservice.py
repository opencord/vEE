from service import XOSService
from services.vee.models import VEEService

class VEEService(XOSService):
    provides = "tosca.nodes.VEEService"
    xos_model = VEEService
    copyin_props = ["view_url", "icon_url", "enabled", "published", "public_key", "versionNumber"]

