
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


from django.contrib import admin

from services.fabric.models import *
from django import forms
from django.utils.safestring import mark_safe
from django.contrib.auth.admin import UserAdmin
from django.contrib.admin.widgets import FilteredSelectMultiple
from django.contrib.auth.forms import ReadOnlyPasswordHashField
from django.contrib.auth.signals import user_logged_in
from django.utils import timezone
from django.contrib.contenttypes import generic
from suit.widgets import LinkedSelect
from core.models import AddressPool
from core.admin import ServiceAppAdmin,SliceInline,ServiceAttrAsTabInline, ReadOnlyAwareAdmin, XOSTabularInline, ServicePrivilegeInline, TenantRootTenantInline, TenantRootPrivilegeInline
from core.middleware import get_request

from functools import update_wrapper
from django.contrib.admin.views.main import ChangeList
from django.core.urlresolvers import reverse
from django.contrib.admin.utils import quote

#TODO this class has to be redone according to vEE needs
class VEEServiceForm(forms.ModelForm):
    def __init__(self,*args,**kwargs):
        super (VEEServiceForm,self ).__init__(*args,**kwargs)

    def save(self, commit=True):
        return super(VEEServiceForm, self).save(commit=commit)

    class Meta:
        model = VEEService
        fields = '__all__'

class VEEServiceAdmin(ReadOnlyAwareAdmin):
    model = VEEService
    verbose_name = "VEE Service"
    verbose_name_plural = "VEE Services"
    list_display = ("backend_status_icon", "name", "enabled")
    list_display_links = ('backend_status_icon', 'name', )
    fieldsets = [(None, {'fields': ['backend_status_text', 'name','enabled','versionNumber', 'description', "view_url", "icon_url", "autoconfig", ],
                         'classes':['suit-tab suit-tab-general']})]
    readonly_fields = ('backend_status_text', )
    inlines = [SliceInline,ServiceAttrAsTabInline,ServicePrivilegeInline]
    form = VEEServiceForm

    extracontext_registered_admins = True

    user_readonly_fields = ["name", "enabled", "versionNumber", "description"]

    suit_form_tabs =(('general', 'VEE Service Details'),
        ('administration', 'Administration'),
        #('tools', 'Tools'),
        ('slices','Slices'),
        ('serviceattrs','Additional Attributes'),
        ('serviceprivileges','Privileges'),
    )

    suit_form_includes = (('veeadmin.html', 'top', 'administration'),
                           )

    def get_queryset(self, request):
        return VEEService.get_service_objects_by_user(request.user)

admin.site.register(VEEService, VEEServiceAdmin)
