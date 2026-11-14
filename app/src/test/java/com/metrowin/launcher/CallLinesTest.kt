package com.metrowin.launcher

import android.telecom.Call
import android.telecom.CallAudioState
import com.metrowin.launcher.calls.CallLine
import com.metrowin.launcher.calls.CallLines
import com.metrowin.launcher.calls.MergeAction
import com.metrowin.launcher.calls.OutputKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CallLinesTest {
    private fun line(id: Int, state: Int, parent: Int? = null) = CallLine(id, "1$id", null, state, 0, true, false, false, parentId = parent)

    @Test
    fun theRingingCallIsTheOneTheScreenIsAbout() {
        val lines = listOf(line(1, Call.STATE_ACTIVE), line(2, Call.STATE_RINGING))
        assertEquals(2, CallLines.main(lines)?.id)
    }

    @Test
    fun aHeldCallComesAfterOneThatIsOn() {
        assertEquals(2, CallLines.main(listOf(line(1, Call.STATE_HOLDING), line(2, Call.STATE_ACTIVE)))?.id)
        assertEquals(1, CallLines.main(listOf(line(1, Call.STATE_HOLDING)))?.id)
        assertNull(CallLines.main(emptyList()))
    }

    @Test
    fun thePartsOfAConferenceAreNeverTheMainCall() {
        val lines = listOf(line(3, Call.STATE_ACTIVE, parent = 1), line(1, Call.STATE_ACTIVE))
        assertEquals(1, CallLines.main(lines)?.id)
    }

    @Test
    fun endAndAnswerEndsTheCallThatIsOnOnly() {
        val lines = listOf(line(1, Call.STATE_HOLDING), line(2, Call.STATE_ACTIVE), line(3, Call.STATE_RINGING))
        assertEquals(2, CallLines.toEnd(lines)?.id)
        assertNull(CallLines.toEnd(listOf(line(1, Call.STATE_HOLDING), line(3, Call.STATE_RINGING))))
    }

    @Test
    fun mergeChoosesTheWayTheOperatorAllows() {
        assertEquals(MergeAction.Merge, CallLines.mergeAction(canMergeConference = true, canHold = false, hasOther = false))
        assertEquals(MergeAction.Conference, CallLines.mergeAction(canMergeConference = false, canHold = true, hasOther = true))
        assertEquals(MergeAction.None, CallLines.mergeAction(canMergeConference = false, canHold = true, hasOther = false))
        assertEquals(MergeAction.None, CallLines.mergeAction(canMergeConference = false, canHold = false, hasOther = true))
    }

    @Test
    @Suppress("DEPRECATION")
    fun theOutputsAreTheRoutesTheDeviceHasWithTheCurrentOneMarked() {
        val outputs = CallLines.outputsOf(CallAudioState.ROUTE_EARPIECE or CallAudioState.ROUTE_SPEAKER or CallAudioState.ROUTE_BLUETOOTH, CallAudioState.ROUTE_SPEAKER)
        assertEquals(listOf(OutputKind.Ear, OutputKind.Bluetooth, OutputKind.Speaker), outputs.map { it.kind })
        assertEquals(listOf(OutputKind.Speaker), outputs.filter { it.active }.map { it.kind })
    }
}
