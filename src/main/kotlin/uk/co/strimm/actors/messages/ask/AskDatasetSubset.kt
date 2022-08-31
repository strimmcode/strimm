package uk.co.strimm.actors.messages.ask

import uk.co.strimm.actors.messages.ActorMessage

class AskDatasetSubset(val ix1 : Int, val ix2 : Int, val szDataset : String) : ActorMessage()