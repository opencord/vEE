from django.db import models
from core.models import Service
import traceback
from xos.exceptions import *
from xos.config import Config

VEE_KIND = "vee"

class VEEService(Service):
    KIND = VEE_KIND

    class Meta:
        app_label = "vee"
        verbose_name = "VEE Service"


    autoconfig = models.BooleanField(default=True, help_text="Autoconfigure the vEE")
