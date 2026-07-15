package com.dominar.ride.ui.screens;

import android.bluetooth.BluetoothDevice;
import androidx.lifecycle.ViewModel;
import com.dominar.ride.data.BleTestManager;
import kotlinx.coroutines.flow.StateFlow;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000R\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0010\u000e\n\u0002\b\u0003\n\u0002\u0010\b\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0010\n\u0002\u0010\u000b\n\u0002\b\u0010\n\u0002\u0010\u0002\n\u0002\b\u000e\b\u0007\u0018\u00002\u00020\u0001B\u0005\u00a2\u0006\u0002\u0010\u0002J\u0015\u00106\u001a\u0004\u0018\u0001072\u0006\u00108\u001a\u00020\u0015\u00a2\u0006\u0002\u00109J\r\u0010:\u001a\u0004\u0018\u000107\u00a2\u0006\u0002\u0010;J\u000e\u0010<\u001a\u0002072\u0006\u0010=\u001a\u00020\fJ\u0006\u0010>\u001a\u000207J\u0006\u0010?\u001a\u000207J\u0006\u0010@\u001a\u000207J\u0006\u0010A\u001a\u000207J\u0006\u0010B\u001a\u000207J\r\u0010C\u001a\u0004\u0018\u000107\u00a2\u0006\u0002\u0010;J\r\u0010D\u001a\u0004\u0018\u000107\u00a2\u0006\u0002\u0010;R\u0017\u0010\u0003\u001a\b\u0012\u0004\u0012\u00020\u00050\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0006\u0010\u0007R\u0017\u0010\b\u001a\b\u0012\u0004\u0012\u00020\t0\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u0007R\u0010\u0010\u000b\u001a\u0004\u0018\u00010\fX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0017\u0010\r\u001a\b\u0012\u0004\u0012\u00020\u000f0\u000e8F\u00a2\u0006\u0006\u001a\u0004\b\u0010\u0010\u0011R)\u0010\u0012\u001a\u001a\u0012\u0016\u0012\u0014\u0012\u0010\u0012\u000e\u0012\u0004\u0012\u00020\u0015\u0012\u0004\u0012\u00020\u00050\u00140\u00130\u000e8F\u00a2\u0006\u0006\u001a\u0004\b\u0016\u0010\u0011R\u001d\u0010\u0017\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\u00050\u00130\u000e8F\u00a2\u0006\u0006\u001a\u0004\b\u0018\u0010\u0011R\u0017\u0010\u0019\u001a\b\u0012\u0004\u0012\u00020\u00050\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u001a\u0010\u0007R\u0017\u0010\u001b\u001a\b\u0012\u0004\u0012\u00020\u00050\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u001c\u0010\u0007R\u0017\u0010\u001d\u001a\b\u0012\u0004\u0012\u00020\u00050\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u001e\u0010\u0007R\u0017\u0010\u001f\u001a\b\u0012\u0004\u0012\u00020\u00050\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b \u0010\u0007R\u0017\u0010!\u001a\b\u0012\u0004\u0012\u00020\u00050\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\"\u0010\u0007R\u0017\u0010#\u001a\b\u0012\u0004\u0012\u00020\u00050\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b$\u0010\u0007R\u0017\u0010%\u001a\b\u0012\u0004\u0012\u00020&0\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\'\u0010\u0007R\u0017\u0010(\u001a\b\u0012\u0004\u0012\u00020\t0\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b)\u0010\u0007R\u0017\u0010*\u001a\b\u0012\u0004\u0012\u00020\u00050\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b+\u0010\u0007R\u0017\u0010,\u001a\b\u0012\u0004\u0012\u00020\t0\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b-\u0010\u0007R\u0017\u0010.\u001a\b\u0012\u0004\u0012\u00020\t0\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b/\u0010\u0007R\u0017\u00100\u001a\b\u0012\u0004\u0012\u00020\u00050\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b1\u0010\u0007R\u0017\u00102\u001a\b\u0012\u0004\u0012\u00020\t0\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b3\u0010\u0007R\u0017\u00104\u001a\b\u0012\u0004\u0012\u00020\t0\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b5\u0010\u0007\u00a8\u0006E"}, d2 = {"Lcom/dominar/ride/ui/screens/BleTestViewModel;", "Landroidx/lifecycle/ViewModel;", "()V", "alertContent", "Lkotlinx/coroutines/flow/MutableStateFlow;", "", "getAlertContent", "()Lkotlinx/coroutines/flow/MutableStateFlow;", "alertType", "", "getAlertType", "bleManager", "Lcom/dominar/ride/data/BleTestManager;", "connectionState", "Lkotlinx/coroutines/flow/StateFlow;", "Lcom/dominar/ride/data/BleTestManager$ConnectionState;", "getConnectionState", "()Lkotlinx/coroutines/flow/StateFlow;", "foundDevices", "", "Lkotlin/Pair;", "Landroid/bluetooth/BluetoothDevice;", "getFoundDevices", "logs", "getLogs", "navDistFrac", "getNavDistFrac", "navDistLeftFrac", "getNavDistLeftFrac", "navDistLeftInt", "getNavDistLeftInt", "navDistance", "getNavDistance", "navEtaH", "getNavEtaH", "navEtaM", "getNavEtaM", "navIsMeters", "", "getNavIsMeters", "navManeuver", "getNavManeuver", "navText", "getNavText", "phoneBattery", "getPhoneBattery", "phoneCallState", "getPhoneCallState", "phoneCallerName", "getPhoneCallerName", "phoneSignal", "getPhoneSignal", "phoneVolume", "getPhoneVolume", "connectToDevice", "", "device", "(Landroid/bluetooth/BluetoothDevice;)Lkotlin/Unit;", "disconnect", "()Lkotlin/Unit;", "initManager", "manager", "sendAlert", "sendNavPacket", "sendNavStart", "sendNavStop", "sendPhoneStatus", "startScan", "stopScan", "app_debug"})
public final class BleTestViewModel extends androidx.lifecycle.ViewModel {
    @org.jetbrains.annotations.Nullable()
    private com.dominar.ride.data.BleTestManager bleManager;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.MutableStateFlow<java.lang.Integer> navManeuver = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.MutableStateFlow<java.lang.String> navDistance = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.MutableStateFlow<java.lang.String> navDistFrac = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.MutableStateFlow<java.lang.String> navDistLeftInt = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.MutableStateFlow<java.lang.String> navDistLeftFrac = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.MutableStateFlow<java.lang.Boolean> navIsMeters = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.MutableStateFlow<java.lang.String> navText = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.MutableStateFlow<java.lang.String> navEtaH = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.MutableStateFlow<java.lang.String> navEtaM = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.MutableStateFlow<java.lang.Integer> alertType = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.MutableStateFlow<java.lang.String> alertContent = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.MutableStateFlow<java.lang.Integer> phoneBattery = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.MutableStateFlow<java.lang.Integer> phoneSignal = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.MutableStateFlow<java.lang.Integer> phoneVolume = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.MutableStateFlow<java.lang.Integer> phoneCallState = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.MutableStateFlow<java.lang.String> phoneCallerName = null;
    
