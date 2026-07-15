package com.dominar.ride.protocol

import kotlin.math.roundToInt

/**
 * Pure packet builders/parsers for the Bajaj Ride BLE cluster protocol.
 * No Android dependencies — fully unit-testable on the JVM.
 */
object DominarProtocol {

    // Characteristic UUIDs
    const val UUID_NAV = "0110676e-6972-6565-6e69-676e4543544f"
    const val UUID_PHONE = "0210676e-6972-6565-6e69-676e4543544f"
    const val UUID_MISSED = "0310676e-6972-6565-6e69-676e4543544f"
    const val UUID_ALERT = "0410676e-6972-6565-6e69-676e4543544f"
    const val UUID_CONTROLS = "0a10676e-6972-6565-6e69-676e4543544f"
    const val UUID_MEDIA = "0610676e-6972-6565-6e69-676e4543544f"

    enum class Maneuver(val code: Int) {
        TURN_LEFT(0x49), TURN_RIGHT(0x4A),
        TURN_SLIGHT_LEFT(0x43), TURN_SLIGHT_RIGHT(0x44),
        TURN_SHARP_LEFT(0x45), TURN_SHARP_RIGHT(0x46),
        STRAIGHT(0x47),
        ROUNDABOUT_LEFT(0x4E), ROUNDABOUT_RIGHT(0x55),
        U_TURN_LEFT(0x4F), U_TURN_RIGHT(0x50),
        DESTINATION_REACHED(0x48)
    }

    enum class CallState(val code: Int) {
        NO_CALL(0), INCOMING(1), OUTGOING(2), ACTIVE(3), END(4)
    }

    enum class AlertType(val code: Int) { SMS(1), WHATSAPP(2) }

    // ---------- Helpers ----------

    /** Truncate to at most [maxBytes] UTF-8 bytes without splitting a character. */
    internal fun truncateUtf8(text: String, maxBytes: Int): ByteArray {
        var t = text
        while (t.isNotEmpty() && t.toByteArray(Charsets.UTF_8).size > maxBytes) {
            t = t.dropLast(1)
        }
        return t.toByteArray(Charsets.UTF_8)
    }

    /** Keep only printable-ASCII chars, then truncate to [maxLen]. */
    internal fun toAscii(text: String, maxLen: Int): ByteArray {
        val cleaned = text.filter { it.code in 32..126 }.take(maxLen)
        return cleaned.toByteArray(Charsets.US_ASCII)
    }

    internal fun checksum(packet: ByteArray, upToExclusive: Int): Byte {
        var sum = 0
        for (i in 0 until upToExclusive) sum += packet[i].toInt() and 0xFF
        return (sum and 0xFF).toByte()
    }

    // ---------- Navigation (48 bytes) ----------

    fun buildNavigationPacket(
        isPm: Boolean,
        distanceUnitMeters: Boolean,
        maneuver: Maneuver,
        distanceToTurn: Double,
        etaHour: Int,
        etaMinute: Int,
        distanceLeft: Double,
        roundaboutExit: Int = 0,
        instructionText: String = ""
    ): ByteArray {
        val packet = ByteArray(48)

        var flags = 0x01
        if (isPm) flags = flags or 0x80
        if (distanceUnitMeters) flags = flags or 0x10
        packet[0] = flags.toByte()

        packet[1] = maneuver.code.toByte()

        val turnInt = distanceToTurn.toInt()
        val turnFrac = ((distanceToTurn - turnInt) * 100).roundToInt().coerceIn(0, 99)
        packet[2] = (turnFrac and 0xFF).toByte()
        packet[3] = ((turnFrac shr 8) and 0xFF).toByte()
        packet[4] = (turnInt and 0xFF).toByte()
        packet[5] = ((turnInt shr 8) and 0xFF).toByte()

        packet[6] = etaMinute.toByte()
        packet[7] = (((roundaboutExit and 0x0F) shl 4) or (etaHour and 0x0F)).toByte()

        val leftInt = distanceLeft.toInt()
        val leftFrac = ((distanceLeft - leftInt) * 100).roundToInt().coerceIn(0, 99)
        packet[8] = (leftFrac and 0xFF).toByte()
        packet[9] = ((leftFrac shr 8) and 0xFF).toByte()
        packet[10] = (leftInt and 0xFF).toByte()
        packet[11] = ((leftInt shr 8) and 0xFF).toByte()

        packet[12] = 0x00 // Flags2 — TODO: verify with sniffed packets
        packet[13] = 0x00

        val textBytes = toAscii(instructionText, 31)
        packet[14] = textBytes.size.toByte()
        textBytes.copyInto(packet, destinationOffset = 15)

        packet[47] = checksum(packet, 47)
        return packet
    }

