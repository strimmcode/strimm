package uk.co.strimm.actors.messages.ask
import uk.co.strimm.STRIMMBuffer
import uk.co.strimm.actors.messages.ActorMessage

class AskFlushSTRIMMData(var data : List< List<STRIMMBuffer> >) : ActorMessage()