package uk.co.strimm.settings

import uk.co.strimm.SettingKeys

class GeneralSettings : SettingGroup(SettingKeys.GeneralSettings.GROUP_NAME){
    var settings = arrayListOf<Setting>()
}