    fun buildNavStartPacket(): ByteArray {
        val packet = ByteArray(48)
        packet[0] = 0x01
        packet[47] = checksum(packet, 47)
        return packet
    }

    fun buildNavStopPacket(): ByteArray = ByteArray(48)

    fun buildDestinationReachedPacket(): ByteArray {
        val packet = ByteArray(48)
        packet[0] = 0x01
        packet[1] = Maneuver.DESTINATION_REACHED.code.toByte()
        packet[47] = checksum(packet, 47)
        return packet
    }

    // ---------- Phone status (55 bytes) ----------

    fun buildPhoneStatusPacket(
        volume: Int,
        headsetConnected: Boolean,
        callState: CallState,
        batteryLevel: Int,
        signalStrength: Int,
        isActiveCall: Boolean,
        callerName: String,
        heartbeat: Int
    ): ByteArray {
        val packet = ByteArray(55)
        packet[0] = (0xC0 or ((if (headsetConnected) 1 else 0) shl 4) or (volume and 0x0F)).toByte()
        packet[1] = ((callState.code and 0x07) or ((batteryLevel and 0x1F) shl 3)).toByte()
        packet[2] = (signalStrength and 0xFF).toByte()
        packet[3] = if (isActiveCall) 1 else 0
        packet[4] = if (isActiveCall) 0 else 1

        // FIX: truncate by UTF-8 *bytes*, not chars — Persian names crashed the old code
        val nameBytes = truncateUtf8(callerName, 30)
        packet[20] = nameBytes.size.toByte()
        nameBytes.copyInto(packet, destinationOffset = 21)

        packet[53] = (heartbeat and 0xFF).toByte()
        return packet
    }

    // ---------- Missed call (74 bytes) ----------

    fun buildMissedCallPacket(name: String, number: String): ByteArray {
        val packet = ByteArray(74)
        packet[0] = 1
        val nameBytes = truncateUtf8(name, 32)
        packet[1] = nameBytes.size.toByte()
        nameBytes.copyInto(packet, destinationOffset = 2)
        val numberBytes = truncateUtf8(number, 18)
        packet[34] = numberBytes.size.toByte()
        numberBytes.copyInto(packet, destinationOffset = 35)
        return packet
    }

    // ---------- SMS / WhatsApp alert (40 bytes) ----------

    fun buildAlertPacket(type: AlertType, content: String): ByteArray {
        val packet = ByteArray(40)
        packet[0] = type.code.toByte()
        val contentBytes = truncateUtf8(content, 32)
        packet[1] = contentBytes.size.toByte()
        contentBytes.copyInto(packet, destinationOffset = 2)
        return packet
    }

    // ---------- Control packet (received from vehicle) ----------

    data class ControlState(
        val volume: Int,
        val callAcceptCounter: Int,
        val callRejectCounter: Int,
        val rejectWithSmsCounter: Int
    )

    fun parseControlPacket(data: ByteArray): ControlState? {
        if (data.size < 4) return null
        return ControlState(
            volume = data[0].toInt() and 0x0F,
            callAcceptCounter = data[1].toInt() and 0xFF,
            callRejectCounter = data[2].toInt() and 0xFF,
            rejectWithSmsCounter = data[3].toInt() and 0xFF
        )
    }
}
