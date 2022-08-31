package uk.co.strimm.sourceMethods

import akka.actor.ActorRef
import akka.japi.Pair
import uk.co.strimm.STRIMMBuffer
import uk.co.strimm.experiment.Source
import java.util.*
import java.util.concurrent.CompletableFuture
import kotlin.collections.HashMap

interface SourceMethod {
    val properties : HashMap<String, String>
    var actor : ActorRef?
    fun init(source : Source)
    fun preStart()
    fun run() : STRIMMBuffer?
    fun postStop()
}