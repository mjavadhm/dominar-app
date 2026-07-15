# Bajaj Ride Bluetooth Protocol Documentation

This document details the Bluetooth Low Energy (BLE) protocol used by the Bajaj Ride app to communicate with the vehicle cluster.

## Service & Characteristics

**Service**: (Implicit in code, likely advertised by device)
**UUIDs**:
- **Navigation (TBT)**: `0110676e-6972-6565-6e69-676e4543544f`
- **General Status (Phone)**: `0210676e-6972-6565-6e69-676e4543544f`
- **Missed Call**: `0310676e-6972-6565-6e69-676e4543544f`
- **Alerts (SMS/WhatsApp)**: `0410676e-6972-6565-6e69-676e4543544f`
- **Controls (Call Accept/Reject, Music)**: `0a10676e-6972-6565-6e69-676e4543544f`
- **Media Info**: `0610676e-6972-6565-6e69-676e4543544f`

---

## 1. Navigation Protocol

**Characteristic**: `0110676e-6972-6565-6e69-676e4543544f`
**Packet Size**: 48 bytes (Typical Update)

### Periodic Update Packet
Sent periodically (every ~1s) during active navigation.

| Offset | Length | Description | Logic / Values |
| :--- | :--- | :--- | :--- |
| 0 | 1 | Flags | `0x81` if PM, `0x01` if AM <br> `Bit 0`: Always 1 <br> `Bit 4`: Distance Unit (1 = Meters, 0 = km/other?) <br> `Bit 7`: Time format (0=AM, 1=PM) |
| 1 | 1 | Maneuver Code | See [Maneuver Codes](#maneuver-codes) |
| 2 | 1 | Dist to Turn (Frac LSB) | Fractional part of distance * 100 (LSB) |
| 3 | 1 | Dist to Turn (Frac MSB) | Fractional part of distance * 100 (MSB) |
| 4 | 1 | Dist to Turn (Int LSB) | Integer part of distance (LSB) |
| 5 | 1 | Dist to Turn (Int MSB) | Integer part of distance (MSB) |
| 6 | 1 | ETA Minutes | Current Minute of ETA |
| 7 | 1 | ETA Hours + Exit | `(RoundaboutExitID << 4) \| (Current Hour)` |
| 8 | 1 | Dist Left (Frac LSB) | Total Distance Left (Frac LSB) |
| 9 | 1 | Dist Left (Frac MSB) | Total Distance Left (Frac MSB) |
| 10 | 1 | Dist Left (Int LSB) | Total Distance Left (Int LSB) |
| 11 | 1 | Dist Left (Int MSB) | Total Distance Left (Int MSB) |
| 12 | 1 | Flags 2 | `Bit 0`: Distance Left Unit? |
| 13 | 1 | Reserved / 0 | |
| 14 | 1 | Text Length | Length of instruction text (Max 31) |
| 15 | 32 | Text | Instruction text (e.g., road name), ASCII |
| 47 | 1 | Checksum | Sum of bytes 0-46 (masked with 0xFF) |

### Start/Stop/Destination Packets
Alternative simpler structure (49 bytes) used for events.

**On Start**:
`01 00 [20 bytes 00...] ...` (Maneuver 0)
**On Stop**:
`00 00 [20 bytes 00...] ...`
**Destination Reached**:
`01 48 00 ...` (Maneuver `0x48` = 72 = DESTINATION_REACHED)

### Maneuver Codes (PrimaryTurns)
| ID | Hex | Action |
| :--- | :--- | :--- |
| 73 | 0x49 | TURN_LEFT |
| 74 | 0x4A | TURN_RIGHT |
| 67 | 0x43 | TURN_SLIGHT_LEFT |
| 68 | 0x44 | TURN_SLIGHT_RIGHT |
| 69 | 0x45 | TURN_SHARP_LEFT |
| 70 | 0x46 | TURN_SHARP_RIGHT |
| 71 | 0x47 | STRAIGHT |
| 78 | 0x4E | ROUNDABOUT_LEFT |
| 85 | 0x55 | ROUNDABOUT_RIGHT |
| 79 | 0x4F | U_TURN_LEFT |
| 80 | 0x50 | U_TURN_RIGHT |
| 72 | 0x48 | DESTINATION_REACHED |

---

## 2. Call Protocol

### Phone Status (General)
**Characteristic**: `0210676e-6972-6565-6e69-676e4543544f`
**Packet Size**: 55 bytes

| Offset | Length | Description | Values |
| :--- | :--- | :--- | :--- |
| 0 | 1 | Status Byte 0 | `(Headset << 4) \| Volume \| 0xC0` |
| 1 | 1 | Status Byte 1 | `CallState \| (BatteryLevel << 3)` |
| 2 | 1 | Signal Strength | 0-4 |
| 3 | 1 | Is Active Call | 1 = Yes, 0 = No |
| 4 | 1 | Is Not Active | 0 = Yes, 1 = No |
| 20 | 1 | Name Length | Max 30 |
| 21 | 30 | Caller Name | UTF-8 String |
| 53 | 1 | Heartbeat | Increments on every update |

**Call States**:
- `0`: NO_CALL
- `1`: INCOMING_CALL
- `2`: OUTGOING_CALL
- `3`: ACTIVE_CALL
- `4`: END_CALL

### Missed Call
**Characteristic**: `0310676e-6972-6565-6e69-676e4543544f`
**Packet Size**: 74 bytes

| Offset | Length | Description | Values |
| :--- | :--- | :--- | :--- |
| 0 | 1 | ID | Always 1? |
| 1 | 1 | Name Length | Max 32 |
| 2 | 32 | Name | UTF-8 String |
| 34 | 1 | Number Length | Max 18 |
| 35 | 18 | Number | UTF-8 String |

### Call Control (Received from Vehicle)
**Characteristic**: `0a10676e-6972-6565-6e69-676e4543544f`
**Read/Notify** from vehicle.

| Offset | Description |
| :--- | :--- |
| 0 | Volume Control (Bits 0-3) |
| 1 | Call Accept (Changed value triggers acceptance) |
| 2 | Call Reject (Changed value triggers rejection) |
| 3 | Call Reject with SMS |

---

## 3. SMS / Alerts Protocol

**Characteristic**: `0410676e-6972-6565-6e69-676e4543544f`
**Packet Size**: 40 bytes

| Offset | Length | Description | Values |
| :--- | :--- | :--- | :--- |
| 0 | 1 | Alert Type | `1` = SMS, `2` = WhatsApp |
| 1 | 1 | Content Length | Max 32 |
| 2 | 32 | Content | Sender Name / Message Snippet |
