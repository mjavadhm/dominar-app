package com.dominar.ride.data;

import android.annotation.SuppressLint;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.Context;
import android.os.ParcelUuid;
import android.util.Log;
import kotlinx.coroutines.flow.StateFlow;
import java.util.UUID;

/**
 * BLE Test Manager for communicating with the Bajaj Ride cluster.
 *
 * Characteristic UUIDs from protocol docs:
 * - Navigation (TBT):   0110676e-6972-6565-6e69-676e4543544f
 * - Phone Status:        0210676e-6972-6565-6e69-676e4543544f
 * - Missed Call:         0310676e-6972-6565-6e69-676e4543544f
 * - SMS/WhatsApp Alert:  0410676e-6972-6565-6e69-676e4543544f
 * - Controls:            0a10676e-6972-6565-6e69-676e4543544f
 * - Media Info:          0610676e-6972-6565-6e69-676e4543544f
 */
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u0080\u0001\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010!\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0012\n\u0000\n\u0002\u0010\b\n\u0002\b\u0006\n\u0002\u0010\u000b\n\u0002\b\u0016\n\u0002\u0010\u0002\n\u0002\b\b\n\u0002\u0018\u0002\n\u0002\b\u0004\b\u0007\u0018\u0000 L2\u00020\u0001:\u0002LMB\r\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004J\u001a\u0010 \u001a\u00020!2\b\b\u0002\u0010\"\u001a\u00020#2\b\b\u0002\u0010$\u001a\u00020\fJ\u001a\u0010%\u001a\u00020!2\b\b\u0002\u0010&\u001a\u00020\f2\b\b\u0002\u0010\'\u001a\u00020\fJt\u0010(\u001a\u00020!2\b\b\u0002\u0010)\u001a\u00020*2\b\b\u0002\u0010+\u001a\u00020*2\b\b\u0002\u0010,\u001a\u00020#2\b\b\u0002\u0010-\u001a\u00020#2\b\b\u0002\u0010.\u001a\u00020#2\b\b\u0002\u0010/\u001a\u00020#2\b\b\u0002\u00100\u001a\u00020#2\b\b\u0002\u00101\u001a\u00020#2\b\b\u0002\u00102\u001a\u00020#2\b\b\u0002\u00103\u001a\u00020#2\b\b\u0002\u00104\u001a\u00020\fJ\u0006\u00105\u001a\u00020!J\u0006\u00106\u001a\u00020!JV\u00107\u001a\u00020!2\b\b\u0002\u00108\u001a\u00020#2\b\b\u0002\u00109\u001a\u00020#2\b\b\u0002\u0010:\u001a\u00020#2\b\b\u0002\u0010;\u001a\u00020#2\b\b\u0002\u0010<\u001a\u00020#2\b\b\u0002\u0010=\u001a\u00020*2\b\b\u0002\u0010>\u001a\u00020\f2\b\b\u0002\u0010?\u001a\u00020#J\u0010\u0010@\u001a\u00020A2\u0006\u0010B\u001a\u00020\u000bH\u0007J\b\u0010C\u001a\u00020AH\u0007J\u0010\u0010D\u001a\u00020A2\u0006\u0010E\u001a\u00020\fH\u0002J\b\u0010F\u001a\u00020AH\u0007J\b\u0010G\u001a\u00020AH\u0007J\u0018\u0010H\u001a\u00020*2\u0006\u0010I\u001a\u00020J2\u0006\u0010K\u001a\u00020!H\u0007R\u0014\u0010\u0005\u001a\b\u0012\u0004\u0012\u00020\u00070\u0006X\u0082\u0004\u00a2\u0006\u0002\n\u0000R&\u0010\b\u001a\u001a\u0012\u0016\u0012\u0014\u0012\u0010\u0012\u000e\u0012\u0004\u0012\u00020\u000b\u0012\u0004\u0012\u00020\f0\n0\t0\u0006X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u001a\u0010\r\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\f0\t0\u0006X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0010\u0010\u000e\u001a\u0004\u0018\u00010\u000fX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0017\u0010\u0010\u001a\b\u0012\u0004\u0012\u00020\u00070\u0011\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0012\u0010\u0013R\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0014\u0010\u0014\u001a\b\u0012\u0004\u0012\u00020\u000b0\u0015X\u0082\u000e\u00a2\u0006\u0002\n\u0000R)\u0010\u0016\u001a\u001a\u0012\u0016\u0012\u0014\u0012\u0010\u0012\u000e\u0012\u0004\u0012\u00020\u000b\u0012\u0004\u0012\u00020\f0\n0\t0\u0011\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0017\u0010\u0013R\u000e\u0010\u0018\u001a\u00020\u0019X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u001d\u0010\u001a\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\f0\t0\u0011\u00a2\u0006\b\n\u0000\u001a\u0004\b\u001b\u0010\u0013R\u000e\u0010\u001c\u001a\u00020\u001dX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0010\u0010\u001e\u001a\u0004\u0018\u00010\u001fX\u0082\u000e\u00a2\u0006\u0002\n\u0000\u00a8\u0006N"}, d2 = {"Lcom/dominar/ride/data/BleTestManager;", "", "context", "Landroid/content/Context;", "(Landroid/content/Context;)V", "_connectionState", "Lkotlinx/coroutines/flow/MutableStateFlow;", "Lcom/dominar/ride/data/BleTestManager$ConnectionState;", "_foundDevices", "", "Lkotlin/Pair;", "Landroid/bluetooth/BluetoothDevice;", "", "_logs", "bluetoothGatt", "Landroid/bluetooth/BluetoothGatt;", "connectionState", "Lkotlinx/coroutines/flow/StateFlow;", "getConnectionState", "()Lkotlinx/coroutines/flow/StateFlow;", "discoveredDevices", "", "foundDevices", "getFoundDevices", "gattCallback", "Landroid/bluetooth/BluetoothGattCallback;", "logs", "getLogs", "scanCallback", "Landroid/bluetooth/le/ScanCallback;", "scanner", "Landroid/bluetooth/le/BluetoothLeScanner;", "buildAlertPacket", "", "alertType", "", "content", "buildMissedCallPacket", "name", "number", "buildNavPacket", "isAm", "", "isMeters", "maneuverCode", "distToTurnInt", "distToTurnFrac", "etaHours", "etaMinutes", "distLeftInt", "distLeftFrac", "roundaboutExit", "instructionText", "buildNavStartPacket", "buildNavStopPacket", "buildPhoneStatusPacket", "volume", "headset", "callState", "batteryLevel", "signalStrength", "isActiveCall", "callerName", "heartbeat", "connectToDevice", "", "device", "disconnect", "log", "msg", "startScan", "stopScan", "writeToCharacteristic", "charUuid", "Ljava/util/UUID;", "data", "Companion", "ConnectionState", "app_debug"})
public final class BleTestManager {
    @org.jetbrains.annotations.NotNull()
    private final android.content.Context context = null;
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String TAG = "BleTestManager";
    private static final java.util.UUID UUID_NAV = null;
    private static final java.util.UUID UUID_PHONE = null;
    private static final java.util.UUID UUID_MISSED = null;
    private static final java.util.UUID UUID_ALERT = null;
    private static final java.util.UUID UUID_CONTROLS = null;
    private static final java.util.UUID UUID_MEDIA = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.MutableStateFlow<com.dominar.ride.data.BleTestManager.ConnectionState> _connectionState = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.StateFlow<com.dominar.ride.data.BleTestManager.ConnectionState> connectionState = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.MutableStateFlow<java.util.List<java.lang.String>> _logs = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.StateFlow<java.util.List<java.lang.String>> logs = null;
    @org.jetbrains.annotations.Nullable()
    private android.bluetooth.BluetoothGatt bluetoothGatt;
    @org.jetbrains.annotations.Nullable()
    private android.bluetooth.le.BluetoothLeScanner scanner;
    @org.jetbrains.annotations.NotNull()
    private java.util.List<android.bluetooth.BluetoothDevice> discoveredDevices;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.MutableStateFlow<java.util.List<kotlin.Pair<android.bluetooth.BluetoothDevice, java.lang.String>>> _foundDevices = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.StateFlow<java.util.List<kotlin.Pair<android.bluetooth.BluetoothDevice, java.lang.String>>> foundDevices = null;
    @org.jetbrains.annotations.NotNull()
    private final android.bluetooth.le.ScanCallback scanCallback = null;
    @org.jetbrains.annotations.NotNull()
    private final android.bluetooth.BluetoothGattCallback gattCallback = null;
    @org.jetbrains.annotations.NotNull()
    public static final com.dominar.ride.data.BleTestManager.Companion Companion = null;
    
