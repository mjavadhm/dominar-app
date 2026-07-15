package com.dominar.ride.ui.screens;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.compose.foundation.*;
import androidx.compose.foundation.layout.*;
import androidx.compose.material3.*;
import androidx.compose.runtime.*;
import androidx.compose.ui.Alignment;
import androidx.compose.ui.Modifier;
import androidx.compose.ui.text.font.FontWeight;
import androidx.core.content.ContextCompat;
import com.dominar.ride.data.BleTestManager;
import com.dominar.ride.ui.theme.*;

@kotlin.Metadata(mv = {1, 9, 0}, k = 2, xi = 48, d1 = {"\u0000X\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010\u000b\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\b\u0007\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0006\u001a\u0018\u0010\u0000\u001a\u00020\u00012\u0006\u0010\u0002\u001a\u00020\u00032\u0006\u0010\u0004\u001a\u00020\u0005H\u0003\u001a\u001e\u0010\u0006\u001a\u00020\u00012\u0006\u0010\u0007\u001a\u00020\u00032\f\u0010\b\u001a\b\u0012\u0004\u0012\u00020\u00010\tH\u0007\u001a:\u0010\n\u001a\u00020\u00012\u0006\u0010\u0002\u001a\u00020\u00032\u0006\u0010\u0004\u001a\u00020\u00052\u0018\u0010\u000b\u001a\u0014\u0012\u0010\u0012\u000e\u0012\u0004\u0012\u00020\u000e\u0012\u0004\u0012\u00020\u000f0\r0\f2\u0006\u0010\u0010\u001a\u00020\u0011H\u0003\u001a,\u0010\u0012\u001a\u00020\u00012\u0006\u0010\u0013\u001a\u00020\u000f2\u0006\u0010\u0014\u001a\u00020\u000f2\u0012\u0010\u0015\u001a\u000e\u0012\u0004\u0012\u00020\u000f\u0012\u0004\u0012\u00020\u00010\u0016H\u0003\u001a\u0016\u0010\u0017\u001a\u00020\u00012\f\u0010\u0018\u001a\b\u0012\u0004\u0012\u00020\u000f0\fH\u0003\u001a\u0018\u0010\u0019\u001a\u00020\u00012\u0006\u0010\u0002\u001a\u00020\u00032\u0006\u0010\u0004\u001a\u00020\u0005H\u0003\u001a\u0018\u0010\u001a\u001a\u00020\u00012\u0006\u0010\u0002\u001a\u00020\u00032\u0006\u0010\u0004\u001a\u00020\u0005H\u0003\u001a.\u0010\u001b\u001a\u00020\u00012\u0006\u0010\u001c\u001a\u00020\u000f2\u001c\u0010\u001d\u001a\u0018\u0012\u0004\u0012\u00020\u001e\u0012\u0004\u0012\u00020\u00010\u0016\u00a2\u0006\u0002\b\u001f\u00a2\u0006\u0002\b H\u0003\u001aD\u0010!\u001a\u00020\u00012\u0006\u0010\u0013\u001a\u00020\u000f2\u0006\u0010\"\u001a\u00020#2\b\b\u0002\u0010$\u001a\u00020\u00112\b\b\u0002\u0010%\u001a\u00020\u00112\f\u0010&\u001a\b\u0012\u0004\u0012\u00020\u00010\tH\u0003\u00f8\u0001\u0000\u00a2\u0006\u0004\b\'\u0010(\u0082\u0002\u0007\n\u0005\b\u00a1\u001e0\u0001\u00a8\u0006)"}, d2 = {"AlertTab", "", "vm", "Lcom/dominar/ride/ui/screens/BleTestViewModel;", "state", "Lcom/dominar/ride/data/BleTestManager$ConnectionState;", "BleTestScreen", "viewModel", "onBack", "Lkotlin/Function0;", "ConnectionTab", "devices", "", "Lkotlin/Pair;", "Landroid/bluetooth/BluetoothDevice;", "", "permsGranted", "", "FormField", "label", "value", "onValueChange", "Lkotlin/Function1;", "LogPanel", "logs", "NavigationTab", "PhoneTab", "SectionCard", "title", "content", "Landroidx/compose/foundation/layout/ColumnScope;", "Landroidx/compose/runtime/Composable;", "Lkotlin/ExtensionFunctionType;", "TestButton", "color", "Landroidx/compose/ui/graphics/Color;", "enabled", "fullWidth", "onClick", "TestButton-iJQMabo", "(Ljava/lang/String;JZZLkotlin/jvm/functions/Function0;)V", "app_debug"})
public final class BleTestScreenKt {
    
    @androidx.compose.runtime.Composable()
    public static final void BleTestScreen(@org.jetbrains.annotations.NotNull()
    com.dominar.ride.ui.screens.BleTestViewModel viewModel, @org.jetbrains.annotations.NotNull()
    kotlin.jvm.functions.Function0<kotlin.Unit> onBack) {
    }
    
    @androidx.compose.runtime.Composable()
    private static final void ConnectionTab(com.dominar.ride.ui.screens.BleTestViewModel vm, com.dominar.ride.data.BleTestManager.ConnectionState state, java.util.List<kotlin.Pair<android.bluetooth.BluetoothDevice, java.lang.String>> devices, boolean permsGranted) {
    }
    
    @androidx.compose.runtime.Composable()
    private static final void NavigationTab(com.dominar.ride.ui.screens.BleTestViewModel vm, com.dominar.ride.data.BleTestManager.ConnectionState state) {
    }
    
    @androidx.compose.runtime.Composable()
    private static final void AlertTab(com.dominar.ride.ui.screens.BleTestViewModel vm, com.dominar.ride.data.BleTestManager.ConnectionState state) {
    }
    
    @androidx.compose.runtime.Composable()
    private static final void PhoneTab(com.dominar.ride.ui.screens.BleTestViewModel vm, com.dominar.ride.data.BleTestManager.ConnectionState state) {
    }
    
    @androidx.compose.runtime.Composable()
    private static final void SectionCard(java.lang.String title, kotlin.jvm.functions.Function1<? super androidx.compose.foundation.layout.ColumnScope, kotlin.Unit> content) {
    }
    
    @androidx.compose.runtime.Composable()
    private static final void FormField(java.lang.String label, java.lang.String value, kotlin.jvm.functions.Function1<? super java.lang.String, kotlin.Unit> onValueChange) {
    }
    
    @androidx.compose.runtime.Composable()
    private static final void LogPanel(java.util.List<java.lang.String> logs) {
    }
}