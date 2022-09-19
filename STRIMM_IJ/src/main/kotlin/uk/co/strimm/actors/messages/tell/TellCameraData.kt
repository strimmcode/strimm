package uk.co.strimm.actors.messages.tell

import uk.co.strimm.CameraMetaDataStore
import uk.co.strimm.actors.ArrayImgStore

class TellCameraData (val imageStore : ArrayImgStore, val metaData : ArrayList<CameraMetaDataStore>, val dataName : String)