    public BleTestManager(@org.jetbrains.annotations.NotNull()
    android.content.Context context) {
        super();
    }
    
    @org.jetbrains.annotations.NotNull()
    public final kotlinx.coroutines.flow.StateFlow<com.dominar.ride.data.BleTestManager.ConnectionState> getConnectionState() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final kotlinx.coroutines.flow.StateFlow<java.util.List<java.lang.String>> getLogs() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final kotlinx.coroutines.flow.StateFlow<java.util.List<kotlin.Pair<android.bluetooth.BluetoothDevice, java.lang.String>>> getFoundDevices() {
        return null;
    }
    
    private final void log(java.lang.String msg) {
    }
    
    @android.annotation.SuppressLint(value = {"MissingPermission"})
    public final void startScan() {
    }
    
    @android.annotation.SuppressLint(value = {"MissingPermission"})
    public final void stopScan() {
    }
    
    @android.annotation.SuppressLint(value = {"MissingPermission"})
    public final void connectToDevice(@org.jetbrains.annotations.NotNull()
    android.bluetooth.BluetoothDevice device) {
    }
    
    @android.annotation.SuppressLint(value = {"MissingPermission"})
    public final void disconnect() {
    }
    
    @android.annotation.SuppressLint(value = {"MissingPermission"})
    public final boolean writeToCharacteristic(@org.jetbrains.annotations.NotNull()
    java.util.UUID charUuid, @org.jetbrains.annotations.NotNull()
    byte[] data) {
        return false;
    }
    
