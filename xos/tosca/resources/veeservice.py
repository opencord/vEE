from service import XOSService
from services.vee.models import VeeService

class VeeService(XOSService):
    provides = "tosca.nodes.VeeService"
    xos_model = VeeService
    copyin_props = ["view_url", "icon_url", "enabled", "published", "public_key", "versionNumber"]