    public BleTestViewModel() {
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
    
    @org.jetbrains.annotations.NotNull()
    public final kotlinx.coroutines.flow.MutableStateFlow<java.lang.Integer> getNavManeuver() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final kotlinx.coroutines.flow.MutableStateFlow<java.lang.String> getNavDistance() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final kotlinx.coroutines.flow.MutableStateFlow<java.lang.String> getNavDistFrac() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final kotlinx.coroutines.flow.MutableStateFlow<java.lang.String> getNavDistLeftInt() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final kotlinx.coroutines.flow.MutableStateFlow<java.lang.String> getNavDistLeftFrac() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final kotlinx.coroutines.flow.MutableStateFlow<java.lang.Boolean> getNavIsMeters() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final kotlinx.coroutines.flow.MutableStateFlow<java.lang.String> getNavText() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final kotlinx.coroutines.flow.MutableStateFlow<java.lang.String> getNavEtaH() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final kotlinx.coroutines.flow.MutableStateFlow<java.lang.String> getNavEtaM() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final kotlinx.coroutines.flow.MutableStateFlow<java.lang.Integer> getAlertType() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final kotlinx.coroutines.flow.MutableStateFlow<java.lang.String> getAlertContent() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final kotlinx.coroutines.flow.MutableStateFlow<java.lang.Integer> getPhoneBattery() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final kotlinx.coroutines.flow.MutableStateFlow<java.lang.Integer> getPhoneSignal() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final kotlinx.coroutines.flow.MutableStateFlow<java.lang.Integer> getPhoneVolume() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final kotlinx.coroutines.flow.MutableStateFlow<java.lang.Integer> getPhoneCallState() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final kotlinx.coroutines.flow.MutableStateFlow<java.lang.String> getPhoneCallerName() {
        return null;
    }
    
    public final void initManager(@org.jetbrains.annotations.NotNull()
    com.dominar.ride.data.BleTestManager manager) {
    }
    
    @org.jetbrains.annotations.Nullable()
    public final kotlin.Unit startScan() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final kotlin.Unit stopScan() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final kotlin.Unit connectToDevice(@org.jetbrains.annotations.NotNull()
    android.bluetooth.BluetoothDevice device) {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final kotlin.Unit disconnect() {
        return null;
    }
    
    public final void sendNavPacket() {
    }
    
    public final void sendNavStart() {
    }
    
    public final void sendNavStop() {
    }
    
    public final void sendAlert() {
    }
    
    public final void sendPhoneStatus() {
    }
}