    /**
     * Build Navigation packet (48 bytes).
     * Based on protocol Section 1.
     */
    @org.jetbrains.annotations.NotNull()
    public final byte[] buildNavPacket(boolean isAm, boolean isMeters, int maneuverCode, int distToTurnInt, int distToTurnFrac, int etaHours, int etaMinutes, int distLeftInt, int distLeftFrac, int roundaboutExit, @org.jetbrains.annotations.NotNull()
    java.lang.String instructionText) {
        return null;
    }
    
    /**
     * Build Navigation Start packet.
     */
    @org.jetbrains.annotations.NotNull()
    public final byte[] buildNavStartPacket() {
        return null;
    }
    
    /**
     * Build Navigation Stop packet.
     */
    @org.jetbrains.annotations.NotNull()
    public final byte[] buildNavStopPacket() {
        return null;
    }
    
    /**
     * Build Phone Status packet (55 bytes).
     * Based on protocol Section 2.
     */
    @org.jetbrains.annotations.NotNull()
    public final byte[] buildPhoneStatusPacket(int volume, int headset, int callState, int batteryLevel, int signalStrength, boolean isActiveCall, @org.jetbrains.annotations.NotNull()
    java.lang.String callerName, int heartbeat) {
        return null;
    }
    
    /**
     * Build SMS/Alert packet (40 bytes).
     * Based on protocol Section 3.
     */
    @org.jetbrains.annotations.NotNull()
    public final byte[] buildAlertPacket(int alertType, @org.jetbrains.annotations.NotNull()
    java.lang.String content) {
        return null;
    }
    
