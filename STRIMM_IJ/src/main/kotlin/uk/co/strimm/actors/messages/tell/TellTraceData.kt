package uk.co.strimm.actors.messages.tell

import uk.co.strimm.TraceDataStore
import uk.co.strimm.actors.messages.ActorMessage
import java.util.ArrayList

class TellTraceData(val traceData : ArrayList<TraceDataStore>, val isTraceFromROI : Boolean) : ActorMessage()