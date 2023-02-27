package uk.co.strimm.actors.messages

import akka.actor.ActorRef

class WatchThisActor(val actorToWatch : ActorRef) : ActorMessage()