    /**
     * Build Missed Call packet (74 bytes).
     * Based on protocol Section 2 - Missed Call.
     */
    @org.jetbrains.annotations.NotNull()
    public final byte[] buildMissedCallPacket(@org.jetbrains.annotations.NotNull()
    java.lang.String name, @org.jetbrains.annotations.NotNull()
    java.lang.String number) {
        return null;
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u001a\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u000e\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u0019\u0010\u0005\u001a\n \u0007*\u0004\u0018\u00010\u00060\u0006\u00a2\u0006\b\n\u0000\u001a\u0004\b\b\u0010\tR\u0019\u0010\n\u001a\n \u0007*\u0004\u0018\u00010\u00060\u0006\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000b\u0010\tR\u0019\u0010\f\u001a\n \u0007*\u0004\u0018\u00010\u00060\u0006\u00a2\u0006\b\n\u0000\u001a\u0004\b\r\u0010\tR\u0019\u0010\u000e\u001a\n \u0007*\u0004\u0018\u00010\u00060\u0006\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000f\u0010\tR\u0019\u0010\u0010\u001a\n \u0007*\u0004\u0018\u00010\u00060\u0006\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0011\u0010\tR\u0019\u0010\u0012\u001a\n \u0007*\u0004\u0018\u00010\u00060\u0006\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0013\u0010\t\u00a8\u0006\u0014"}, d2 = {"Lcom/dominar/ride/data/BleTestManager$Companion;", "", "()V", "TAG", "", "UUID_ALERT", "Ljava/util/UUID;", "kotlin.jvm.PlatformType", "getUUID_ALERT", "()Ljava/util/UUID;", "UUID_CONTROLS", "getUUID_CONTROLS", "UUID_MEDIA", "getUUID_MEDIA", "UUID_MISSED", "getUUID_MISSED", "UUID_NAV", "getUUID_NAV", "UUID_PHONE", "getUUID_PHONE", "app_debug"})
    public static final class Companion {
        
        private Companion() {
            super();
        }
        
        public final java.util.UUID getUUID_NAV() {
            return null;
        }
        
        public final java.util.UUID getUUID_PHONE() {
            return null;
        }
        
        public final java.util.UUID getUUID_MISSED() {
            return null;
        }
        
        public final java.util.UUID getUUID_ALERT() {
            return null;
        }
        
        public final java.util.UUID getUUID_CONTROLS() {
            return null;
        }
        
        public final java.util.UUID getUUID_MEDIA() {
            return null;
        }
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000&\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0007\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\b7\u0018\u00002\u00020\u0001:\u0006\u0003\u0004\u0005\u0006\u0007\bB\u0007\b\u0004\u00a2\u0006\u0002\u0010\u0002\u0082\u0001\u0006\t\n\u000b\f\r\u000e\u00a8\u0006\u000f"}, d2 = {"Lcom/dominar/ride/data/BleTestManager$ConnectionState;", "", "()V", "Connected", "Connecting", "DeviceFound", "Disconnected", "Error", "Scanning", "Lcom/dominar/ride/data/BleTestManager$ConnectionState$Connected;", "Lcom/dominar/ride/data/BleTestManager$ConnectionState$Connecting;", "Lcom/dominar/ride/data/BleTestManager$ConnectionState$DeviceFound;", "Lcom/dominar/ride/data/BleTestManager$ConnectionState$Disconnected;", "Lcom/dominar/ride/data/BleTestManager$ConnectionState$Error;", "Lcom/dominar/ride/data/BleTestManager$ConnectionState$Scanning;", "app_debug"})
    public static abstract class ConnectionState {
        
        private ConnectionState() {
            super();
        }
        
        @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000$\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0000\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\b\n\u0000\n\u0002\u0010\u000e\n\u0000\b\u00c7\n\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0013\u0010\u0003\u001a\u00020\u00042\b\u0010\u0005\u001a\u0004\u0018\u00010\u0006H\u00d6\u0003J\t\u0010\u0007\u001a\u00020\bH\u00d6\u0001J\t\u0010\t\u001a\u00020\nH\u00d6\u0001\u00a8\u0006\u000b"}, d2 = {"Lcom/dominar/ride/data/BleTestManager$ConnectionState$Connected;", "Lcom/dominar/ride/data/BleTestManager$ConnectionState;", "()V", "equals", "", "other", "", "hashCode", "", "toString", "", "app_debug"})
        public static final class Connected extends com.dominar.ride.data.BleTestManager.ConnectionState {
            @org.jetbrains.annotations.NotNull()
            public static final com.dominar.ride.data.BleTestManager.ConnectionState.Connected INSTANCE = null;
            
            private Connected() {
            }
            
            @java.lang.Override()
            public boolean equals(@org.jetbrains.annotations.Nullable()
            java.lang.Object other) {
                return false;
            }
            
            @java.lang.Override()
            public int hashCode() {
                return 0;
            }
            
            @java.lang.Override()
            @org.jetbrains.annotations.NotNull()
            public java.lang.String toString() {
                return null;
            }
        }
        
        @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000$\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0000\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\b\n\u0000\n\u0002\u0010\u000e\n\u0000\b\u00c7\n\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0013\u0010\u0003\u001a\u00020\u00042\b\u0010\u0005\u001a\u0004\u0018\u00010\u0006H\u00d6\u0003J\t\u0010\u0007\u001a\u00020\bH\u00d6\u0001J\t\u0010\t\u001a\u00020\nH\u00d6\u0001\u00a8\u0006\u000b"}, d2 = {"Lcom/dominar/ride/data/BleTestManager$ConnectionState$Connecting;", "Lcom/dominar/ride/data/BleTestManager$ConnectionState;", "()V", "equals", "", "other", "", "hashCode", "", "toString", "", "app_debug"})
        public static final class Connecting extends com.dominar.ride.data.BleTestManager.ConnectionState {
            @org.jetbrains.annotations.NotNull()
            public static final com.dominar.ride.data.BleTestManager.ConnectionState.Connecting INSTANCE = null;
            
            private Connecting() {
            }
            
            @java.lang.Override()
            public boolean equals(@org.jetbrains.annotations.Nullable()
            java.lang.Object other) {
                return false;
            }
            
            @java.lang.Override()
            public int hashCode() {
                return 0;
            }
            
            @java.lang.Override()
            @org.jetbrains.annotations.NotNull()
            public java.lang.String toString() {
                return null;
            }
        }
        
        @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000,\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0002\b\t\n\u0002\u0010\u000b\n\u0000\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\b\n\u0002\b\u0002\b\u0087\b\u0018\u00002\u00020\u0001B\u0015\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\u0002\u0010\u0006J\t\u0010\u000b\u001a\u00020\u0003H\u00c6\u0003J\t\u0010\f\u001a\u00020\u0005H\u00c6\u0003J\u001d\u0010\r\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\b\b\u0002\u0010\u0004\u001a\u00020\u0005H\u00c6\u0001J\u0013\u0010\u000e\u001a\u00020\u000f2\b\u0010\u0010\u001a\u0004\u0018\u00010\u0011H\u00d6\u0003J\t\u0010\u0012\u001a\u00020\u0013H\u00d6\u0001J\t\u0010\u0014\u001a\u00020\u0005H\u00d6\u0001R\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0007\u0010\bR\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\t\u0010\n\u00a8\u0006\u0015"}, d2 = {"Lcom/dominar/ride/data/BleTestManager$ConnectionState$DeviceFound;", "Lcom/dominar/ride/data/BleTestManager$ConnectionState;", "device", "Landroid/bluetooth/BluetoothDevice;", "name", "", "(Landroid/bluetooth/BluetoothDevice;Ljava/lang/String;)V", "getDevice", "()Landroid/bluetooth/BluetoothDevice;", "getName", "()Ljava/lang/String;", "component1", "component2", "copy", "equals", "", "other", "", "hashCode", "", "toString", "app_debug"})
        public static final class DeviceFound extends com.dominar.ride.data.BleTestManager.ConnectionState {
            @org.jetbrains.annotations.NotNull()
            private final android.bluetooth.BluetoothDevice device = null;
            @org.jetbrains.annotations.NotNull()
            private final java.lang.String name = null;
            
            public DeviceFound(@org.jetbrains.annotations.NotNull()
            android.bluetooth.BluetoothDevice device, @org.jetbrains.annotations.NotNull()
            java.lang.String name) {
            }
            
            @org.jetbrains.annotations.NotNull()
            public final android.bluetooth.BluetoothDevice getDevice() {
                return null;
            }
            
            @org.jetbrains.annotations.NotNull()
            public final java.lang.String getName() {
                return null;
            }
            
            @org.jetbrains.annotations.NotNull()
            public final android.bluetooth.BluetoothDevice component1() {
                return null;
            }
            
            @org.jetbrains.annotations.NotNull()
            public final java.lang.String component2() {
                return null;
            }
            
            @org.jetbrains.annotations.NotNull()
            public final com.dominar.ride.data.BleTestManager.ConnectionState.DeviceFound copy(@org.jetbrains.annotations.NotNull()
            android.bluetooth.BluetoothDevice device, @org.jetbrains.annotations.NotNull()
            java.lang.String name) {
                return null;
            }
            
            @java.lang.Override()
            public boolean equals(@org.jetbrains.annotations.Nullable()
            java.lang.Object other) {
                return false;
            }
            
            @java.lang.Override()
            public int hashCode() {
                return 0;
            }
            
            @java.lang.Override()
            @org.jetbrains.annotations.NotNull()
            public java.lang.String toString() {
                return null;
            }
        }
        
        @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000$\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0000\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\b\n\u0000\n\u0002\u0010\u000e\n\u0000\b\u00c7\n\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0013\u0010\u0003\u001a\u00020\u00042\b\u0010\u0005\u001a\u0004\u0018\u00010\u0006H\u00d6\u0003J\t\u0010\u0007\u001a\u00020\bH\u00d6\u0001J\t\u0010\t\u001a\u00020\nH\u00d6\u0001\u00a8\u0006\u000b"}, d2 = {"Lcom/dominar/ride/data/BleTestManager$ConnectionState$Disconnected;", "Lcom/dominar/ride/data/BleTestManager$ConnectionState;", "()V", "equals", "", "other", "", "hashCode", "", "toString", "", "app_debug"})
        public static final class Disconnected extends com.dominar.ride.data.BleTestManager.ConnectionState {
            @org.jetbrains.annotations.NotNull()
            public static final com.dominar.ride.data.BleTestManager.ConnectionState.Disconnected INSTANCE = null;
            
            private Disconnected() {
            }
            
            @java.lang.Override()
            public boolean equals(@org.jetbrains.annotations.Nullable()
            java.lang.Object other) {
                return false;
            }
            
            @java.lang.Override()
            public int hashCode() {
                return 0;
            }
            
            @java.lang.Override()
            @org.jetbrains.annotations.NotNull()
            public java.lang.String toString() {
                return null;
            }
        }
        
        @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000&\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0006\n\u0002\u0010\u000b\n\u0000\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\b\n\u0002\b\u0002\b\u0087\b\u0018\u00002\u00020\u0001B\r\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004J\t\u0010\u0007\u001a\u00020\u0003H\u00c6\u0003J\u0013\u0010\b\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u0003H\u00c6\u0001J\u0013\u0010\t\u001a\u00020\n2\b\u0010\u000b\u001a\u0004\u0018\u00010\fH\u00d6\u0003J\t\u0010\r\u001a\u00020\u000eH\u00d6\u0001J\t\u0010\u000f\u001a\u00020\u0003H\u00d6\u0001R\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0005\u0010\u0006\u00a8\u0006\u0010"}, d2 = {"Lcom/dominar/ride/data/BleTestManager$ConnectionState$Error;", "Lcom/dominar/ride/data/BleTestManager$ConnectionState;", "message", "", "(Ljava/lang/String;)V", "getMessage", "()Ljava/lang/String;", "component1", "copy", "equals", "", "other", "", "hashCode", "", "toString", "app_debug"})
        public static final class Error extends com.dominar.ride.data.BleTestManager.ConnectionState {
            @org.jetbrains.annotations.NotNull()
            private final java.lang.String message = null;
            
            public Error(@org.jetbrains.annotations.NotNull()
            java.lang.String message) {
            }
            
            @org.jetbrains.annotations.NotNull()
            public final java.lang.String getMessage() {
                return null;
            }
            
            @org.jetbrains.annotations.NotNull()
            public final java.lang.String component1() {
                return null;
            }
            
            @org.jetbrains.annotations.NotNull()
            public final com.dominar.ride.data.BleTestManager.ConnectionState.Error copy(@org.jetbrains.annotations.NotNull()
            java.lang.String message) {
                return null;
            }
            
            @java.lang.Override()
            public boolean equals(@org.jetbrains.annotations.Nullable()
            java.lang.Object other) {
                return false;
            }
            
            @java.lang.Override()
            public int hashCode() {
                return 0;
            }
            
            @java.lang.Override()
            @org.jetbrains.annotations.NotNull()
            public java.lang.String toString() {
                return null;
            }
        }
        
        @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000$\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0000\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\b\n\u0000\n\u0002\u0010\u000e\n\u0000\b\u00c7\n\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0013\u0010\u0003\u001a\u00020\u00042\b\u0010\u0005\u001a\u0004\u0018\u00010\u0006H\u00d6\u0003J\t\u0010\u0007\u001a\u00020\bH\u00d6\u0001J\t\u0010\t\u001a\u00020\nH\u00d6\u0001\u00a8\u0006\u000b"}, d2 = {"Lcom/dominar/ride/data/BleTestManager$ConnectionState$Scanning;", "Lcom/dominar/ride/data/BleTestManager$ConnectionState;", "()V", "equals", "", "other", "", "hashCode", "", "toString", "", "app_debug"})
        public static final class Scanning extends com.dominar.ride.data.BleTestManager.ConnectionState {
            @org.jetbrains.annotations.NotNull()
            public static final com.dominar.ride.data.BleTestManager.ConnectionState.Scanning INSTANCE = null;
            
            private Scanning() {
            }
            
            @java.lang.Override()
            public boolean equals(@org.jetbrains.annotations.Nullable()
            java.lang.Object other) {
                return false;
            }
            
            @java.lang.Override()
            public int hashCode() {
                return 0;
            }
            
            @java.lang.Override()
            @org.jetbrains.annotations.NotNull()
            public java.lang.String toString() {
                return null;
            }
        }
    }
}