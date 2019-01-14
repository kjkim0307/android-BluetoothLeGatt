/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.bluetoothlegatt;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ExpandableListView;
import android.widget.ImageView;
import android.widget.SimpleExpandableListAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * For a given BLE device, this Activity provides the user interface to connect, display data,
 * and display GATT services and characteristics supported by the device.  The Activity
 * communicates with {@code BluetoothLeService}, which in turn interacts with the
 * Bluetooth LE API.
 */
public class DeviceControlActivity extends AppCompatActivity {
    private final static String TAG = DeviceControlActivity.class.getSimpleName();

    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";

    private TextView mConnectionState;
    private TextView mCommandField;
    private TextView mDataField;
    private Button mIRON, mWhiteON, mOffAll, mFlash;
    private String mDeviceName;
    private String mDeviceAddress;
    private BluetoothLeService mBluetoothLeService;
    private ArrayList<ArrayList<BluetoothGattCharacteristic>> mGattCharacteristics =
            new ArrayList<ArrayList<BluetoothGattCharacteristic>>();
    private boolean mConnected = false;
    private BluetoothGattCharacteristic mNotifyCharacteristic;

    byte sendByte[] = new byte[20];
    private ImageView iv1,iv2,iv3,iv4,iv5,iv6,iv7,iv8;
    private byte portStatus;
    private byte ledStatus = 0;
    private byte pwmStatus;
    private byte linklossStatus;
    private byte adctimeHighStatus;
    private byte adctimeLowStatus;

    private final String LIST_NAME = "NAME";
    private final String LIST_UUID = "UUID";

