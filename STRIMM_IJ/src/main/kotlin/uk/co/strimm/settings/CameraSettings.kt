package uk.co.strimm.settings

import uk.co.strimm.SettingKeys

class CameraSettings : SettingGroup(SettingKeys.CameraSettings.GROUP_NAME){
    var settings = arrayListOf<Setting>()
}