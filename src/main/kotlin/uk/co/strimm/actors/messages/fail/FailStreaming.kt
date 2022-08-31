package uk.co.strimm.actors.messages.fail

import uk.co.strimm.actors.messages.ActorMessage

open class FailStreaming(val ex : Throwable) : ActorMessage()  {
}