    private CameraBasicFragment mCameraBasicFragment;
    private static final int REQUEST_WRITE_PERMISSION = 2;

    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            // Automatically connects to the device upon successful start-up initialization.
            mBluetoothLeService.connect(mDeviceAddress);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };

    // Handles various events fired by the Service.
    // ACTION_GATT_CONNECTED: connected to a GATT server.
    // ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
    // ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
    // ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read
    //                        or notification operations.
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                mConnected = true;
                updateConnectionState(R.string.connected);
                invalidateOptionsMenu();
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;
                updateConnectionState(R.string.disconnected);
                invalidateOptionsMenu();
                clearUI();
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                // Show all the supported services and characteristics on the user interface.
                displayGattServices(mBluetoothLeService.getSupportedGattServices());
                getDeviceSetting();
            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                byte[] sendByte = intent.getByteArrayExtra("init");

                if((sendByte[0] == 0x55) && (sendByte[1] == 0x33)){
                    Log.d(TAG,"======= Init Setting Data ");
                    updateCommandState("Init Data");

                    portStatus = sendByte[2];
                    pwmStatus = sendByte[3];
                    linklossStatus = sendByte[4];

                    adctimeHighStatus = sendByte[5];
                    adctimeLowStatus = sendByte[6];

                    setPortStatus(portStatus);
                    //setPWMStatus(pwmStatus);
                    //setAdcTimeStatus(adctimeHighStatus,adctimeLowStatus);

                    ledStatus = (byte) 0xff;
                    sendLedStatus();
                }

                displayData(intent.getStringExtra(BluetoothLeService.EXTRA_DATA));
            }
        }
    };


    private void getDeviceSetting(){
        if(mGattCharacteristics != null){
            final BluetoothGattCharacteristic characteristic = mGattCharacteristics.get(6).get(0);
            mBluetoothLeService.readCharacteristic(characteristic);
        }
    }

    private void updateCommandState(final String str) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mCommandField.setText(str);
            }
        });
    }


    // If a given GATT characteristic is selected, check for supported features.  This sample
    // demonstrates 'Read' and 'Notify' features.  See
    // http://d.android.com/reference/android/bluetooth/BluetoothGatt.html for the complete
    // list of supported characteristic features.
    private final ExpandableListView.OnChildClickListener servicesListClickListner =
            new ExpandableListView.OnChildClickListener() {
                @Override
                public boolean onChildClick(ExpandableListView parent, View v, int groupPosition,
                                            int childPosition, long id) {
                    if (mGattCharacteristics != null) {
                        final BluetoothGattCharacteristic characteristic =
                                mGattCharacteristics.get(groupPosition).get(childPosition);
                        final int charaProp = characteristic.getProperties();
                        if ((charaProp | BluetoothGattCharacteristic.PROPERTY_READ) > 0) {
                            // If there is an active notification on a characteristic, clear
                            // it first so it doesn't update the data field on the user interface.
                            if (mNotifyCharacteristic != null) {
                                mBluetoothLeService.setCharacteristicNotification(
                                        mNotifyCharacteristic, false);
                                mNotifyCharacteristic = null;
                            }
                            mBluetoothLeService.readCharacteristic(characteristic);
                        }
                        if ((charaProp | BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
                            mNotifyCharacteristic = characteristic;
                            mBluetoothLeService.setCharacteristicNotification(
                                    characteristic, true);
                        }
                        return true;
                    }
                    return false;
                }
    };

    private void clearUI() {
        mDataField.setText(R.string.no_data);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.gatt_services_characteristics);

        final Intent intent = getIntent();
        mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);

        // Sets up UI references.
        ((TextView) findViewById(R.id.device_address)).setText(mDeviceAddress);
        mConnectionState = (TextView) findViewById(R.id.connection_state);
        mCommandField = (TextView) findViewById(R.id.command_value);
        mDataField = (TextView) findViewById(R.id.data_value);
        final Handler mHandler = new Handler();

        getSupportActionBar().setTitle(mDeviceName);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

        iv1 = (ImageView) findViewById(R.id.imageView1);
        iv2 = (ImageView) findViewById(R.id.imageView2);
        iv3 = (ImageView) findViewById(R.id.imageView3);
        iv4 = (ImageView) findViewById(R.id.imageView4);
        iv5 = (ImageView) findViewById(R.id.imageView5);
        iv6 = (ImageView) findViewById(R.id.imageView6);
        iv7 = (ImageView) findViewById(R.id.imageView7);
        iv8 = (ImageView) findViewById(R.id.imageView8);

        iv1.setOnTouchListener(onBtnTouchListener);
        iv2.setOnTouchListener(onBtnTouchListener);
        iv3.setOnTouchListener(onBtnTouchListener);
        iv4.setOnTouchListener(onBtnTouchListener);
        iv5.setOnTouchListener(onBtnTouchListener);
        iv6.setOnTouchListener(onBtnTouchListener);
        iv7.setOnTouchListener(onBtnTouchListener);
        iv8.setOnTouchListener(onBtnTouchListener);

        mIRON = (Button)findViewById(R.id.btn_ir_on);
        mIRON.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                turnOnIR();
            }
        });
        mWhiteON = (Button)findViewById(R.id.btn_led_on);
        mWhiteON.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                turnOnLED();
            }
        });
        mOffAll = (Button)findViewById(R.id.btn_off_all);
        mOffAll.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ledStatus = (byte)0xFF;
                setPIOImg();
                sendLedStatus();
            }
        });
        mFlash = (Button)findViewById(R.id.btn_flash);
        mFlash.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // LED 켜고, 300ms후에 IR킴
                if (mCameraBasicFragment != null) {
                    mCameraBasicFragment.takePicture();
                }
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        turnOnLED();
                    }
                }, 550);
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        turnOnIR();
                    }
                }, 850);
            }
        });
        mCameraBasicFragment = null;
        if (null == savedInstanceState) {
            mCameraBasicFragment = CameraBasicFragment.newInstance();
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.container, mCameraBasicFragment)
                    .commit();
        }

        if(ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED){
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQUEST_WRITE_PERMISSION);
        }

    }

    private void turnOnIR(){
        // IR에 해당하는 5번 키고 나머지는 끄고.
        ledStatus = (byte) 0xDF;
        setPIOImg();
        sendLedStatus();
    }
    private void turnOnLED(){
        // LED에 해당하는 6번 키고 나머지는 끄고.
        ledStatus = (byte) 0xBF;
        setPIOImg();
        sendLedStatus();
    }



    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (mBluetoothLeService != null) {
            final boolean result = mBluetoothLeService.connect(mDeviceAddress);
            Log.d(TAG, "Connect request result=" + result);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.gatt_services, menu);
        if (mConnected) {
            menu.findItem(R.id.menu_connect).setVisible(false);
            menu.findItem(R.id.menu_disconnect).setVisible(true);
        } else {
            menu.findItem(R.id.menu_connect).setVisible(true);
            menu.findItem(R.id.menu_disconnect).setVisible(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.menu_connect:
                mBluetoothLeService.connect(mDeviceAddress);
                return true;
            case R.id.menu_disconnect:
                mBluetoothLeService.disconnect();
                return true;
            case android.R.id.home:
                onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void updateConnectionState(final int resourceId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mConnectionState.setText(resourceId);
            }
        });
    }

    private void displayData(String data) {
        if (data != null) {
            mDataField.setText(data);
        }
    }

    // Demonstrates how to iterate through the supported GATT Services/Characteristics.
    // In this sample, we populate the data structure that is bound to the ExpandableListView
    // on the UI.
    private void displayGattServices(List<BluetoothGattService> gattServices) {
        if (gattServices == null) return;
        String uuid = null;
        String unknownServiceString = getResources().getString(R.string.unknown_service);
        String unknownCharaString = getResources().getString(R.string.unknown_characteristic);
        ArrayList<HashMap<String, String>> gattServiceData = new ArrayList<HashMap<String, String>>();
        ArrayList<ArrayList<HashMap<String, String>>> gattCharacteristicData
                = new ArrayList<ArrayList<HashMap<String, String>>>();
        mGattCharacteristics = new ArrayList<ArrayList<BluetoothGattCharacteristic>>();

        // Loops through available GATT Services.
        for (BluetoothGattService gattService : gattServices) {
            HashMap<String, String> currentServiceData = new HashMap<String, String>();
            uuid = gattService.getUuid().toString();
            currentServiceData.put(
                    LIST_NAME, SampleGattAttributes.lookup(uuid, unknownServiceString));
            currentServiceData.put(LIST_UUID, uuid);
            gattServiceData.add(currentServiceData);

            ArrayList<HashMap<String, String>> gattCharacteristicGroupData =
                    new ArrayList<HashMap<String, String>>();
            List<BluetoothGattCharacteristic> gattCharacteristics =
                    gattService.getCharacteristics();
            ArrayList<BluetoothGattCharacteristic> charas =
                    new ArrayList<BluetoothGattCharacteristic>();

            // Loops through available Characteristics.
            for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
                charas.add(gattCharacteristic);
                HashMap<String, String> currentCharaData = new HashMap<String, String>();
                uuid = gattCharacteristic.getUuid().toString();
                currentCharaData.put(
                        LIST_NAME, SampleGattAttributes.lookup(uuid, unknownCharaString));
                currentCharaData.put(LIST_UUID, uuid);
                gattCharacteristicGroupData.add(currentCharaData);
            }
            mGattCharacteristics.add(charas);
            gattCharacteristicData.add(gattCharacteristicGroupData);
        }

        SimpleExpandableListAdapter gattServiceAdapter = new SimpleExpandableListAdapter(
                this,
                gattServiceData,
                android.R.layout.simple_expandable_list_item_2,
                new String[] {LIST_NAME, LIST_UUID},
                new int[] { android.R.id.text1, android.R.id.text2 },
                gattCharacteristicData,
                android.R.layout.simple_expandable_list_item_2,
                new String[] {LIST_NAME, LIST_UUID},
                new int[] { android.R.id.text1, android.R.id.text2 }
        );
    }

    private View.OnTouchListener onBtnTouchListener = new View.OnTouchListener(){

        public boolean onTouch(View v, MotionEvent $e)
        {
            switch ($e.getAction())
            {
                case MotionEvent.ACTION_DOWN:
                    switch(v.getId()){
                        case R.id.imageView1:
                            if((portStatus & 0x01) == 0x01){
                                if((ledStatus & 0x01) == 0x01){
                                    iv1.setImageResource(R.drawable.zero);
                                    ledStatus = (byte) (ledStatus & (~0x01));
                                } else {
                                    iv1.setImageResource(R.drawable.one);
                                    ledStatus = (byte) (ledStatus | 0x01);
                                }
                            }
                            break;
                        case R.id.imageView2:
                            if((portStatus & 0x02) == 0x02){
                                if((ledStatus & 0x02) == 0x02){
                                    iv2.setImageResource(R.drawable.zero);
                                    ledStatus = (byte) (ledStatus & (~0x02));
                                } else {
                                    iv2.setImageResource(R.drawable.one);
                                    ledStatus = (byte) (ledStatus | 0x02);
                                }
                            }
                            break;
                        case R.id.imageView3:
                            if((portStatus & 0x04) == 0x04){
                                if((ledStatus & 0x04) == 0x04){
                                    iv3.setImageResource(R.drawable.zero);
                                    ledStatus = (byte) (ledStatus & (~0x04));
                                } else {
                                    iv3.setImageResource(R.drawable.one);
                                    ledStatus = (byte) (ledStatus | 0x04);
                                }
                            }
                            break;
                        case R.id.imageView4:
                            if((portStatus & 0x08) == 0x08){
                                if((ledStatus & 0x08) == 0x08){
                                    iv4.setImageResource(R.drawable.zero);
                                    ledStatus = (byte) (ledStatus & (~0x08));
                                } else {
                                    iv4.setImageResource(R.drawable.one);
                                    ledStatus = (byte) (ledStatus | 0x08);
                                }
                            }
                            break;
                        case R.id.imageView5:
                            if((portStatus & 0x10) == 0x10){
                                if((ledStatus & 0x10) == 0x10){
                                    iv5.setImageResource(R.drawable.zero);
                                    ledStatus = (byte) (ledStatus & (~0x10));
                                } else {
                                    iv5.setImageResource(R.drawable.one);
                                    ledStatus = (byte) (ledStatus | 0x10);
                                }
                            }
                            break;
                        case R.id.imageView6:
                            if((portStatus & 0x20) == 0x20){
                                if((ledStatus & 0x20) == 0x20){
                                    iv6.setImageResource(R.drawable.zero);
                                    ledStatus = (byte) (ledStatus & (~0x20));
                                } else {
                                    iv6.setImageResource(R.drawable.one);
                                    ledStatus = (byte) (ledStatus | 0x20);
                                }
                            }
                            break;
                        case R.id.imageView7:
                            if((portStatus & 0x40) == 0x40){
                                if((ledStatus & 0x40) == 0x40){
                                    iv7.setImageResource(R.drawable.zero);
                                    ledStatus = (byte) (ledStatus & (~0x40));
                                } else {
                                    iv7.setImageResource(R.drawable.one);
                                    ledStatus = (byte) (ledStatus | 0x40);
                                }
                            }
                            break;
                        case R.id.imageView8:
                            if((portStatus & 0x80) == 0x80){
                                if((ledStatus & 0x80) == 0x80){
                                    iv8.setImageResource(R.drawable.zero);
                                    ledStatus = (byte) (ledStatus & (~0x80));
                                } else {
                                    iv8.setImageResource(R.drawable.one);
                                    ledStatus = (byte) (ledStatus | 0x80);
                                }
                            }
                            break;
                    }
                    sendLedStatus();
                    break;
                case MotionEvent.ACTION_UP:
                    break;
                case MotionEvent.ACTION_POINTER_DOWN:
                    break;
                default:
                    break;
            }
            return false;
        }
    };

    private void setPIOImg(){
        iv1.setImageResource(R.drawable.one);
        iv2.setImageResource(R.drawable.one);
        iv3.setImageResource(R.drawable.one);
        iv4.setImageResource(R.drawable.one);
        iv5.setImageResource(R.drawable.one);
        iv6.setImageResource(R.drawable.one);
        iv7.setImageResource(R.drawable.one);
        iv8.setImageResource(R.drawable.one);

        if((ledStatus & 0x01) != 0x01)
            iv1.setImageResource(R.drawable.zero);

        if((ledStatus & 0x02) != 0x02)
            iv2.setImageResource(R.drawable.zero);

        if((ledStatus & 0x04) != 0x04)
            iv3.setImageResource(R.drawable.zero);

        if((ledStatus & 0x08) != 0x08)
            iv4.setImageResource(R.drawable.zero);

        if((ledStatus & 0x10) != 0x10)
            iv5.setImageResource(R.drawable.zero);

        if((ledStatus & 0x20) != 0x20)
            iv6.setImageResource(R.drawable.zero);

        if((ledStatus & 0x40) != 0x40)
            iv7.setImageResource(R.drawable.zero);

        if((ledStatus & 0x80) != 0x80)
            iv8.setImageResource(R.drawable.zero);
    }

    private void sendLedStatus(){
        if(mGattCharacteristics != null){
            final BluetoothGattCharacteristic characteristic = mGattCharacteristics.get(4).get(1);
            mBluetoothLeService.writeCharacteristic(characteristic, ledStatus);
            updateCommandState("Send PIO");
            displayData(bytesToHex(ledStatus));
        }
    }


    private void setPortStatus(byte status){

        if((status & 0x80) == 0x80){
            iv8.setImageResource(R.drawable.one);
            iv8.setVisibility(View.VISIBLE);
        }

        if((status & 0x40) == 0x40){
            iv7.setImageResource(R.drawable.one);
            iv7.setVisibility(View.VISIBLE);
        }

        if((status & 0x20) == 0x20){
            iv6.setImageResource(R.drawable.one);
            iv6.setVisibility(View.VISIBLE);
        }
        if((status & 0x10) == 0x10){
            iv5.setImageResource(R.drawable.one);
            iv5.setVisibility(View.VISIBLE);
        }
        if((status & 0x08) == 0x08){
            iv4.setImageResource(R.drawable.one);
            iv4.setVisibility(View.VISIBLE);
        }

        if((status & 0x04) == 0x04){
            iv3.setImageResource(R.drawable.one);
            iv3.setVisibility(View.VISIBLE);
        }

        if((status & 0x02) == 0x02){
            iv2.setImageResource(R.drawable.one);
            iv2.setVisibility(View.VISIBLE);
        }
        if((status & 0x01) == 0x01){
            iv1.setImageResource(R.drawable.one);
            iv1.setVisibility(View.VISIBLE);
        }

        Handler mHandler = new Handler();
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                // notification enable
                final BluetoothGattCharacteristic characteristic = mGattCharacteristics.get(4).get(0);
                mBluetoothLeService.setCharacteristicNotification(characteristic, true);
            }
        }, 1000);


        Handler mHandler2 = new Handler();
        mHandler2.postDelayed(new Runnable() {
            @Override
            public void run() {
                // notification enable
                final BluetoothGattCharacteristic characteristicADC0 = mGattCharacteristics.get(5).get(0);
                mBluetoothLeService.setCharacteristicNotification(characteristicADC0, true);
            }
        }, 2000);

        Handler mHandler3 = new Handler();
        mHandler3.postDelayed(new Runnable() {
            @Override
            public void run() {
                // notification enable
                final BluetoothGattCharacteristic characteristicADC1 = mGattCharacteristics.get(5).get(1);
                mBluetoothLeService.setCharacteristicNotification(characteristicADC1, true);
            }
        }, 3000);
    }

    final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();
    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 3];
        for ( int j = 0; j < bytes.length; j++ ) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 3] = hexArray[v >>> 4];
            hexChars[j * 3 + 1] = hexArray[v & 0x0F];
            hexChars[j * 3 + 2] = ' ';
        }
        return new String(hexChars);
    }

    public static String bytesToHex(byte bytedata) {
        char[] hexChars = new char[2];

        int v = bytedata & 0xFF;
        hexChars[0] = hexArray[v >>> 4];
        hexChars[1] = hexArray[v & 0x0F];

        return new String(hexChars);
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case REQUEST_WRITE_PERMISSION:
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // 권한 허가
                    // 해당 권한을 사용해서 작업을 진행할 수 있습니다
                } else {
                    // 권한 거부
                    // 사용자가 해당권한을 거부했을때 해주어야 할 동작을 수행합니다
                }
                return;
        }
    }
}
