package com.dominar.ride.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DominarProtocolTest {

    @Test
    fun navPacket_is48Bytes_withValidChecksum() {
        val p = DominarProtocol.buildNavigationPacket(
            isPm = false, distanceUnitMeters = false,
            maneuver = DominarProtocol.Maneuver.TURN_LEFT,
            distanceToTurn = 1.45, etaHour = 10, etaMinute = 30,
            distanceLeft = 12.30, instructionText = "Azadi St"
        )
        assertEquals(48, p.size)
        var sum = 0
        for (i in 0 until 47) sum += p[i].toInt() and 0xFF
        assertEquals((sum and 0xFF).toByte(), p[47])
        assertEquals(0x49.toByte(), p[1]) // TURN_LEFT
        assertEquals(45.toByte(), p[2])   // 0.45 -> 45
        assertEquals(1.toByte(), p[4])    // integer part
        assertEquals(30.toByte(), p[6])   // ETA minute
    }

    @Test
    fun persianCallerName_doesNotOverflow() {
        val p = DominarProtocol.buildPhoneStatusPacket(
            volume = 7, headsetConnected = false,
            callState = DominarProtocol.CallState.INCOMING,
            batteryLevel = 4, signalStrength = 3, isActiveCall = false,
            callerName = "محمدجواد حسینی مقدم طولانی",
            heartbeat = 1
        )
        assertEquals(55, p.size)
        assertTrue(p[20].toInt() and 0xFF <= 30)
    }

    @Test
    fun persianSms_fits32Bytes() {
        val p = DominarProtocol.buildAlertPacket(
            DominarProtocol.AlertType.WHATSAPP,
            "سلام، کجایی؟ زنگ بزن بهم لطفا"
        )
        assertEquals(40, p.size)
        assertTrue(p[1].toInt() and 0xFF <= 32)
    }

    @Test
    fun persianInstructionText_isStrippedWithoutCrash() {
        val p = DominarProtocol.buildNavigationPacket(
            isPm = false, distanceUnitMeters = true,
            maneuver = DominarProtocol.Maneuver.STRAIGHT,
            distanceToTurn = 200.0, etaHour = 1, etaMinute = 0,
            distanceLeft = 5.0, instructionText = "خیابان آزادی"
        )
        assertEquals(48, p.size)
    }

    @Test
    fun navStopPacket_isAllZeros() {
        assertTrue(DominarProtocol.buildNavStopPacket().all { it == 0.toByte() })
    }

    @Test
    fun controlPacket_parsesCounters() {
        val c = DominarProtocol.parseControlPacket(byteArrayOf(0x07, 0x01, 0x02, 0x00))
        assertEquals(7, c!!.volume)
        assertEquals(1, c.callAcceptCounter)
        assertEquals(2, c.callRejectCounter)
    }
}
