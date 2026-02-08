import android.app.Activity;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.iclio.iotswitch2.BluetoothLeService;
import java.util.Calendar;
import java.util.List;

public class DeviceControlActivity extends AppCompatActivity {
    public static final byte CMD2_FROM_JUNG = 123;
    public static final byte CMD2_REQ_ALIVE = 124;
    public static final byte CMD2_REQ_CTR_AIRCHG = 104;
    public static final byte CMD2_REQ_CTR_AIRPWR = 105;
    public static final byte CMD2_REQ_CTR_AIRWIND = 106;
    public static final byte CMD2_REQ_CTR_CONC = 66;
    public static final byte CMD2_REQ_CTR_FANPWR = 116;
    public static final byte CMD2_REQ_CTR_FANTIME = 118;
    public static final byte CMD2_REQ_CTR_FANWIND = 115;
    public static final byte CMD2_REQ_CTR_LAMP = 65;
    public static final byte CMD2_REQ_CTR_PATT = 73;
    public static final byte CMD2_REQ_CTR_PWR = 67;
    public static final byte CMD2_REQ_CTR_TEMPCHG = 99;
    public static final byte CMD2_REQ_CTR_TEMPEXIT = 100;
    public static final byte CMD2_REQ_DEV_STS = 68;
    public static final byte CMD2_REQ_DUMMY = 111;
    public static final byte CMD2_REQ_ETC_AIRC = 103;
    public static final byte CMD2_REQ_ETC_FAN = 114;
    public static final byte CMD2_REQ_ETC_TEMP = 98;
    public static final byte CMD2_REQ_OTHER_DEV = 112;
    public static final byte CMD2_REQ_RSTS_AIRC = 102;
    public static final byte CMD2_REQ_RSTS_FAN = 113;
    public static final byte CMD2_REQ_RSTS_TEMP = 97;
    public static final byte CMD2_REQ_SELFTEST = Byte.MAX_VALUE;
    public static final byte CMD2_REQ_SETINFO = 69;
    public static final byte CMD2_REQ_SETTING = 126;
    public static final byte CMD2_REQ_SET_BANG = 74;
    public static final byte CMD2_REQ_SET_FUNC = 70;
    public static final byte CMD2_REQ_SET_SLEEP = 72;
    public static final byte CMD2_REQ_SET_WAKE = 71;
    public static final byte CMD2_REQ_SET_WATCH = 76;
    public static final byte CMD2_REQ_STS_AIRC = 107;
    public static final byte CMD2_REQ_STS_CONC = 81;
    public static final byte CMD2_REQ_STS_CONCUT = 83;
    public static final byte CMD2_REQ_STS_CONPWR = 82;
    public static final byte CMD2_REQ_STS_FAN = 117;
    public static final byte CMD2_REQ_STS_LAMP = 80;
    public static final byte CMD2_REQ_STS_PWR = 84;
    public static final byte CMD2_REQ_STS_TEMP = 101;
    public static final byte CMD2_REQ_STS_TIME1 = 87;
    public static final byte CMD2_REQ_STS_TIME2 = 88;
    public static final byte CMD2_REQ_TEST1 = 121;
    public static final byte CMD2_REQ_TIMEINFO = 75;
    public static final byte CMD2_TO_JUNG = 122;
    public static final byte CMD_REQ_CTR_CONC = 50;
    public static final byte CMD_REQ_CTR_LAMP = 49;
    public static final byte CMD_REQ_CTR_PATT = 54;
    public static final byte CMD_REQ_CTR_PWR = 51;
    public static final byte CMD_REQ_DEV_STS = 52;
    public static final byte CMD_REQ_SETINFO = 53;
    public static final byte CMD_REQ_SET_BANG = 61;
    public static final byte CMD_REQ_SET_SLEEP = 60;
    public static final byte CMD_REQ_SET_WAKE = 59;
    public static final byte CMD_REQ_SET_WATCH = 63;
    public static final byte CMD_REQ_STS_CONC = 66;
    public static final byte CMD_REQ_STS_CONCUT = 68;
    public static final byte CMD_REQ_STS_CONPWR = 67;
    public static final byte CMD_REQ_STS_LAMP = 65;
    public static final byte CMD_REQ_STS_PWR = 69;
    public static final byte CMD_REQ_SUMPWR = 125;
    public static final byte CMD_REQ_TEST1 = 121;
    public static final byte CMD_REQ_TIMEINFO = 62;
    static int ConnectTimeout = 0;
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";
    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_ROOM_NUMBER = "ROOM_NUMBER";
    private static final long SCAN_PERIOD = 10000;
    private static final int STATE_CONNECTED = 2;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_DISCONNECTED = 0;
    private static final String TAG = "MyBLE2";
    static int TxCnt = 0;
    public static boolean TxDcommSetAlarm = false;
    public static boolean TxDcommSetBang = false;
    public static boolean TxDcommSetExit = false;
    public static boolean TxDcommSetPatten = false;
    public static boolean TxDcommSetPwrAir = false;
    public static boolean TxDcommSetPwrFan = false;
    public static boolean TxDcommSetSleep = false;
    public static boolean TxDcommSetTemp = false;
    public static boolean TxDcommSetTempAir = false;
    public static boolean TxDcommSetTimeFan = false;
    public static boolean TxDcommSetWindAir = false;
    public static boolean TxDcommSetWindFan = false;
    public static Activity ctlActivity = null;
    public static boolean enableDevAircon = false;
    public static boolean enableDevFan = false;
    public static boolean enableDevTemp = false;
    public static boolean isAddTempAircon = false;
    public static boolean isCommFirstEnd = false;
    public static boolean isCommInitAircon = false;
    public static boolean isCommInitEnd = false;
    public static boolean isCommInitTemp = false;
    public static boolean isCommOk = false;
    public static boolean isDcomHexBCD = true;
    public static boolean isDcomPass = true;
    public static boolean isGovernment = false;
    public static boolean isSpeechRecognizer = true;
    public static String mDeviceAddress = null;
    public static String mRoomNumber = null;
    public static String strFanTime = "00";
    public static String strTAirCurrent = "00.0";
    public static String strTAirSetting = "00.0";
    public static String strTempCurrent = "00.0";
    public static String strTempSetting = "00.0";
    public static String strTimeAlarm1 = "0000";
    public static String strTimeAlarm2 = "0000";
    public static String strTimeAlarm3 = "0000";
    public static String strTimeSafeOff = "0000";
    public static String strTimeSafeOn = "0000";
    public static String strTimeSleep = "0000";
    public static boolean txDcommReqAirconStatus = false;
    public static boolean txDcommReqFanStatus = false;
    public static boolean txDcommReqSumPwr = false;
    public static boolean txDcommReqSwitchSet = false;
    public static boolean txDcommReqTempStatus = false;
    public static boolean txDcommReqWatt = false;
    LinearLayout activity_device_title;
    Button button;
    byte cmdBefore;
    int cntDialog;
    CommThread commThread;
    CommThreadPass commThreadPass;
    int commandIndex;
    List<BluetoothDevice> deviceList;
    int displayIndex;
    FragmentConnecting fragmentConnecting;
    FragmentControlAircon fragmentControlAircon;
    FragmentControlConvenience fragmentControlConvenience;
    FragmentControlFan fragmentControlFan;
    FragmentControlMain fragmentControlMain;
    FragmentControlPower fragmentControlPower;
    FragmentControlTemp fragmentControlTemp;
    FragmentCtlConvAlarm fragmentCtlConvAlarm;
    FragmentCtlConvSafe fragmentCtlConvSafe;
    FragmentCtlConvSleep fragmentCtlConvSleep;
    FragmenSetupSwitch fragmentSetupSwitch;
    ImageView imgV_header1;
    ImageView imgV_header2;
    boolean isBind = false;
    boolean isTxDcommDummy = false;
    char lampCount;
    char lampNumber;
    char[] lampState;
    /* access modifiers changed from: private */
    public BluetoothAdapter mBluetoothAdapter;
    /* access modifiers changed from: private */
    public BluetoothLeService mBluetoothLeService;
    /* access modifiers changed from: private */
    public boolean mConnected = false;
    private String mDeviceName;
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                boolean unused = DeviceControlActivity.this.mConnected = true;
                Log.d(DeviceControlActivity.TAG, "jyk ACTION_GATT_CONNECTED ");
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                boolean unused2 = DeviceControlActivity.this.mConnected = false;
                DeviceControlActivity.this.clearComm();
                Log.d(DeviceControlActivity.TAG, "jyk ACTION_GATT_DISCONNECTED ");
                DeviceControlActivity.this.finish();
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                Log.d(DeviceControlActivity.TAG, "jyk ACTION_GATT_SERVICES_DISCOVERED ");
                DeviceControlActivity.this.mBluetoothLeService.enableTXNotification();
                DeviceControlActivity.this.isTxDcommDummy = true;
                DeviceControlActivity.this.txDcommSetWatch = true;
                DeviceControlActivity.this.txDcommReqDeviceInfo = true;
                DeviceControlActivity.this.txDcommReqOtherDevInfo = true;
            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                BluetoothLeService unused3 = DeviceControlActivity.this.mBluetoothLeService;
                byte[] byteArrayExtra = intent.getByteArrayExtra(BluetoothLeService.EXTRA_DATA);
                if (DeviceControlActivity.isDcomPass) {
                    DeviceControlActivity.this.rxAnalyzerPass(byteArrayExtra);
                } else {
                    DeviceControlActivity.this.rxAnalyzer(byteArrayExtra);
                }
            } else if (BluetoothLeService.DEVICE_DOES_NOT_SUPPORT_UART.equals(action)) {
                DeviceControlActivity.this.finish();
            } else {
                Log.d(DeviceControlActivity.TAG, "jyk ACTION_DATA etc ");
            }
        }
    };
    /* access modifiers changed from: private */
    public BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {
        public void onLeScan(BluetoothDevice bluetoothDevice, int i, byte[] bArr) {
            int i2 = bArr[0] + 1;
            int i3 = bArr[i2] + 1;
            String str = "";
            String str2 = str;
            for (int i4 = 0; i4 < i3; i4++) {
                StringBuilder sb = new StringBuilder();
                sb.append(str2);
                int i5 = i2 + i4;
                sb.append(String.format("%02x", new Object[]{Byte.valueOf(bArr[i5])}));
                str2 = sb.toString();
                if (i4 > 1) {
                    str = str + ((char) bArr[i5]);
                }
            }
            if (str.equals("Clio_UART.")) {
                Log.d(DeviceControlActivity.TAG, "===>Register device=" + bluetoothDevice.getName() + " tmp2=" + str);
                if (DeviceControlActivity.this.mScanning) {
                    DeviceControlActivity.this.mBluetoothAdapter.stopLeScan(DeviceControlActivity.this.mLeScanCallback);
                    boolean unused = DeviceControlActivity.this.mScanning = false;
                    if (bluetoothDevice != null) {
                        int parseInt = Integer.parseInt(DeviceControlActivity.mRoomNumber);
                        String address = bluetoothDevice.getAddress();
                        boolean z = true;
                        for (int i6 = 0; i6 < 6; i6++) {
                            if (address.equals(MainActivity.strArrRoomInfo[i6][2])) {
                                if (i6 == parseInt - 1) {
                                    Log.d(DeviceControlActivity.TAG, "Err:This is impossibleness !!!");
                                } else {
                                    Log.d(DeviceControlActivity.TAG, "Warnig: Already Registered");
                                    Toast.makeText(DeviceControlActivity.this.getApplicationContext(), "이미 등록되어 있습니다", 1).show();
                                    DeviceControlActivity.this.finish();
                                    z = false;
                                }
                            }
                        }
                        if (z) {
                            Log.d(DeviceControlActivity.TAG, "Register OK. selectAddr=" + address);
                            int i7 = parseInt - 1;
                            MainActivity.strArrRoomInfo[i7][1] = bluetoothDevice.getName();
                            MainActivity.strArrRoomInfo[i7][2] = bluetoothDevice.getAddress();
                            DeviceControlActivity.this.finish();
                        }
                    }
                }
            }
        }
    };
    DialogInterface mPopupSelfTest1 = null;
    /* access modifiers changed from: private */
    public boolean mScanning;
    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            BluetoothLeService unused = DeviceControlActivity.this.mBluetoothLeService = ((BluetoothLeService.LocalBinder) iBinder).getService();
            if (!DeviceControlActivity.this.mBluetoothLeService.initialize()) {
                Log.e(DeviceControlActivity.TAG, "Unable to initialize Bluetooth");
                DeviceControlActivity.this.finish();
            }
            DeviceControlActivity.this.mBluetoothLeService.connect(DeviceControlActivity.mDeviceAddress);
        }

        public void onServiceDisconnected(ComponentName componentName) {
            BluetoothLeService unused = DeviceControlActivity.this.mBluetoothLeService = null;
        }
    };
    ProgressDialog progressDialog = null;
    int stepSelfTest;
    String strErrCode;
    String strPowerVal = "0000";
    String strVerBLE = "";
    String strVerSwitch = "";
    String str_watt;
    TextView textV_header1;
    TextView textV_header2;
    TextView textV_header3;
    CountDownTimer timerDialog;
    Menu titleMenu;
    boolean txDcommReqDeviceInfo = false;
    boolean txDcommReqOtherDevInfo = false;
    boolean txDcommReqSelfTest = false;
    boolean txDcommReqTimeInfo = false;
    boolean txDcommSetLamp = false;
    boolean txDcommSetWatch = false;
    boolean txDcommTest1 = false;
    int txRetryCnt;
    int wattSum = 0;

    /* access modifiers changed from: protected */
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        setContentView((int) R.layout.activity_device_control);
        Log.d(TAG, "DeviceControlActivity onCreate()");
        ctlActivity = this;
        LinearLayout linearLayout = (LinearLayout) findViewById(R.id.activity_device_title);
        this.activity_device_title = linearLayout;
        linearLayout.setVisibility(8);
        this.textV_header1 = (TextView) findViewById(R.id.textV_header1);
        this.textV_header2 = (TextView) findViewById(R.id.textV_header2);
        this.textV_header3 = (TextView) findViewById(R.id.textV_header3);
        this.imgV_header1 = (ImageView) findViewById(R.id.imgV_header1);
        this.imgV_header2 = (ImageView) findViewById(R.id.imgV_header2);
        this.imgV_header1.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                Log.d(DeviceControlActivity.TAG, "imgV_header1 click");
            }
        });
        this.imgV_header1.setOnLongClickListener(new View.OnLongClickListener() {
            public boolean onLongClick(View view) {
                Log.d(DeviceControlActivity.TAG, "imgV_header1 Long click");
                return true;
            }
        });
        if (MainActivity.isTablet) {
            float dimension = getResources().getDimension(R.dimen.text_title_size) * MainActivity.fontSizeTable;
            this.textV_header1.setTextSize(dimension);
            this.textV_header2.setTextSize(dimension);
            this.textV_header3.setTextSize(dimension);
        }
        this.lampState = new char[4];
        if (!MainActivity.isDisplayTest) {
            registerReceiver(this.mGattUpdateReceiver, makeGattUpdateIntentFilter());
        }
        Intent intent = getIntent();
        this.mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);
        mRoomNumber = intent.getStringExtra(EXTRAS_ROOM_NUMBER);
        if (MainActivity.isDisplayTest || !mDeviceAddress.equals("0")) {
            if (!MainActivity.isDisplayTest) {
                bindService(new Intent(this, BluetoothLeService.class), this.mServiceConnection, 1);
                this.isBind = true;
                isCommOk = true;
                ConnectTimeout = 0;
                if (isDcomPass) {
                    CommThreadPass commThreadPass2 = new CommThreadPass();
                    this.commThreadPass = commThreadPass2;
                    commThreadPass2.start();
                } else {
                    CommThread commThread2 = new CommThread();
                    this.commThread = commThread2;
                    commThread2.start();
                }
            }
            this.fragmentConnecting = new FragmentConnecting();
            this.fragmentControlMain = new FragmentControlMain();
            this.fragmentControlPower = new FragmentControlPower();
            this.fragmentControlConvenience = new FragmentControlConvenience();
            this.fragmentCtlConvAlarm = new FragmentCtlConvAlarm();
            this.fragmentCtlConvSleep = new FragmentCtlConvSleep();
            this.fragmentCtlConvSafe = new FragmentCtlConvSafe();
            this.fragmentControlTemp = new FragmentControlTemp();
            this.fragmentControlAircon = new FragmentControlAircon();
            this.fragmentControlFan = new FragmentControlFan();
            this.fragmentSetupSwitch = new FragmenSetupSwitch();
            if (MainActivity.isDisplayTest) {
                isCommInitEnd = true;
                this.lampCount = '2';
                char[] cArr = this.lampState;
                cArr[0] = '0';
                cArr[1] = '0';
                cArr[2] = '0';
                cArr[3] = '0';
                FragmentControlAircon.stepWind = 0;
                this.strPowerVal = "0567";
                isAddTempAircon = true;
                enableDevTemp = true;
                enableDevAircon = true;
                enableDevFan = true;
                strTempCurrent = "25.0";
                strTempSetting = "27.0";
                strTAirCurrent = "23";
                strTAirSetting = "21";
                strFanTime = "30";
                this.strVerSwitch = "10 Jun 21 2017";
                this.strVerBLE = "20 Jun 22 2017";
                this.stepSelfTest = 0;
                onFragmentChanged(R.integer.Display_Connecting);
                return;
            }
            onFragmentChanged(R.integer.Display_Connecting);
            enableDevTemp = false;
            enableDevAircon = false;
            isAddTempAircon = false;
            enableDevFan = false;
            this.stepSelfTest = 0;
            return;
        }
        Log.d(TAG, "Return.... mDeviceAddress=" + mDeviceAddress);
        this.fragmentConnecting = new FragmentConnecting();
        onFragmentChanged(R.integer.Display_Connecting);
        BluetoothAdapter adapter = ((BluetoothManager) getSystemService("bluetooth")).getAdapter();
        this.mBluetoothAdapter = adapter;
        adapter.startLeScan(this.mLeScanCallback);
        this.mScanning = true;
    }

    public void onFragmentChanged(int i) {
        this.displayIndex = i;
        String str = MainActivity.strArrRoomInfo[Integer.parseInt(mRoomNumber) - 1][0];
        Log.d(TAG, "onFragmentChanged() index=" + i);
        switch (i) {
            case R.integer.Display_Connecting:
                getSupportFragmentManager().beginTransaction().replace(R.id.container1, this.fragmentConnecting).commit();
                this.textV_header1.setVisibility(4);
                this.textV_header2.setVisibility(4);
                this.textV_header3.setVisibility(4);
                this.imgV_header1.setVisibility(4);
                this.imgV_header2.setVisibility(4);
                this.activity_device_title.setVisibility(8);
                return;
            case R.integer.Display_CtlAircon:
                getSupportFragmentManager().beginTransaction().replace(R.id.container1, this.fragmentControlAircon).commit();
                this.textV_header1.setText(str);
                this.textV_header2.setText("냉방");
                this.textV_header3.setVisibility(8);
                this.imgV_header2.setVisibility(8);
                return;
            case R.integer.Display_CtlConvAlarm:
                getSupportFragmentManager().beginTransaction().replace(R.id.container1, this.fragmentCtlConvAlarm).commit();
                this.textV_header1.setText(str);
                this.textV_header2.setText("편의기능");
                this.textV_header3.setText("알람");
                this.textV_header3.setVisibility(0);
                this.imgV_header2.setVisibility(0);
                return;
            case R.integer.Display_CtlConvMain:
                getSupportFragmentManager().beginTransaction().replace(R.id.container1, this.fragmentControlConvenience).commit();
                this.textV_header1.setText(str);
                this.textV_header2.setText("편의기능");
                this.textV_header3.setVisibility(8);
                this.imgV_header2.setVisibility(8);
                return;
            case R.integer.Display_CtlConvSafe:
                getSupportFragmentManager().beginTransaction().replace(R.id.container1, this.fragmentCtlConvSafe).commit();
                this.textV_header1.setText(str);
                this.textV_header2.setText("편의기능");
                this.textV_header3.setText("방범");
                this.textV_header3.setVisibility(0);
                this.imgV_header2.setVisibility(0);
                return;
            case R.integer.Display_CtlConvSleep:
                getSupportFragmentManager().beginTransaction().replace(R.id.container1, this.fragmentCtlConvSleep).commit();
                this.textV_header1.setText(str);
                this.textV_header2.setText("편의기능");
                this.textV_header3.setText("취침");
                this.textV_header3.setVisibility(0);
                this.imgV_header2.setVisibility(0);
                return;
            case R.integer.Display_CtlFan:
                getSupportFragmentManager().beginTransaction().replace(R.id.container1, this.fragmentControlFan).commit();
                this.textV_header1.setText(str);
                this.textV_header2.setText("환기");
                this.textV_header3.setVisibility(8);
                this.imgV_header2.setVisibility(8);
                return;
            case R.integer.Display_CtlMain:
                getSupportFragmentManager().beginTransaction().replace(R.id.container1, this.fragmentControlMain).commit();
                this.textV_header1.setText(str);
                this.textV_header2.setText("조명");
                this.textV_header1.setVisibility(0);
                this.textV_header2.setVisibility(0);
                this.textV_header3.setVisibility(8);
                this.imgV_header1.setVisibility(0);
                this.imgV_header2.setVisibility(8);
                this.activity_device_title.setVisibility(0);
                this.titleMenu.findItem(R.id.action_back).setVisible(true);
                this.titleMenu.findItem(R.id.action_home).setVisible(true);
                return;
            case R.integer.Display_CtlPower:
                getSupportFragmentManager().beginTransaction().replace(R.id.container1, this.fragmentControlPower).commit();
                this.textV_header1.setText(str);
                this.textV_header2.setText("전력");
                this.textV_header3.setVisibility(8);
                this.imgV_header2.setVisibility(8);
                txDcommReqWatt = true;
                return;
            case R.integer.Display_CtlTemp:
                getSupportFragmentManager().beginTransaction().replace(R.id.container1, this.fragmentControlTemp).commit();
                this.textV_header1.setText(str);
                this.textV_header2.setText("난방");
                this.textV_header3.setVisibility(8);
                this.imgV_header2.setVisibility(8);
                return;
            case R.integer.Display_SetupSwitch:
                getSupportFragmentManager().beginTransaction().replace(R.id.container1, this.fragmentSetupSwitch).commit();
                this.textV_header1.setText(str);
                this.textV_header2.setText("스위치설정");
                return;
            default:
                return;
        }
    }

    public void onBackPressed() {
        Log.d(TAG, "jyk onBackPressed");
        switch (this.displayIndex) {
            case R.integer.Display_Connecting:
                if (MainActivity.isDisplayTest) {
                    FragmentConnecting.timer.cancel();
                }
                super.onBackPressed();
                return;
            case R.integer.Display_CtlAircon:
            case R.integer.Display_CtlFan:
            case R.integer.Display_CtlTemp:
            case R.integer.Display_SetupSwitch:
                onFragmentChanged(R.integer.Display_CtlMain);
                return;
            case R.integer.Display_CtlConvAlarm:
                onFragmentChanged(R.integer.Display_CtlConvMain);
                return;
            case R.integer.Display_CtlConvMain:
                onFragmentChanged(R.integer.Display_CtlMain);
                return;
            case R.integer.Display_CtlConvSafe:
                onFragmentChanged(R.integer.Display_CtlConvMain);
                return;
            case R.integer.Display_CtlConvSleep:
                onFragmentChanged(R.integer.Display_CtlConvMain);
                TxDcommSetSleep = true;
                return;
            case R.integer.Display_CtlMain:
                super.onBackPressed();
                return;
            case R.integer.Display_CtlPower:
                onFragmentChanged(R.integer.Display_CtlMain);
                return;
            default:
                return;
        }
    }

    /* access modifiers changed from: protected */
    public void onResume() {
        super.onResume();
        if (!MainActivity.isDisplayTest) {
            registerReceiver(this.mGattUpdateReceiver, makeGattUpdateIntentFilter());
            Log.d(TAG, "DeviceControlActivity onResume() mConnected=" + this.mConnected + " isCommOk=" + isCommOk + " mBluetoothLeService=" + this.mBluetoothLeService);
            BluetoothLeService bluetoothLeService = this.mBluetoothLeService;
            if (bluetoothLeService != null) {
                int stateConnect = bluetoothLeService.stateConnect();
                if (stateConnect == 0) {
                    Log.d(TAG, "state = STATE_DISCONNECTED");
                    finish();
                } else if (stateConnect == 1) {
                    Log.d(TAG, "state = STATE_CONNECTING");
                } else if (stateConnect == 2) {
                    Log.d(TAG, "state = STATE_CONNECTED");
                }
            }
        }
    }

    /* access modifiers changed from: protected */
    public void onPause() {
        super.onPause();
        if (!MainActivity.isDisplayTest) {
            unregisterReceiver(this.mGattUpdateReceiver);
        }
    }

    /* access modifiers changed from: protected */
    public void onDestroy() {
        super.onDestroy();
        if (!MainActivity.isDisplayTest) {
            if (this.mScanning) {
                this.mScanning = false;
                this.mBluetoothAdapter.stopLeScan(this.mLeScanCallback);
            }
            if (!mDeviceAddress.equals("0") && this.isBind) {
                unbindService(this.mServiceConnection);
            }
            this.mBluetoothLeService = null;
            FragmentConnecting.timer.cancel();
            clearComm();
            Log.d(TAG, "DeviceControlActivity onDestroy()");
        }
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        Log.d(TAG, "DeviceControlActivity onCreateOptionsMenu ");
        this.titleMenu = menu;
        menu.findItem(R.id.action_back).setVisible(false);
        this.titleMenu.findItem(R.id.action_home).setVisible(false);
        if (!isAddTempAircon) {
            this.titleMenu.findItem(R.id.action_selfTest).setVisible(false);
        }
        return super.onCreateOptionsMenu(menu);
    }

    public boolean onOptionsItemSelected(MenuItem menuItem) {
        if (isAddTempAircon) {
            this.titleMenu.findItem(R.id.action_selfTest).setVisible(true);
        }
        switch (menuItem.getItemId()) {
            case R.id.action_back:
                Log.d(TAG, "onOptionsItemSelected(back) ");
                onBackPressed();
                return true;
            case R.id.action_end:
                Log.d(TAG, "onOptionsItemSelected(end) ");
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle((CharSequence) "프로그램 종료");
                builder.setMessage((CharSequence) "프로그램을 종료하겠습니까?").setCancelable(false).setPositiveButton((CharSequence) "종료", (DialogInterface.OnClickListener) new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialogInterface, int i) {
                        ((MainActivity) MainActivity.mainActivity).finish();
                        DeviceControlActivity.this.finish();
                    }
                }).setNegativeButton((CharSequence) "취소", (DialogInterface.OnClickListener) new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialogInterface, int i) {
                        dialogInterface.cancel();
                    }
                });
                builder.create().show();
                return true;
            case R.id.action_home:
                Log.d(TAG, "onOptionsItemSelected(home) ");
                finish();
                return true;
            case R.id.action_info:
                Log.d(TAG, "onOptionsItemSelected(info) ");
                String str = MainActivity.strVerName;
                String string = getString(R.string.Compile_IotSwitch);
                AlertDialog.Builder builder2 = new AlertDialog.Builder(this);
                builder2.setTitle((CharSequence) "정보");
                builder2.setMessage((CharSequence) "앱버전: " + str.substring(0, 1) + "." + str.substring(1, 2) + "." + str.substring(2, 3) + " " + string + "\n스위치(Main)버전: " + this.strVerSwitch.substring(0, 1) + "." + this.strVerSwitch.substring(1, 2) + " " + this.strVerSwitch.substring(2) + "\n스위치(BLE)버전: " + this.strVerBLE.substring(0, 1) + "." + this.strVerBLE.substring(1, 2) + " " + this.strVerBLE.substring(2));
                builder2.show();
                return true;
            case R.id.action_selfTest:
                this.txDcommReqSelfTest = true;
                this.cntDialog = 0;
                AnonymousClass8 r4 = new CountDownTimer(5000, 1000) {
                    public void onTick(long j) {
                        DeviceControlActivity deviceControlActivity = DeviceControlActivity.this;
                        int i = deviceControlActivity.cntDialog + 1;
                        deviceControlActivity.cntDialog = i;
                        if (i >= 1 && DeviceControlActivity.this.stepSelfTest > 1) {
                            if (DeviceControlActivity.this.progressDialog != null) {
                                DeviceControlActivity.this.progressDialog.cancel();
                            }
                            AlertDialog.Builder builder = new AlertDialog.Builder(DeviceControlActivity.this);
                            builder.setTitle((CharSequence) "[고장진단]");
                            if (DeviceControlActivity.this.strErrCode.equals("EL01")) {
                                builder.setMessage((CharSequence) "에러코드: EL01 \n온도밸브 제어기 통신에러 \n해당 디바이스의 점검이 필요합니다");
                            } else if (DeviceControlActivity.this.strErrCode.equals("BL01")) {
                                builder.setMessage((CharSequence) "에러코드: BL01 \n보일러 통신에러 \n해당 디바이스의 점검이 필요합니다");
                            } else if (DeviceControlActivity.this.strErrCode.equals("")) {
                                builder.setMessage((CharSequence) "에러코드: 없음 \n정상적으로 동작중입니다");
                            } else {
                                builder.setMessage((CharSequence) "에러코드: " + DeviceControlActivity.this.strErrCode + "\n정의되지않음 \n해당 디바이스의 점검이 필요합니다");
                            }
                            DeviceControlActivity.this.mPopupSelfTest1 = builder.show();
                            DeviceControlActivity.this.timerDialog.cancel();
                            DeviceControlActivity.this.cntDialog = 0;
                        }
                    }

                    public void onFinish() {
                        DeviceControlActivity.this.cntDialog = 0;
                        Log.d(DeviceControlActivity.TAG, "-->Dialog CountDownTimer onFinish()");
                        if (DeviceControlActivity.this.progressDialog != null) {
                            DeviceControlActivity.this.progressDialog.cancel();
                        }
                        AlertDialog.Builder builder = new AlertDialog.Builder(DeviceControlActivity.this);
                        builder.setTitle((CharSequence) "[고장진단]");
                        builder.setMessage((CharSequence) "응답하지 않습니다 \n잠시후 다시 시도하세요");
                        DeviceControlActivity.this.mPopupSelfTest1 = builder.show();
                    }
                };
                this.timerDialog = r4;
                r4.start();
                Log.d(TAG, "-->Dialog CountDownTimer start()");
                ProgressDialog progressDialog2 = new ProgressDialog(this);
                this.progressDialog = progressDialog2;
                progressDialog2.setTitle("[고장진단]");
                this.progressDialog.setMessage("고장진단 진행중입니다\n잠시만 기다리세요");
                this.progressDialog.show();
                this.stepSelfTest = 1;
                return true;
            case R.id.action_settings:
                Log.d(TAG, "onOptionsItemSelected(setting) ");
                return true;
            default:
                return super.onOptionsItemSelected(menuItem);
        }
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        intentFilter.addAction(BluetoothLeService.DEVICE_DOES_NOT_SUPPORT_UART);
        return intentFilter;
    }

    /* access modifiers changed from: private */
    public void clearComm() {
        this.txDcommSetWatch = false;
        this.txDcommReqDeviceInfo = false;
        txDcommReqWatt = false;
        this.txDcommReqTimeInfo = false;
        this.txDcommSetLamp = false;
        this.txDcommReqSelfTest = false;
        txDcommReqSwitchSet = false;
        this.txDcommReqOtherDevInfo = false;
        TxDcommSetAlarm = false;
        TxDcommSetBang = false;
        TxDcommSetSleep = false;
        TxDcommSetPatten = false;
        txDcommReqTempStatus = false;
        TxDcommSetTemp = false;
        TxDcommSetExit = false;
        txDcommReqAirconStatus = false;
        TxDcommSetTempAir = false;
        TxDcommSetPwrAir = false;
        TxDcommSetWindAir = false;
        txDcommReqFanStatus = false;
        TxDcommSetTimeFan = false;
        TxDcommSetPwrFan = false;
        TxDcommSetWindFan = false;
        this.isTxDcommDummy = false;
        isCommOk = false;
        isCommInitEnd = false;
        isCommInitTemp = false;
        isCommInitAircon = false;
        TxCnt = 0;
        isCommFirstEnd = false;
    }

    /* access modifiers changed from: private */
    public void rxAnalyzerPass(byte[] bArr) {
        int i;
        char[] cArr = new char[20];
        byte b = 0;
        byte b2 = bArr[0];
        byte b3 = bArr[1];
        byte b4 = bArr[2];
        byte b5 = bArr[3];
        byte b6 = bArr[4];
        if (b2 == 126 && b3 == 16 && b4 == 15) {
            byte b7 = 0;
            byte b8 = 0;
            byte b9 = 0;
            while (true) {
                i = b6 + 5;
                if (b7 >= i) {
                    break;
                }
                byte b10 = bArr[b7];
                b9 = (byte) (b9 ^ b10);
                b8 = (byte) (b8 + b10);
                b7 = (byte) (b7 + 1);
            }
            byte b11 = (byte) (b8 + b9);
            if (b9 == bArr[i] && b11 == bArr[b6 + 6]) {
                for (byte b12 = 0; b12 < b6; b12 = (byte) (b12 + 1)) {
                    cArr[b12] = (char) (bArr[b12 + 5] & 255);
                }
                this.cmdBefore = 0;
                if (b5 == 49) {
                    this.txDcommSetLamp = false;
                    Log.d(TAG, "txDcommSetLamp clear ");
                } else if (b5 == 54) {
                    TxDcommSetPatten = false;
                    Log.d(TAG, "TxDcommSetPatten clear ");
                } else if (b5 == 97) {
                    txDcommReqTempStatus = false;
                    Log.d(TAG, "txDcommReqTempStatus clear ");
                } else if (b5 == 121) {
                    this.txDcommTest1 = false;
                    Log.d(TAG, "txDcommTest1 clear ");
                    Log.d(TAG, "cmd=" + Integer.toHexString(b5) + " len=" + b6 + " data=" + Integer.toHexString(cArr[0]) + Integer.toHexString(cArr[1]) + Integer.toHexString(cArr[2]) + Integer.toHexString(cArr[3]));
                } else if (b5 != 51) {
                    String str = "";
                    if (b5 == 52) {
                        this.txDcommReqDeviceInfo = false;
                        Log.d(TAG, "2)txDcommReqDeviceInfo clear ");
                        this.strVerBLE = str;
                        if (b6 == 0) {
                            this.strVerBLE = "00";
                            Log.d(TAG, "IoT Switch Version(BLE) : 이전버전 V" + this.strVerSwitch + " len=" + b6);
                        } else if (b6 == 1) {
                            this.strVerBLE += cArr[0];
                            Log.d(TAG, "IoT Switch Version(BLE) : 이전버전 V" + this.strVerBLE + " len=" + b6);
                            this.strVerBLE = "00";
                        } else {
                            while (b < b6) {
                                if (b == 2) {
                                    this.strVerBLE += " ";
                                }
                                this.strVerBLE += cArr[b];
                                b = (byte) (b + 1);
                            }
                            Log.d(TAG, "IoT Switch Version(BLE) : V" + this.strVerBLE + " len=" + b6);
                        }
                    } else if (b5 == 112) {
                        Log.d(TAG, "txDcommReqOtherDevInfo clear ");
                        this.txDcommReqOtherDevInfo = false;
                        for (byte b13 = 0; b13 < b6; b13 = (byte) (b13 + 1)) {
                            str = str + Integer.toHexString(cArr[b13]);
                        }
                        Log.d(TAG, "Rx(CMD2_REQ_OTHER_DEV): " + str);
                        if (b6 < 6) {
                            Log.d(TAG, "Data invalid length=" + b6);
                            enableDevTemp = false;
                            enableDevAircon = false;
                            isAddTempAircon = false;
                            return;
                        }
                        char c = cArr[0];
                        if (c > 4) {
                            this.lampCount = '4';
                        } else {
                            this.lampCount = (char) (c + '0');
                        }
                        Log.d(TAG, "lampCount=" + this.lampCount);
                        if (cArr[2] == 1) {
                            enableDevTemp = true;
                        } else {
                            enableDevTemp = false;
                        }
                        if (cArr[3] == 1) {
                            enableDevAircon = true;
                        } else {
                            enableDevAircon = false;
                        }
                        if (cArr[4] == 1) {
                            enableDevFan = true;
                        } else {
                            enableDevFan = false;
                        }
                        if (enableDevAircon || enableDevTemp || enableDevFan) {
                            isAddTempAircon = true;
                        }
                        if (b6 > 6) {
                            Log.d(TAG, "Switch Model = " + Integer.toHexString(cArr[6]));
                        }
                        isCommFirstEnd = true;
                        onFragmentChanged(R.integer.Display_CtlMain);
                    } else if (b5 != 113) {
                        switch (b5) {
                            case CMD_REQ_SET_WAKE:
                                TxDcommSetAlarm = false;
                                Log.d(TAG, "TxDcommSetSetAlarm clear ");
                                return;
                            case 60:
                                TxDcommSetSleep = false;
                                Log.d(TAG, "TxDcommSetSleep clear ");
                                return;
                            case 61:
                                TxDcommSetBang = false;
                                Log.d(TAG, "TxDcommSetBang clear ");
                                return;
                            case 62:
                                this.txDcommReqTimeInfo = false;
                                Log.d(TAG, "4)txDcommReqTimeInfo clear ");
                                if (enableDevTemp) {
                                    txDcommReqTempStatus = true;
                                } else if (enableDevAircon) {
                                    txDcommReqAirconStatus = true;
                                } else if (enableDevFan) {
                                    txDcommReqFanStatus = true;
                                }
                                isCommInitEnd = true;
                                Log.d(TAG, "5)IoT Switch InitComm End ");
                                for (byte b14 = 0; b14 < 12; b14 = (byte) (b14 + 1)) {
                                    str = str + Integer.toHexString(cArr[b14]);
                                }
                                Log.d(TAG, "Rx(CMD_REQ_TIMEINFO): " + str);
                                strTimeAlarm1 = String.format("%01x", new Object[]{Integer.valueOf(cArr[0] >> 4)});
                                strTimeAlarm1 += String.format("%01x", new Object[]{Integer.valueOf(cArr[0] & 15)});
                                strTimeAlarm1 += String.format("%01x", new Object[]{Integer.valueOf(cArr[1] >> 4)});
                                strTimeAlarm1 += String.format("%01x", new Object[]{Integer.valueOf(cArr[1] & 15)});
                                strTimeAlarm2 = String.format("%01x", new Object[]{Integer.valueOf(cArr[2] >> 4)});
                                strTimeAlarm2 += String.format("%01x", new Object[]{Integer.valueOf(cArr[2] & 15)});
                                strTimeAlarm2 += String.format("%01x", new Object[]{Integer.valueOf(cArr[3] >> 4)});
                                strTimeAlarm2 += String.format("%01x", new Object[]{Integer.valueOf(cArr[3] & 15)});
                                strTimeAlarm3 = String.format("%01x", new Object[]{Integer.valueOf(cArr[4] >> 4)});
                                strTimeAlarm3 += String.format("%01x", new Object[]{Integer.valueOf(cArr[4] & 15)});
                                strTimeAlarm3 += String.format("%01x", new Object[]{Integer.valueOf(cArr[5] >> 4)});
                                strTimeAlarm3 += String.format("%01x", new Object[]{Integer.valueOf(cArr[5] & 15)});
                                strTimeSleep = String.format("%01x", new Object[]{Integer.valueOf(cArr[6] >> 4)});
                                strTimeSleep += String.format("%01x", new Object[]{Integer.valueOf(cArr[6] & 15)});
                                strTimeSleep += String.format("%01x", new Object[]{Integer.valueOf(cArr[7] >> 4)});
                                strTimeSleep += String.format("%01x", new Object[]{Integer.valueOf(cArr[7] & 15)});
                                strTimeSafeOn = String.format("%01x", new Object[]{Integer.valueOf(cArr[8] >> 4)});
                                strTimeSafeOn += String.format("%01x", new Object[]{Integer.valueOf(cArr[8] & 15)});
                                strTimeSafeOn += String.format("%01x", new Object[]{Integer.valueOf(cArr[9] >> 4)});
                                strTimeSafeOn += String.format("%01x", new Object[]{Integer.valueOf(cArr[9] & 15)});
                                strTimeSafeOff = String.format("%01x", new Object[]{Integer.valueOf(cArr[10] >> 4)});
                                strTimeSafeOff += String.format("%01x", new Object[]{Integer.valueOf(cArr[10] & 15)});
                                strTimeSafeOff += String.format("%01x", new Object[]{Integer.valueOf(cArr[11] >> 4)});
                                strTimeSafeOff += String.format("%01x", new Object[]{Integer.valueOf(cArr[11] & 15)});
                                if (FragmentCtlConvAlarm.AlarmNew) {
                                    if (strTimeAlarm1.equals("0000")) {
                                        FragmentCtlConvAlarm.select1 = false;
                                    } else {
                                        FragmentCtlConvAlarm.select1 = true;
                                    }
                                    if (strTimeAlarm2.equals("0000")) {
                                        FragmentCtlConvAlarm.select2 = false;
                                    } else {
                                        FragmentCtlConvAlarm.select2 = true;
                                    }
                                    if (strTimeAlarm3.equals("0000")) {
                                        FragmentCtlConvAlarm.select3 = false;
                                    } else {
                                        FragmentCtlConvAlarm.select3 = true;
                                    }
                                    if (FragmentCtlConvAlarm.select1 || FragmentCtlConvAlarm.select2 || FragmentCtlConvAlarm.select3) {
                                        FragmentCtlConvAlarm.enableAlarm = true;
                                    } else {
                                        FragmentCtlConvAlarm.enableAlarm = false;
                                    }
                                } else if (!strTimeAlarm1.equals("0000") || !strTimeAlarm2.equals("0000") || !strTimeAlarm3.equals("0000")) {
                                    FragmentCtlConvAlarm.enableAlarm = true;
                                } else {
                                    FragmentCtlConvAlarm.enableAlarm = false;
                                }
                                if (strTimeSleep.equals("0000")) {
                                    FragmentCtlConvSleep.enableSleep = false;
                                } else {
                                    FragmentCtlConvSleep.enableSleep = true;
                                }
                                if (!strTimeSafeOn.equals("0000") || !strTimeSafeOff.equals("0000")) {
                                    FragmentCtlConvSafe.enableSafe = true;
                                } else {
                                    FragmentCtlConvSafe.enableSafe = false;
                                }
                                Log.d(TAG, "방범시간: " + strTimeSafeOn + " " + strTimeSafeOff);
                                Log.d(TAG, "알람시간: " + strTimeAlarm1 + " " + strTimeAlarm2 + " " + strTimeAlarm3 + " 취침시간: " + strTimeSleep + "방범시간: " + strTimeSafeOn + " " + strTimeSafeOff);
                                return;
                            case 63:
                                this.txDcommSetWatch = false;
                                TxCnt = 0;
                                Log.d(TAG, "1)txDcommSetWatch clear ");
                                this.strVerSwitch = str;
                                if (b6 == 0) {
                                    this.strVerSwitch = "00";
                                    Log.d(TAG, "IoT Switch Version(ST) : 이전버전 V" + this.strVerSwitch + " len=" + b6);
                                } else if (b6 == 1) {
                                    this.strVerSwitch += cArr[0];
                                    Log.d(TAG, "IoT Switch Version(ST) : 이전버전 V" + this.strVerSwitch + " len=" + b6);
                                    this.strVerSwitch = "00";
                                } else {
                                    while (b < b6) {
                                        if (b == 1) {
                                            this.strVerSwitch += " ";
                                        }
                                        if (b == 0) {
                                            this.strVerSwitch += ((char) ((cArr[b] >> 4) + 48));
                                            this.strVerSwitch += ((char) ((cArr[b] & 15) + '0'));
                                        } else {
                                            this.strVerSwitch += cArr[b];
                                        }
                                        b = (byte) (b + 1);
                                    }
                                    Log.d(TAG, "IoT Switch Version(ST) : V" + this.strVerSwitch + " len=" + b6);
                                }
                                saveDeviceList();
                                return;
                            default:
                                switch (b5) {
                                    case 65:
                                        for (byte b15 = 0; b15 < 5; b15 = (byte) (b15 + 1)) {
                                            str = str + Integer.toHexString(cArr[b15]);
                                        }
                                        Log.d(TAG, "Rx(CMD2_REQ_STS_LAMP): " + str);
                                        this.lampCount = (char) (cArr[0] + '0');
                                        byte b16 = 0;
                                        while (b16 < 4) {
                                            int i2 = b16 + 1;
                                            this.lampState[b16] = (char) (cArr[i2] + '0');
                                            b16 = (byte) i2;
                                        }
                                        cArr[0] = 1;
                                        MakeTxFramePass((byte) 65, (byte) 1, cArr);
                                        if (this.displayIndex == R.integer.Display_CtlMain) {
                                            this.fragmentControlMain.lampDisplay();
                                        }
                                        if (this.displayIndex == R.integer.Display_Connecting) {
                                            enableDevTemp = false;
                                            enableDevAircon = false;
                                            isAddTempAircon = false;
                                            enableDevFan = false;
                                            isCommFirstEnd = true;
                                            onFragmentChanged(R.integer.Display_CtlMain);
                                            Log.d(TAG, "jyk change");
                                            return;
                                        }
                                        return;
                                    case 66:
                                        MakeTxFramePass((byte) 66, (byte) 1, cArr);
                                        return;
                                    case 67:
                                        MakeTxFramePass((byte) 67, (byte) 1, cArr);
                                        return;
                                    case 68:
                                        MakeTxFramePass((byte) 68, (byte) 1, cArr);
                                        if (!isCommInitEnd) {
                                            txDcommReqWatt = true;
                                            return;
                                        }
                                        return;
                                    case 69:
                                        String str2 = str;
                                        for (byte b17 = 0; b17 < 8; b17 = (byte) (b17 + 1)) {
                                            str2 = str2 + Integer.toHexString(cArr[b17]);
                                        }
                                        Log.d(TAG, "Rx(CMD_REQ_STS_PWR): " + str2);
                                        this.strPowerVal = str;
                                        this.strPowerVal = String.format("%01x", new Object[]{Integer.valueOf(cArr[4] >> 4)});
                                        this.strPowerVal += String.format("%01x", new Object[]{Integer.valueOf(cArr[4] & 15)});
                                        this.strPowerVal += String.format("%01x", new Object[]{Integer.valueOf(cArr[5] >> 4)});
                                        this.strPowerVal += String.format("%01x", new Object[]{Integer.valueOf(cArr[5] & 15)});
                                        cArr[0] = 1;
                                        MakeTxFramePass((byte) 69, (byte) 1, cArr);
                                        if (this.displayIndex == R.integer.Display_CtlPower) {
                                            this.fragmentControlPower.powerDisplay();
                                        }
                                        if (!isCommInitEnd) {
                                            this.txDcommReqTimeInfo = true;
                                            return;
                                        }
                                        return;
                                    default:
                                        switch (b5) {
                                            case 99:
                                                TxDcommSetTemp = false;
                                                Log.d(TAG, "TxDcommSetTemp clear ");
                                                return;
                                            case 100:
                                                TxDcommSetExit = false;
                                                Log.d(TAG, "TxDcommSetExit clear ");
                                                return;
                                            case 101:
                                                MakeTxFramePass(CMD2_REQ_STS_TEMP, (byte) 1, cArr);
                                                if (cArr[0] == 0) {
                                                    String str3 = str;
                                                    for (byte b18 = 0; b18 < 8; b18 = (byte) (b18 + 1)) {
                                                        str3 = str3 + Integer.toHexString(cArr[b18]);
                                                    }
                                                    Log.d(TAG, "Rx(CMD2_REQ_STS_TEMP): " + str3);
                                                    strTempCurrent = String.format("%01x", new Object[]{Integer.valueOf(cArr[2] >> 4)});
                                                    strTempCurrent += String.format("%01x", new Object[]{Integer.valueOf(cArr[2] & 15)});
                                                    if (cArr[1] == 1) {
                                                        strTempCurrent += ".5";
                                                    } else {
                                                        strTempCurrent += ".0";
                                                    }
                                                    strTempSetting = String.format("%01x", new Object[]{Integer.valueOf(cArr[4] >> 4)});
                                                    strTempSetting += String.format("%01x", new Object[]{Integer.valueOf(cArr[4] & 15)});
                                                    if (cArr[3] == 1) {
                                                        strTempSetting += ".5";
                                                    } else {
                                                        strTempSetting += ".0";
                                                    }
                                                    if (cArr[5] == 1) {
                                                        FragmentControlTemp.enableTempExit = true;
                                                    } else {
                                                        FragmentControlTemp.enableTempExit = false;
                                                    }
                                                    if (cArr[6] == 1) {
                                                        FragmentControlTemp.enableTempPower = true;
                                                    } else {
                                                        FragmentControlTemp.enableTempPower = false;
                                                    }
                                                    if (this.displayIndex == R.integer.Display_CtlTemp) {
                                                        this.fragmentControlTemp.tempDisplay();
                                                    }
                                                    if (this.stepSelfTest == 1) {
                                                        this.stepSelfTest = 2;
                                                        this.strErrCode = str;
                                                    }
                                                } else {
                                                    String str4 = str;
                                                    for (byte b19 = 0; b19 < 6; b19 = (byte) (b19 + 1)) {
                                                        str4 = str4 + Integer.toHexString(cArr[b19]);
                                                    }
                                                    Log.d(TAG, "Rx(CMD2_REQ_STS_TEMP): " + str4);
                                                    while (b < 4) {
                                                        str = str + cArr[b + 2];
                                                        b = (byte) (b + 1);
                                                    }
                                                    Log.d(TAG, "ErrCode=" + str);
                                                    if (this.stepSelfTest == 0) {
                                                        DialogInterface dialogInterface = this.mPopupSelfTest1;
                                                        if (dialogInterface != null) {
                                                            dialogInterface.dismiss();
                                                        }
                                                        AlertDialog.Builder builder = new AlertDialog.Builder(this);
                                                        builder.setTitle((CharSequence) "[고장진단]");
                                                        if (str.equals("EL01")) {
                                                            builder.setMessage((CharSequence) "에러코드(난방): EL01 \n온도밸브 제어기 통신에러 \n해당 디바이스의 점검이 필요합니다");
                                                        } else if (str.equals("BL01")) {
                                                            builder.setMessage((CharSequence) "에러코드: BL01 \n보일러 통신에러 \n해당 디바이스의 점검이 필요합니다");
                                                        } else {
                                                            builder.setMessage((CharSequence) "에러코드: " + str + "\n정의되지않음 \n해당 디바이스의 점검이 필요합니다");
                                                        }
                                                        this.mPopupSelfTest1 = builder.show();
                                                    } else {
                                                        this.stepSelfTest = 2;
                                                        this.strErrCode = str;
                                                    }
                                                }
                                                if (enableDevAircon) {
                                                    if (!isCommInitTemp) {
                                                        txDcommReqAirconStatus = true;
                                                        isCommInitTemp = true;
                                                    }
                                                } else if (enableDevFan && !isCommInitTemp) {
                                                    txDcommReqFanStatus = true;
                                                    isCommInitTemp = true;
                                                }
                                                Log.d(TAG, "isAddTempAircon=" + isAddTempAircon + " enableDevTemp=" + enableDevTemp + " enableDevAircon=" + enableDevAircon + "enableDevFan=" + enableDevFan);
                                                return;
                                            case 102:
                                                txDcommReqAirconStatus = false;
                                                Log.d(TAG, "txDcommReqAirconStatus clear ");
                                                return;
                                            default:
                                                switch (b5) {
                                                    case 104:
                                                        TxDcommSetTempAir = false;
                                                        Log.d(TAG, "TxDcommSetTempAir clear ");
                                                        return;
                                                    case 105:
                                                        TxDcommSetPwrAir = false;
                                                        Log.d(TAG, "TxDcommSetPwrAir clear ");
                                                        return;
                                                    case 106:
                                                        TxDcommSetWindAir = false;
                                                        Log.d(TAG, "TxDcommSetWindAir clear ");
                                                        return;
                                                    case 107:
                                                        MakeTxFramePass(CMD2_REQ_STS_AIRC, (byte) 1, cArr);
                                                        if (cArr[0] == 0) {
                                                            String str5 = str;
                                                            for (byte b20 = 0; b20 < 8; b20 = (byte) (b20 + 1)) {
                                                                str5 = str5 + Integer.toHexString(cArr[b20]);
                                                            }
                                                            Log.d(TAG, "Rx(CMD2_REQ_STS_AIRC): " + str5);
                                                            strTAirCurrent = String.format("%01x", new Object[]{Integer.valueOf(cArr[2] >> 4)});
                                                            strTAirCurrent += String.format("%01x", new Object[]{Integer.valueOf(cArr[2] & 15)});
                                                            strTAirSetting = String.format("%01x", new Object[]{Integer.valueOf(cArr[4] >> 4)});
                                                            strTAirSetting += String.format("%01x", new Object[]{Integer.valueOf(cArr[4] & 15)});
                                                            if (cArr[5] == 1) {
                                                                FragmentControlAircon.enableAirPwr = true;
                                                            } else {
                                                                FragmentControlAircon.enableAirPwr = false;
                                                            }
                                                            FragmentControlAircon.stepWind = cArr[6];
                                                            if (this.displayIndex == R.integer.Display_CtlAircon) {
                                                                this.fragmentControlAircon.airconDisplay();
                                                            }
                                                            if (this.stepSelfTest == 1) {
                                                                this.stepSelfTest = 3;
                                                                this.strErrCode = str;
                                                            }
                                                        } else {
                                                            String str6 = str;
                                                            for (byte b21 = 0; b21 < 6; b21 = (byte) (b21 + 1)) {
                                                                str6 = str6 + Integer.toHexString(cArr[b21]);
                                                            }
                                                            Log.d(TAG, "Rx(CMD2_REQ_STS_AIRC): " + str6);
                                                            while (b < 4) {
                                                                str = str + cArr[b + 2];
                                                                b = (byte) (b + 1);
                                                            }
                                                            Log.d(TAG, "ErrCode=" + str);
                                                            if (this.stepSelfTest == 0) {
                                                                DialogInterface dialogInterface2 = this.mPopupSelfTest1;
                                                                if (dialogInterface2 != null) {
                                                                    dialogInterface2.dismiss();
                                                                }
                                                                AlertDialog.Builder builder2 = new AlertDialog.Builder(this);
                                                                builder2.setTitle((CharSequence) "[고장진단]");
                                                                if (str.equals("EL01")) {
                                                                    builder2.setMessage((CharSequence) "에러코드(냉방): EL01 \nxxx제어기 통신에러 \n해당 디바이스의 점검이 필요합니다");
                                                                } else if (str.equals("BL01")) {
                                                                    builder2.setMessage((CharSequence) "에러코드: BL01 \nxxxx 통신에러 \n해당 디바이스의 점검이 필요합니다");
                                                                } else {
                                                                    builder2.setMessage((CharSequence) "에러코드: " + str + "\n정의되지않음 \n해당 디바이스의 점검이 필요합니다");
                                                                }
                                                                this.mPopupSelfTest1 = builder2.show();
                                                            } else {
                                                                this.stepSelfTest = 3;
                                                                this.strErrCode = str;
                                                            }
                                                        }
                                                        if (enableDevFan && !isCommInitAircon) {
                                                            txDcommReqFanStatus = true;
                                                            isCommInitAircon = true;
                                                        }
                                                        Log.d(TAG, "isAddTempAircon=" + isAddTempAircon + " enableDevTemp=" + enableDevTemp + " enableDevAircon=" + enableDevAircon);
                                                        return;
                                                    default:
                                                        switch (b5) {
                                                            case 115:
                                                                TxDcommSetWindFan = false;
                                                                Log.d(TAG, "TxDcommSetWindFan clear ");
                                                                return;
                                                            case 116:
                                                                TxDcommSetPwrFan = false;
                                                                Log.d(TAG, "TxDcommSetPwrFan clear ");
                                                                return;
                                                            case 117:
                                                                MakeTxFramePass(CMD2_REQ_STS_FAN, (byte) 1, cArr);
                                                                if (cArr[0] == 0) {
                                                                    for (byte b22 = 0; b22 < 6; b22 = (byte) (b22 + 1)) {
                                                                        str = str + Integer.toHexString(cArr[b22]);
                                                                    }
                                                                    Log.d(TAG, "Rx(CMD2_REQ_STS_FAN): " + str);
                                                                    strFanTime = String.format("%01x", new Object[]{Integer.valueOf(cArr[3] >> 4)});
                                                                    strFanTime += String.format("%01x", new Object[]{Integer.valueOf(cArr[3] & 15)});
                                                                    if (cArr[1] == 1) {
                                                                        FragmentControlFan.enableFanPwr = true;
                                                                    } else {
                                                                        FragmentControlFan.enableFanPwr = false;
                                                                    }
                                                                    FragmentControlFan.stepFanWind = cArr[2];
                                                                    if (this.displayIndex == R.integer.Display_CtlFan) {
                                                                        this.fragmentControlFan.fanDisplay();
                                                                        return;
                                                                    }
                                                                    return;
                                                                }
                                                                return;
                                                            case 118:
                                                                TxDcommSetTimeFan = false;
                                                                Log.d(TAG, "TxDcommSetTimeFan clear ");
                                                                return;
                                                            default:
                                                                switch (b5) {
                                                                    case 124:
                                                                        Log.d(TAG, "CMD2_REQ_ALIVE receive ");
                                                                        return;
                                                                    case 125:
                                                                        txDcommReqSumPwr = false;
                                                                        Log.d(TAG, "txDcommReqSumPwr clear step=" + Integer.toHexString(cArr[12]));
                                                                        char c2 = (cArr[0] << 8) | cArr[1];
                                                                        String str7 = str + Integer.toString(c2) + " ";
                                                                        char c3 = cArr[3] | (cArr[2] << 8);
                                                                        int i3 = c2 + c3;
                                                                        String str8 = str7 + Integer.toString(c3) + " ";
                                                                        char c4 = (cArr[4] << 8) | cArr[5];
                                                                        int i4 = i3 + c4;
                                                                        String str9 = str8 + Integer.toString(c4) + " ";
                                                                        char c5 = (cArr[6] << 8) | cArr[7];
                                                                        int i5 = i4 + c5;
                                                                        String str10 = str9 + Integer.toString(c5) + " ";
                                                                        char c6 = (cArr[8] << 8) | cArr[9];
                                                                        char c7 = (cArr[10] << 8) | cArr[11];
                                                                        int i6 = i5 + c6 + c7;
                                                                        String str11 = (str10 + Integer.toString(c6) + " ") + Integer.toString(c7) + "\n";
                                                                        char c8 = cArr[12];
                                                                        if (c8 == 0) {
                                                                            this.str_watt = "01~06: " + str11;
                                                                            this.wattSum = i6;
                                                                        } else if (c8 == 1) {
                                                                            this.str_watt += "07~12: " + str11;
                                                                            this.wattSum += i6;
                                                                        } else if (c8 == 2) {
                                                                            this.str_watt += "13~18: " + str11;
                                                                            this.wattSum += i6;
                                                                        } else if (c8 == 3) {
                                                                            this.str_watt += "19~24: " + str11;
                                                                            this.wattSum += i6;
                                                                            this.str_watt += "===> 총 누적 전력량: " + Integer.toString(this.wattSum) + "w";
                                                                        }
                                                                        if (cArr[12] == 3) {
                                                                            AlertDialog.Builder builder3 = new AlertDialog.Builder(this);
                                                                            builder3.setTitle((CharSequence) "[전력량 모니터링]");
                                                                            builder3.setMessage((CharSequence) this.str_watt);
                                                                            builder3.show();
                                                                            return;
                                                                        }
                                                                        return;
                                                                    case 126:
                                                                        Log.d(TAG, "txDcommReqSwitchSet clear ");
                                                                        txDcommReqSwitchSet = false;
                                                                        if (FragmenSetupSwitch.isSetupFirst) {
                                                                            for (byte b23 = 0; b23 < 10; b23 = (byte) (b23 + 1)) {
                                                                                str = str + Integer.toHexString(cArr[b23]);
                                                                            }
                                                                            Log.d(TAG, "Rx(CMD2_REQ_SETTING): " + str);
                                                                            FragmenSetupSwitch.setup_id = cArr[0];
                                                                            FragmenSetupSwitch.setup_id2 = cArr[1];
                                                                            FragmenSetupSwitch.setup_lamp = cArr[2];
                                                                            FragmenSetupSwitch.setup_concent = cArr[3];
                                                                            FragmenSetupSwitch.setup_wallpad = cArr[4];
                                                                            FragmenSetupSwitch.setup_way3 = cArr[5];
                                                                            FragmenSetupSwitch.setup_pattern = cArr[6];
                                                                            FragmenSetupSwitch.setup_relay = cArr[7];
                                                                            FragmenSetupSwitch.setup_touch = cArr[8];
                                                                            FragmenSetupSwitch.setup_ct = cArr[9];
                                                                            onFragmentChanged(R.integer.Display_SetupSwitch);
                                                                            return;
                                                                        }
                                                                        if (FragmenSetupSwitch.setup_lamp != this.lampCount - '0') {
                                                                            this.lampCount = (char) (FragmenSetupSwitch.setup_lamp + 48);
                                                                        }
                                                                        onFragmentChanged(R.integer.Display_CtlMain);
                                                                        Toast.makeText(this, "설정 변경되었습니다.", 0).show();
                                                                        return;
                                                                    case Byte.MAX_VALUE:
                                                                        Log.d(TAG, "txDcommReqSelfTest clear ");
                                                                        this.txDcommReqSelfTest = false;
                                                                        return;
                                                                    default:
                                                                        return;
                                                                }
                                                        }
                                                }
                                        }
                                }
                        }
                    } else {
                        txDcommReqFanStatus = false;
                        Log.d(TAG, "txDcommReqFanStatus clear ");
                    }
                } else {
                    txDcommReqWatt = false;
                    Log.d(TAG, "3)txDcommReqWatt clear ");
                }
            } else {
                Log.d(TAG, "!!!!Rcv Data CS Error");
            }
        } else {
            Log.d(TAG, "!!!!Rcv Data Err");
        }
    }

    /* access modifiers changed from: private */
    public void rxAnalyzer(byte[] bArr) {
        char[] cArr = new char[20];
        int i = 0;
        byte b = bArr[0];
        byte b2 = bArr[1];
        byte b3 = bArr[2];
        byte b4 = bArr[3];
        for (int i2 = 0; i2 < b4; i2++) {
            cArr[i2] = (char) (bArr[i2 + 4] & 255);
        }
        if (b == 122 && b2 == 49) {
            this.cmdBefore = 0;
            if (b3 == 65) {
                this.txDcommSetLamp = false;
                Log.d(TAG, "txDcommSetLamp clear ");
            } else if (b3 == 97) {
                txDcommReqTempStatus = false;
                Log.d(TAG, "txDcommReqTempStatus clear ");
            } else if (b3 == 112) {
                Log.d(TAG, "txDcommReqOtherDevInfo clear ");
                this.txDcommReqOtherDevInfo = false;
                Log.d(TAG, "Rx(CMD2_REQ_OTHER_DEV): " + cArr[0] + cArr[1] + cArr[2] + cArr[3] + cArr[4] + cArr[5]);
                if (b4 < 4) {
                    Log.d(TAG, "Data invalid length=" + b4);
                    enableDevTemp = false;
                    enableDevAircon = false;
                    isAddTempAircon = false;
                    return;
                }
                char c = cArr[0];
                if (c > 4) {
                    this.lampCount = '4';
                } else {
                    this.lampCount = (char) (c + '0');
                }
                Log.d(TAG, "lampCount=" + this.lampCount);
                if (cArr[2] == 1) {
                    enableDevTemp = true;
                } else {
                    enableDevTemp = false;
                }
                if (cArr[3] == 1) {
                    enableDevAircon = true;
                } else {
                    enableDevAircon = false;
                }
                if (enableDevAircon || enableDevTemp) {
                    isAddTempAircon = true;
                }
                isCommFirstEnd = true;
                onFragmentChanged(R.integer.Display_CtlMain);
            } else if (b3 == 121) {
                this.txDcommTest1 = false;
                Log.d(TAG, "txDcommTest1 clear ");
                Log.d(TAG, "cmd=" + Integer.toHexString(b3) + " len=" + b4 + " data=" + Integer.toHexString(cArr[0]) + Integer.toHexString(cArr[1]) + Integer.toHexString(cArr[2]) + Integer.toHexString(cArr[3]));
            } else if (b3 == 124) {
                Log.d(TAG, "CMD2_REQ_ALIVE receive ");
            } else if (b3 == Byte.MAX_VALUE) {
                Log.d(TAG, "txDcommReqSelfTest clear ");
                this.txDcommReqSelfTest = false;
            } else if (b3 != 67) {
                String str = "";
                if (b3 == 68) {
                    this.txDcommReqDeviceInfo = false;
                    Log.d(TAG, "2)txDcommReqDeviceInfo clear ");
                    this.strVerSwitch = str;
                    if (b4 == 0) {
                        this.strVerSwitch = "00";
                        Log.d(TAG, "IoT Switch Version(ST) : 이전버전 V" + this.strVerSwitch + " len=" + b4);
                    } else if (b4 == 1) {
                        this.strVerSwitch += cArr[0];
                        Log.d(TAG, "IoT Switch Version(ST) : 이전버전 V" + this.strVerSwitch + " len=" + b4);
                        this.strVerSwitch = "00";
                    } else {
                        while (i < b4) {
                            if (i == 1) {
                                this.strVerSwitch += " ";
                            }
                            if (i == 0) {
                                this.strVerSwitch += ((char) ((cArr[i] >> 4) + 48));
                                this.strVerSwitch += ((char) ((cArr[i] & 15) + '0'));
                            } else {
                                this.strVerSwitch += cArr[i];
                            }
                            i++;
                        }
                        Log.d(TAG, "IoT Switch Version(ST) : V" + this.strVerSwitch + " len=" + b4);
                    }
                } else if (b3 == 87) {
                    MakeTxFrame(CMD2_REQ_STS_TIME1, (byte) 1, "1");
                    strTimeAlarm1 = str;
                    strTimeAlarm2 = str;
                    strTimeAlarm3 = str;
                    strTimeSleep = str;
                    for (int i3 = 0; i3 < 4; i3++) {
                        strTimeAlarm1 += cArr[i3];
                        strTimeAlarm2 += cArr[i3 + 4];
                        strTimeAlarm3 += cArr[i3 + 8];
                        strTimeSleep += cArr[i3 + 12];
                    }
                    if (FragmentCtlConvAlarm.AlarmNew) {
                        if (strTimeAlarm1.equals("0000")) {
                            FragmentCtlConvAlarm.select1 = false;
                        } else {
                            FragmentCtlConvAlarm.select1 = true;
                        }
                        if (strTimeAlarm2.equals("0000")) {
                            FragmentCtlConvAlarm.select2 = false;
                        } else {
                            FragmentCtlConvAlarm.select2 = true;
                        }
                        if (strTimeAlarm3.equals("0000")) {
                            FragmentCtlConvAlarm.select3 = false;
                        } else {
                            FragmentCtlConvAlarm.select3 = true;
                        }
                        if (FragmentCtlConvAlarm.select1 || FragmentCtlConvAlarm.select2 || FragmentCtlConvAlarm.select3) {
                            FragmentCtlConvAlarm.enableAlarm = true;
                        } else {
                            FragmentCtlConvAlarm.enableAlarm = false;
                        }
                    } else if (!strTimeAlarm1.equals("0000") || !strTimeAlarm2.equals("0000") || !strTimeAlarm3.equals("0000")) {
                        FragmentCtlConvAlarm.enableAlarm = true;
                    } else {
                        FragmentCtlConvAlarm.enableAlarm = false;
                    }
                    if (strTimeSleep.equals("0000")) {
                        FragmentCtlConvSleep.enableSleep = false;
                    } else {
                        FragmentCtlConvSleep.enableSleep = true;
                    }
                    Log.d(TAG, "알람시간: " + strTimeAlarm1 + " " + strTimeAlarm2 + " " + strTimeAlarm3 + " 취침시간: " + strTimeSleep);
                } else if (b3 != 88) {
                    switch (b3) {
                        case 71:
                            TxDcommSetAlarm = false;
                            Log.d(TAG, "TxDcommSetSetAlarm clear ");
                            return;
                        case 72:
                            TxDcommSetSleep = false;
                            Log.d(TAG, "TxDcommSetSleep clear ");
                            return;
                        case 73:
                            TxDcommSetPatten = false;
                            Log.d(TAG, "TxDcommSetPatten clear ");
                            return;
                        case 74:
                            TxDcommSetBang = false;
                            Log.d(TAG, "TxDcommSetBang clear ");
                            return;
                        case 75:
                            this.txDcommReqTimeInfo = false;
                            Log.d(TAG, "4)txDcommReqTimeInfo clear ");
                            return;
                        case 76:
                            this.txDcommSetWatch = false;
                            TxCnt = 0;
                            Log.d(TAG, "1)txDcommSetWatch clear ");
                            this.strVerBLE = str;
                            if (b4 == 0) {
                                this.strVerBLE = "00";
                                Log.d(TAG, "IoT Switch Version(BLE) : 이전버전 V" + this.strVerSwitch + " len=" + b4);
                            } else if (b4 == 1) {
                                this.strVerBLE += cArr[0];
                                Log.d(TAG, "IoT Switch Version(BLE) : 이전버전 V" + this.strVerBLE + " len=" + b4);
                                this.strVerBLE = "00";
                            } else {
                                while (i < b4) {
                                    if (i == 2) {
                                        this.strVerBLE += " ";
                                    }
                                    this.strVerBLE += cArr[i];
                                    i++;
                                }
                                Log.d(TAG, "IoT Switch Version(BLE) : V" + this.strVerBLE + " len=" + b4);
                            }
                            saveDeviceList();
                            return;
                        default:
                            switch (b3) {
                                case 80:
                                    MakeTxFrame(CMD2_REQ_STS_LAMP, (byte) 1, "1");
                                    StringBuilder sb = new StringBuilder("Rx(CMD2_REQ_STS_LAMP): ");
                                    sb.append(cArr[0]);
                                    sb.append(cArr[1]);
                                    sb.append(cArr[2]);
                                    sb.append(cArr[3]);
                                    sb.append(cArr[4]);
                                    Log.d(TAG, sb.toString());
                                    this.lampCount = cArr[0];
                                    int i4 = 0;
                                    for (int i5 = 4; i4 < i5; i5 = 4) {
                                        int i6 = i4 + 1;
                                        this.lampState[i4] = cArr[i6];
                                        i4 = i6;
                                    }
                                    if (this.displayIndex == R.integer.Display_CtlMain) {
                                        this.fragmentControlMain.lampDisplay();
                                    }
                                    if (this.displayIndex == R.integer.Display_Connecting) {
                                        enableDevTemp = false;
                                        enableDevAircon = false;
                                        isAddTempAircon = false;
                                        isCommFirstEnd = true;
                                        onFragmentChanged(R.integer.Display_CtlMain);
                                        Log.d(TAG, "jyk change");
                                        return;
                                    }
                                    return;
                                case 81:
                                    MakeTxFrame(CMD2_REQ_STS_CONC, (byte) 1, "1");
                                    return;
                                case 82:
                                    MakeTxFrame(CMD2_REQ_STS_CONPWR, (byte) 1, "1");
                                    return;
                                case 83:
                                    MakeTxFrame(CMD2_REQ_STS_CONCUT, (byte) 1, "1");
                                    if (!isCommInitEnd) {
                                        txDcommReqWatt = true;
                                        return;
                                    }
                                    return;
                                case 84:
                                    MakeTxFrame(CMD2_REQ_STS_PWR, (byte) 1, "1");
                                    Log.d(TAG, "PWR Value: len=" + b4 + " data=" + Integer.toHexString(cArr[8]) + " " + Integer.toHexString(cArr[9]) + " " + Integer.toHexString(cArr[10]) + " " + Integer.toHexString(cArr[11]));
                                    this.strPowerVal = str;
                                    for (int i7 = 0; i7 < 4; i7++) {
                                        this.strPowerVal += cArr[i7 + 8];
                                    }
                                    while (i < 16) {
                                        String str2 = str + cArr[i];
                                        if (i == 3 || i == 7 || i == 11) {
                                            str2 = str2 + " ";
                                        }
                                        str = str2;
                                        i++;
                                    }
                                    Log.d(TAG, "Rcv power data =" + str);
                                    if (this.displayIndex == R.integer.Display_CtlPower) {
                                        this.fragmentControlPower.powerDisplay();
                                    }
                                    if (!isCommInitEnd) {
                                        this.txDcommReqTimeInfo = true;
                                        return;
                                    }
                                    return;
                                default:
                                    switch (b3) {
                                        case 99:
                                            TxDcommSetTemp = false;
                                            Log.d(TAG, "TxDcommSetTemp clear ");
                                            return;
                                        case 100:
                                            TxDcommSetExit = false;
                                            Log.d(TAG, "TxDcommSetExit clear ");
                                            return;
                                        case 101:
                                            MakeTxFrame(CMD2_REQ_STS_TEMP, (byte) 1, "1");
                                            if (cArr[0] == 0) {
                                                Log.d(TAG, "Rx(CMD2_REQ_STS_TEMP): " + cArr[0] + cArr[1] + cArr[2] + cArr[3] + cArr[4] + cArr[5] + cArr[6] + cArr[7]);
                                                strTempCurrent = String.format("%01x", new Object[]{Integer.valueOf(cArr[2] >> 4)});
                                                StringBuilder sb2 = new StringBuilder();
                                                sb2.append(strTempCurrent);
                                                sb2.append(String.format("%01x", new Object[]{Integer.valueOf(cArr[2] & 15)}));
                                                strTempCurrent = sb2.toString();
                                                if (cArr[1] == 1) {
                                                    strTempCurrent += ".5";
                                                } else {
                                                    strTempCurrent += ".0";
                                                }
                                                strTempSetting = String.format("%01x", new Object[]{Integer.valueOf(cArr[4] >> 4)});
                                                strTempSetting += String.format("%01x", new Object[]{Integer.valueOf(cArr[4] & 15)});
                                                if (cArr[3] == 1) {
                                                    strTempSetting += ".5";
                                                } else {
                                                    strTempSetting += ".0";
                                                }
                                                Log.d(TAG, "TempCurTemp=" + strTempCurrent + " TempSetTemp=" + strTempSetting);
                                                if (cArr[5] == 1) {
                                                    FragmentControlTemp.enableTempExit = true;
                                                } else {
                                                    FragmentControlTemp.enableTempExit = false;
                                                }
                                                if (this.displayIndex == R.integer.Display_CtlTemp) {
                                                    this.fragmentControlTemp.tempDisplay();
                                                }
                                                if (enableDevAircon && !isCommInitTemp) {
                                                    txDcommReqAirconStatus = true;
                                                    isCommInitTemp = true;
                                                }
                                                if (this.stepSelfTest == 1) {
                                                    this.stepSelfTest = 2;
                                                    this.strErrCode = str;
                                                }
                                            } else {
                                                StringBuilder sb3 = new StringBuilder("Rx(CMD2_REQ_STS_TEMP:Err): ");
                                                sb3.append(cArr[0]);
                                                sb3.append(cArr[1]);
                                                sb3.append(cArr[2]);
                                                sb3.append(cArr[3]);
                                                sb3.append(cArr[4]);
                                                sb3.append(cArr[5]);
                                                Log.d(TAG, sb3.toString());
                                                for (int i8 = 4; i < i8; i8 = 4) {
                                                    str = str + cArr[i + 2];
                                                    i++;
                                                }
                                                Log.d(TAG, "ErrCode=" + str);
                                                if (this.stepSelfTest == 0) {
                                                    DialogInterface dialogInterface = this.mPopupSelfTest1;
                                                    if (dialogInterface != null) {
                                                        dialogInterface.dismiss();
                                                    }
                                                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                                                    builder.setTitle((CharSequence) "[고장진단]");
                                                    if (str.equals("EL01")) {
                                                        builder.setMessage((CharSequence) "에러코드(난방): EL01 \n온도밸브 제어기 통신에러 \n해당 디바이스의 점검이 필요합니다");
                                                    } else if (str.equals("BL01")) {
                                                        builder.setMessage((CharSequence) "에러코드: BL01 \n보일러 통신에러 \n해당 디바이스의 점검이 필요합니다");
                                                    } else {
                                                        builder.setMessage((CharSequence) "에러코드: " + str + "\n정의되지않음 \n해당 디바이스의 점검이 필요합니다");
                                                    }
                                                    this.mPopupSelfTest1 = builder.show();
                                                } else {
                                                    this.stepSelfTest = 2;
                                                    this.strErrCode = str;
                                                }
                                            }
                                            Log.d(TAG, "isAddTempAircon=" + isAddTempAircon + " enableDevTemp=" + enableDevTemp + " enableDevAircon=" + enableDevAircon);
                                            return;
                                        case 102:
                                            txDcommReqAirconStatus = false;
                                            Log.d(TAG, "txDcommReqAirconStatus clear ");
                                            return;
                                        default:
                                            switch (b3) {
                                                case 104:
                                                    TxDcommSetTempAir = false;
                                                    Log.d(TAG, "TxDcommSetTempAir clear ");
                                                    return;
                                                case 105:
                                                    TxDcommSetPwrAir = false;
                                                    Log.d(TAG, "TxDcommSetPwrAir clear ");
                                                    return;
                                                case 106:
                                                    TxDcommSetWindAir = false;
                                                    Log.d(TAG, "TxDcommSetWindAir clear ");
                                                    return;
                                                case 107:
                                                    MakeTxFrame(CMD2_REQ_STS_AIRC, (byte) 1, "1");
                                                    if (cArr[0] == 0) {
                                                        Log.d(TAG, "Rx(CMD2_REQ_STS_AIRC): " + cArr[0] + cArr[1] + cArr[2] + cArr[3] + cArr[4] + cArr[5] + cArr[6] + cArr[7]);
                                                        strTAirCurrent = String.format("%01x", new Object[]{Integer.valueOf(cArr[2] >> 4)});
                                                        strTAirCurrent += String.format("%01x", new Object[]{Integer.valueOf(cArr[2] & 15)});
                                                        strTAirSetting = String.format("%01x", new Object[]{Integer.valueOf(cArr[4] >> 4)});
                                                        strTAirSetting += String.format("%01x", new Object[]{Integer.valueOf(cArr[4] & 15)});
                                                        Log.d(TAG, "AirCurTemp=" + strTAirCurrent + " AirSetTemp=" + strTAirSetting);
                                                        if (cArr[5] == 1) {
                                                            FragmentControlAircon.enableAirPwr = true;
                                                        } else {
                                                            FragmentControlAircon.enableAirPwr = false;
                                                        }
                                                        FragmentControlAircon.stepWind = cArr[6];
                                                        if (this.displayIndex == R.integer.Display_CtlAircon) {
                                                            this.fragmentControlAircon.airconDisplay();
                                                        }
                                                        if (this.stepSelfTest == 1) {
                                                            this.stepSelfTest = 3;
                                                            this.strErrCode = str;
                                                        }
                                                    } else {
                                                        StringBuilder sb4 = new StringBuilder("Rx(CMD2_REQ_STS_AIRC): ");
                                                        sb4.append(cArr[0]);
                                                        sb4.append(cArr[1]);
                                                        sb4.append(cArr[2]);
                                                        sb4.append(cArr[3]);
                                                        sb4.append(cArr[4]);
                                                        sb4.append(cArr[5]);
                                                        Log.d(TAG, sb4.toString());
                                                        for (int i9 = 4; i < i9; i9 = 4) {
                                                            str = str + cArr[i + 2];
                                                            i++;
                                                        }
                                                        Log.d(TAG, "ErrCode=" + str);
                                                        if (this.stepSelfTest == 0) {
                                                            DialogInterface dialogInterface2 = this.mPopupSelfTest1;
                                                            if (dialogInterface2 != null) {
                                                                dialogInterface2.dismiss();
                                                            }
                                                            AlertDialog.Builder builder2 = new AlertDialog.Builder(this);
                                                            builder2.setTitle((CharSequence) "[고장진단]");
                                                            if (str.equals("EL01")) {
                                                                builder2.setMessage((CharSequence) "에러코드(냉방): EL01 \nxxx제어기 통신에러 \n해당 디바이스의 점검이 필요합니다");
                                                            } else if (str.equals("BL01")) {
                                                                builder2.setMessage((CharSequence) "에러코드: BL01 \nxxxx 통신에러 \n해당 디바이스의 점검이 필요합니다");
                                                            } else {
                                                                builder2.setMessage((CharSequence) "에러코드: " + str + "\n정의되지않음 \n해당 디바이스의 점검이 필요합니다");
                                                            }
                                                            this.mPopupSelfTest1 = builder2.show();
                                                        } else {
                                                            this.stepSelfTest = 3;
                                                            this.strErrCode = str;
                                                        }
                                                    }
                                                    Log.d(TAG, "isAddTempAircon=" + isAddTempAircon + " enableDevTemp=" + enableDevTemp + " enableDevAircon=" + enableDevAircon);
                                                    return;
                                                default:
                                                    return;
                                            }
                                    }
                            }
                    }
                } else {
                    MakeTxFrame(CMD2_REQ_STS_TIME2, (byte) 1, "1");
                    strTimeSafeOn = str;
                    strTimeSafeOff = str;
                    for (int i10 = 0; i10 < 4; i10++) {
                        strTimeSafeOn += cArr[i10];
                        strTimeSafeOff += cArr[i10 + 4];
                    }
                    if (!strTimeSafeOn.equals("0000") || !strTimeSafeOff.equals("0000")) {
                        FragmentCtlConvSafe.enableSafe = true;
                    } else {
                        FragmentCtlConvSafe.enableSafe = false;
                    }
                    Log.d(TAG, "방범시간: " + strTimeSafeOn + " " + strTimeSafeOff);
                    if (enableDevTemp) {
                        txDcommReqTempStatus = true;
                    } else if (enableDevAircon) {
                        txDcommReqAirconStatus = true;
                    }
                    isCommInitEnd = true;
                    Log.d(TAG, "5)IoT Switch InitComm End ");
                }
            } else {
                txDcommReqWatt = false;
                Log.d(TAG, "3)txDcommReqWatt clear ");
            }
        } else {
            Log.d(TAG, "!!!!Rcv Data Err");
        }
    }

    private void saveDeviceList() {
        int parseInt = Integer.parseInt(mRoomNumber);
        Log.d(TAG, "mRoomNumber=" + mRoomNumber);
        int i = parseInt - 1;
        MainActivity.strArrRoomInfo[i][1] = this.mDeviceName;
        MainActivity.strArrRoomInfo[i][2] = mDeviceAddress;
    }

    /* access modifiers changed from: private */
    public void MakeTxFramePass(byte b, byte b2, char[] cArr) {
        int i;
        byte[] bArr = new byte[20];
        for (int i2 = 0; i2 < 20; i2++) {
            bArr[i2] = 0;
        }
        bArr[0] = CMD2_REQ_SETTING;
        bArr[1] = 32;
        bArr[2] = 15;
        bArr[3] = b;
        bArr[4] = b2;
        int i3 = 0;
        while (i3 < b2) {
            try {
                bArr[i3 + 5] = (byte) cArr[i3];
                i3++;
            } catch (Exception e) {
                e.printStackTrace();
                Log.d(TAG, "ERR: MakeTxFramePass: " + e.getMessage());
                return;
            }
        }
        int i4 = 0;
        byte b3 = 0;
        byte b4 = 0;
        while (true) {
            i = b2 + 5;
            if (i4 >= i) {
                break;
            }
            byte b5 = bArr[i4];
            b4 = (byte) (b4 ^ b5);
            b3 = (byte) (b3 + b5);
            i4++;
        }
        bArr[i] = b4;
        bArr[b2 + 6] = (byte) (b3 + b4);
        if (this.mConnected) {
            this.mBluetoothLeService.writeRXCharacteristic(bArr);
            if (b == this.cmdBefore) {
                int i5 = this.txRetryCnt + 1;
                this.txRetryCnt = i5;
                if (i5 > 4) {
                    this.txRetryCnt = 0;
                    if (b == 49) {
                        this.txDcommSetLamp = false;
                    } else if (b == 54) {
                        TxDcommSetPatten = false;
                    } else if (b == 97) {
                        txDcommReqTempStatus = false;
                    } else if (b == 102) {
                        txDcommReqAirconStatus = false;
                    } else if (b == 118) {
                        TxDcommSetTimeFan = false;
                    } else if (b == 121) {
                        this.txDcommTest1 = false;
                    } else if (b == 51) {
                        txDcommReqWatt = false;
                    } else if (b == 52) {
                        this.txDcommReqDeviceInfo = false;
                    } else if (b == 99) {
                        TxDcommSetTemp = false;
                    } else if (b == 100) {
                        TxDcommSetExit = false;
                    } else if (b == 112) {
                        this.txDcommReqOtherDevInfo = false;
                    } else if (b == 113) {
                        txDcommReqFanStatus = false;
                    } else if (b == 115) {
                        TxDcommSetWindFan = false;
                    } else if (b != 116) {
                        switch (b) {
                            case CMD_REQ_SET_WAKE:
                                TxDcommSetAlarm = false;
                                return;
                            case 60:
                                TxDcommSetSleep = false;
                                return;
                            case 61:
                                TxDcommSetBang = false;
                                return;
                            case 62:
                                this.txDcommReqTimeInfo = false;
                                return;
                            case 63:
                                this.txDcommSetWatch = false;
                                return;
                            default:
                                switch (b) {
                                    case 104:
                                        TxDcommSetTempAir = false;
                                        return;
                                    case 105:
                                        TxDcommSetPwrAir = false;
                                        return;
                                    case 106:
                                        TxDcommSetWindAir = false;
                                        return;
                                    default:
                                        switch (b) {
                                            case 125:
                                                txDcommReqSumPwr = false;
                                                return;
                                            case 126:
                                                txDcommReqSwitchSet = false;
                                                return;
                                            case Byte.MAX_VALUE:
                                                this.txDcommReqSelfTest = false;
                                                return;
                                            default:
                                                return;
                                        }
                                }
                        }
                    } else {
                        TxDcommSetPwrFan = false;
                    }
                }
            } else {
                this.cmdBefore = b;
                this.txRetryCnt = 0;
            }
        } else {
            Log.d(TAG, "jyk not connected");
        }
    }

    /* access modifiers changed from: private */
    public void MakeTxFrame1(byte b, byte b2, char[] cArr) {
        byte[] bArr = new byte[20];
        for (int i = 0; i < 20; i++) {
            bArr[i] = 0;
        }
        bArr[0] = CMD2_TO_JUNG;
        bArr[1] = CMD_REQ_CTR_LAMP;
        bArr[2] = b;
        bArr[3] = b2;
        int i2 = 0;
        while (i2 < b2) {
            try {
                bArr[i2 + 4] = (byte) cArr[i2];
                i2++;
            } catch (Exception e) {
                e.printStackTrace();
                Log.d(TAG, "ERR: MakeTxFrame: " + e.getMessage());
                return;
            }
        }
        if (this.mConnected) {
            this.mBluetoothLeService.writeRXCharacteristic(bArr);
            if (b == this.cmdBefore) {
                int i3 = this.txRetryCnt + 1;
                this.txRetryCnt = i3;
                if (i3 > 4) {
                    this.txRetryCnt = 0;
                    if (b == 65) {
                        this.txDcommSetLamp = false;
                    } else if (b == 97) {
                        txDcommReqTempStatus = false;
                    } else if (b == 102) {
                        txDcommReqAirconStatus = false;
                    } else if (b == 112) {
                        this.txDcommReqOtherDevInfo = false;
                    } else if (b == 121) {
                        this.txDcommTest1 = false;
                    } else if (b == Byte.MAX_VALUE) {
                        this.txDcommReqSelfTest = false;
                    } else if (b == 67) {
                        txDcommReqWatt = false;
                    } else if (b == 68) {
                        this.txDcommReqDeviceInfo = false;
                    } else if (b == 99) {
                        TxDcommSetTemp = false;
                    } else if (b != 100) {
                        switch (b) {
                            case 71:
                                TxDcommSetAlarm = false;
                                return;
                            case 72:
                                TxDcommSetSleep = false;
                                return;
                            case 73:
                                TxDcommSetPatten = false;
                                return;
                            case 74:
                                TxDcommSetBang = false;
                                return;
                            case 75:
                                this.txDcommReqTimeInfo = false;
                                return;
                            case 76:
                                this.txDcommSetWatch = false;
                                return;
                            default:
                                switch (b) {
                                    case 104:
                                        TxDcommSetTempAir = false;
                                        return;
                                    case 105:
                                        TxDcommSetPwrAir = false;
                                        return;
                                    case 106:
                                        TxDcommSetWindAir = false;
                                        return;
                                    default:
                                        return;
                                }
                        }
                    } else {
                        TxDcommSetExit = false;
                    }
                }
            } else {
                this.cmdBefore = b;
                this.txRetryCnt = 0;
            }
        } else {
            Log.d(TAG, "jyk not connected");
        }
    }

    /* access modifiers changed from: private */
    public void MakeTxFrame(byte b, byte b2, String str) {
        byte[] bArr = new byte[20];
        for (int i = 0; i < 20; i++) {
            bArr[i] = 0;
        }
        bArr[0] = CMD2_TO_JUNG;
        bArr[1] = CMD_REQ_CTR_LAMP;
        bArr[2] = b;
        bArr[3] = b2;
        int i2 = 0;
        while (i2 < b2) {
            try {
                bArr[i2 + 4] = (byte) str.charAt(i2);
                i2++;
            } catch (Exception e) {
                e.printStackTrace();
                Log.d(TAG, "ERR: MakeTxFrame: " + e.getMessage());
                return;
            }
        }
        if (this.mConnected) {
            this.mBluetoothLeService.writeRXCharacteristic(bArr);
            if (b == this.cmdBefore) {
                int i3 = this.txRetryCnt + 1;
                this.txRetryCnt = i3;
                if (i3 > 4) {
                    this.txRetryCnt = 0;
                    if (b == 65) {
                        this.txDcommSetLamp = false;
                    } else if (b == 97) {
                        txDcommReqTempStatus = false;
                    } else if (b == 102) {
                        txDcommReqAirconStatus = false;
                    } else if (b == 112) {
                        this.txDcommReqOtherDevInfo = false;
                    } else if (b == 121) {
                        this.txDcommTest1 = false;
                    } else if (b == Byte.MAX_VALUE) {
                        this.txDcommReqSelfTest = false;
                    } else if (b == 67) {
                        txDcommReqWatt = false;
                    } else if (b == 68) {
                        this.txDcommReqDeviceInfo = false;
                    } else if (b == 99) {
                        TxDcommSetTemp = false;
                    } else if (b != 100) {
                        switch (b) {
                            case 71:
                                TxDcommSetAlarm = false;
                                return;
                            case 72:
                                TxDcommSetSleep = false;
                                return;
                            case 73:
                                TxDcommSetPatten = false;
                                return;
                            case 74:
                                TxDcommSetBang = false;
                                return;
                            case 75:
                                this.txDcommReqTimeInfo = false;
                                return;
                            case 76:
                                this.txDcommSetWatch = false;
                                return;
                            default:
                                switch (b) {
                                    case 104:
                                        TxDcommSetTempAir = false;
                                        return;
                                    case 105:
                                        TxDcommSetPwrAir = false;
                                        return;
                                    case 106:
                                        TxDcommSetWindAir = false;
                                        return;
                                    default:
                                        return;
                                }
                        }
                    } else {
                        TxDcommSetExit = false;
                    }
                }
            } else {
                this.cmdBefore = b;
                this.txRetryCnt = 0;
            }
        } else {
            Log.d(TAG, "jyk not connected");
        }
    }

    class CommThreadPass extends Thread {
        CommThreadPass() {
        }

        public void run() {
            String str;
            String str2;
            String str3;
            char[] cArr = new char[20];
            while (DeviceControlActivity.isCommOk) {
                cArr[0] = 1;
                if (DeviceControlActivity.this.isTxDcommDummy) {
                    DeviceControlActivity.this.isTxDcommDummy = false;
                    Log.d(DeviceControlActivity.TAG, "isTxDcommDummy 300ms delay");
                } else {
                    byte b = 10;
                    if (DeviceControlActivity.this.txDcommSetWatch) {
                        Calendar instance = Calendar.getInstance();
                        String substring = MainActivity.strVerName.substring(0, 2);
                        int parseInt = Integer.parseInt(substring);
                        int i = instance.get(11);
                        if (i == 0) {
                            i = 24;
                        }
                        int i2 = instance.get(12);
                        int i3 = instance.get(13);
                        int i4 = instance.get(2) + 1;
                        int i5 = instance.get(5);
                        int i6 = instance.get(7);
                        cArr[0] = (char) (((parseInt / 10) << 4) | (parseInt % 10));
                        cArr[1] = (char) (((i / 10) << 4) | (i % 10));
                        cArr[2] = (char) ((i2 % 10) | ((i2 / 10) << 4));
                        cArr[3] = (char) (((i3 / 10) << 4) | (i3 % 10));
                        cArr[4] = (char) (((i4 / 10) << 4) | (i4 % 10));
                        cArr[5] = (char) (((i5 / 10) << 4) | (i5 % 10));
                        cArr[6] = (char) i6;
                        cArr[7] = 'U';
                        DeviceControlActivity.TxCnt++;
                        Log.d(DeviceControlActivity.TAG, "Current Time(my) = " + substring + "  TxCnt=" + DeviceControlActivity.TxCnt);
                        int i7 = DeviceControlActivity.TxCnt;
                        if (i7 == 2 || i7 == 4 || i7 == 5 || i7 == 7 || i7 == 8 || i7 == 10 || i7 == 11) {
                            DeviceControlActivity.this.isTxDcommDummy = true;
                        } else {
                            DeviceControlActivity.this.MakeTxFramePass(DeviceControlActivity.CMD_REQ_SET_WATCH, (byte) 8, cArr);
                        }
                    } else if (DeviceControlActivity.this.txDcommReqOtherDevInfo) {
                        DeviceControlActivity.this.MakeTxFramePass(DeviceControlActivity.CMD2_REQ_OTHER_DEV, (byte) 1, cArr);
                    } else if (DeviceControlActivity.this.txDcommReqSelfTest) {
                        DeviceControlActivity.this.MakeTxFramePass(DeviceControlActivity.CMD2_REQ_SELFTEST, (byte) 1, cArr);
                    } else if (DeviceControlActivity.txDcommReqSwitchSet) {
                        if (FragmenSetupSwitch.isSetupFirst) {
                            cArr[0] = 0;
                            b = 1;
                        } else {
                            cArr[0] = (char) FragmenSetupSwitch.setup_id;
                            cArr[1] = (char) FragmenSetupSwitch.setup_id2;
                            cArr[2] = (char) FragmenSetupSwitch.setup_lamp;
                            cArr[3] = (char) FragmenSetupSwitch.setup_concent;
                            cArr[4] = (char) FragmenSetupSwitch.setup_wallpad;
                            cArr[5] = (char) FragmenSetupSwitch.setup_way3;
                            cArr[6] = (char) FragmenSetupSwitch.setup_pattern;
                            cArr[7] = (char) FragmenSetupSwitch.setup_relay;
                            cArr[8] = (char) FragmenSetupSwitch.setup_touch;
                            cArr[9] = (char) FragmenSetupSwitch.setup_ct;
                        }
                        DeviceControlActivity.this.MakeTxFramePass(DeviceControlActivity.CMD2_REQ_SETTING, b, cArr);
                    } else if (DeviceControlActivity.this.txDcommReqDeviceInfo) {
                        DeviceControlActivity.this.MakeTxFramePass(DeviceControlActivity.CMD_REQ_DEV_STS, (byte) 1, cArr);
                    } else if (DeviceControlActivity.txDcommReqWatt) {
                        DeviceControlActivity.this.MakeTxFramePass(DeviceControlActivity.CMD_REQ_CTR_PWR, (byte) 1, cArr);
                    } else if (DeviceControlActivity.this.txDcommReqTimeInfo) {
                        DeviceControlActivity.this.MakeTxFramePass(DeviceControlActivity.CMD_REQ_TIMEINFO, (byte) 1, cArr);
                    } else if (DeviceControlActivity.this.txDcommSetLamp) {
                        cArr[0] = DeviceControlActivity.this.lampNumber;
                        switch (DeviceControlActivity.this.lampNumber) {
                            case '2':
                                cArr[1] = DeviceControlActivity.this.lampState[1];
                                break;
                            case '3':
                                cArr[1] = DeviceControlActivity.this.lampState[2];
                                break;
                            case '4':
                                cArr[1] = DeviceControlActivity.this.lampState[3];
                                break;
                            default:
                                cArr[1] = DeviceControlActivity.this.lampState[0];
                                break;
                        }
                        cArr[0] = (char) (cArr[0] & 15);
                        cArr[1] = (char) (cArr[1] & 15);
                        DeviceControlActivity.this.MakeTxFramePass(DeviceControlActivity.CMD_REQ_CTR_LAMP, (byte) 2, cArr);
                    } else if (DeviceControlActivity.TxDcommSetPatten) {
                        for (int i8 = 0; i8 < 4; i8++) {
                            char c = DeviceControlActivity.this.lampState[i8];
                            cArr[i8] = c;
                            cArr[i8] = (char) (c & 15);
                        }
                        DeviceControlActivity.this.MakeTxFramePass(DeviceControlActivity.CMD_REQ_CTR_PATT, (byte) 4, cArr);
                    } else {
                        String str4 = "0000";
                        if (DeviceControlActivity.TxDcommSetAlarm) {
                            if (FragmentCtlConvAlarm.AlarmNew) {
                                String str5 = FragmentCtlConvAlarm.select1 ? DeviceControlActivity.strTimeAlarm1 : str4;
                                if (FragmentCtlConvAlarm.select2) {
                                    str3 = str5 + DeviceControlActivity.strTimeAlarm2;
                                } else {
                                    str3 = str5 + str4;
                                }
                                if (FragmentCtlConvAlarm.select3) {
                                    str2 = str3 + DeviceControlActivity.strTimeAlarm3;
                                } else {
                                    str2 = str3 + str4;
                                }
                            } else if (FragmentCtlConvAlarm.enableAlarm) {
                                str2 = DeviceControlActivity.strTimeAlarm1 + DeviceControlActivity.strTimeAlarm2 + DeviceControlActivity.strTimeAlarm3;
                            } else {
                                str2 = "000000000000";
                            }
                            char parseInt2 = (char) (Integer.parseInt(str2.substring(0, 1)) << 4);
                            cArr[0] = parseInt2;
                            cArr[0] = (char) (parseInt2 | ((char) Integer.parseInt(str2.substring(1, 2))));
                            char parseInt3 = (char) (Integer.parseInt(str2.substring(2, 3)) << 4);
                            cArr[1] = parseInt3;
                            cArr[1] = (char) (parseInt3 | ((char) Integer.parseInt(str2.substring(3, 4))));
                            char parseInt4 = (char) (Integer.parseInt(str2.substring(4, 5)) << 4);
                            cArr[2] = parseInt4;
                            cArr[2] = (char) (parseInt4 | ((char) Integer.parseInt(str2.substring(5, 6))));
                            char parseInt5 = (char) (Integer.parseInt(str2.substring(6, 7)) << 4);
                            cArr[3] = parseInt5;
                            cArr[3] = (char) (((char) Integer.parseInt(str2.substring(7, 8))) | parseInt5);
                            char parseInt6 = (char) (Integer.parseInt(str2.substring(8, 9)) << 4);
                            cArr[4] = parseInt6;
                            cArr[4] = (char) (((char) Integer.parseInt(str2.substring(9, 10))) | parseInt6);
                            char parseInt7 = (char) (Integer.parseInt(str2.substring(10, 11)) << 4);
                            cArr[5] = parseInt7;
                            cArr[5] = (char) (((char) Integer.parseInt(str2.substring(11, 12))) | parseInt7);
                            DeviceControlActivity.this.MakeTxFramePass(DeviceControlActivity.CMD_REQ_SET_WAKE, (byte) 6, cArr);
                        } else if (DeviceControlActivity.TxDcommSetSleep) {
                            if (FragmentCtlConvSleep.enableSleep) {
                                str4 = DeviceControlActivity.strTimeSleep;
                            }
                            char parseInt8 = (char) (Integer.parseInt(str4.substring(0, 1)) << 4);
                            cArr[0] = parseInt8;
                            cArr[0] = (char) (parseInt8 | ((char) Integer.parseInt(str4.substring(1, 2))));
                            char parseInt9 = (char) (Integer.parseInt(str4.substring(2, 3)) << 4);
                            cArr[1] = parseInt9;
                            cArr[1] = (char) (parseInt9 | ((char) Integer.parseInt(str4.substring(3, 4))));
                            DeviceControlActivity.this.MakeTxFramePass(DeviceControlActivity.CMD_REQ_SET_SLEEP, (byte) 2, cArr);
                        } else if (DeviceControlActivity.TxDcommSetBang) {
                            if (FragmentCtlConvSafe.enableSafe) {
                                str = DeviceControlActivity.strTimeSafeOn + DeviceControlActivity.strTimeSafeOff;
                            } else {
                                str = "00000000";
                            }
                            char parseInt10 = (char) (Integer.parseInt(str.substring(0, 1)) << 4);
                            cArr[0] = parseInt10;
                            cArr[0] = (char) (parseInt10 | ((char) Integer.parseInt(str.substring(1, 2))));
                            char parseInt11 = (char) (Integer.parseInt(str.substring(2, 3)) << 4);
                            cArr[1] = parseInt11;
                            cArr[1] = (char) (parseInt11 | ((char) Integer.parseInt(str.substring(3, 4))));
                            char parseInt12 = (char) (Integer.parseInt(str.substring(4, 5)) << 4);
                            cArr[2] = parseInt12;
                            cArr[2] = (char) (parseInt12 | ((char) Integer.parseInt(str.substring(5, 6))));
                            char parseInt13 = (char) (Integer.parseInt(str.substring(6, 7)) << 4);
                            cArr[3] = parseInt13;
                            cArr[3] = (char) (((char) Integer.parseInt(str.substring(7, 8))) | parseInt13);
                            DeviceControlActivity.this.MakeTxFramePass(DeviceControlActivity.CMD_REQ_SET_BANG, (byte) 4, cArr);
                        } else if (DeviceControlActivity.txDcommReqTempStatus) {
                            DeviceControlActivity.this.MakeTxFramePass(DeviceControlActivity.CMD2_REQ_RSTS_TEMP, (byte) 1, cArr);
                        } else if (DeviceControlActivity.TxDcommSetTemp) {
                            if (DeviceControlActivity.strTempSetting.substring(2, 4).equals(".5")) {
                                cArr[0] = 1;
                            } else {
                                cArr[0] = 0;
                            }
                            char parseInt14 = (char) (Integer.parseInt(DeviceControlActivity.strTempSetting.substring(0, 1)) << 4);
                            cArr[1] = parseInt14;
                            cArr[1] = (char) (parseInt14 | ((char) (Integer.parseInt(DeviceControlActivity.strTempSetting.substring(1, 2)) & 15)));
                            DeviceControlActivity.this.MakeTxFramePass(DeviceControlActivity.CMD2_REQ_CTR_TEMPCHG, (byte) 2, cArr);
                        } else if (DeviceControlActivity.TxDcommSetExit) {
                            if (FragmentControlTemp.enableTempExit) {
                                cArr[0] = 1;
                            } else {
                                cArr[0] = 0;
                            }
                            DeviceControlActivity.this.MakeTxFramePass(DeviceControlActivity.CMD2_REQ_CTR_TEMPEXIT, (byte) 1, cArr);
                        } else if (DeviceControlActivity.txDcommReqAirconStatus) {
                            DeviceControlActivity.this.MakeTxFramePass(DeviceControlActivity.CMD2_REQ_RSTS_AIRC, (byte) 1, cArr);
                        } else if (DeviceControlActivity.TxDcommSetTempAir) {
                            cArr[0] = 0;
                            char parseInt15 = (char) (Integer.parseInt(DeviceControlActivity.strTAirSetting.substring(0, 1)) << 4);
                            cArr[1] = parseInt15;
                            cArr[1] = (char) (parseInt15 | ((char) (Integer.parseInt(DeviceControlActivity.strTAirSetting.substring(1, 2)) & 15)));
                            DeviceControlActivity.this.MakeTxFramePass(DeviceControlActivity.CMD2_REQ_CTR_AIRCHG, (byte) 2, cArr);
                        } else if (DeviceControlActivity.TxDcommSetPwrAir) {
                            if (FragmentControlAircon.enableAirPwr) {
                                cArr[0] = 1;
                            } else {
                                cArr[0] = 0;
                            }
                            DeviceControlActivity.this.MakeTxFramePass(DeviceControlActivity.CMD2_REQ_CTR_AIRPWR, (byte) 1, cArr);
                        } else if (DeviceControlActivity.TxDcommSetWindAir) {
                            cArr[0] = (char) FragmentControlAircon.stepWind;
                            DeviceControlActivity.this.MakeTxFramePass(DeviceControlActivity.CMD2_REQ_CTR_AIRWIND, (byte) 1, cArr);
                        } else if (DeviceControlActivity.txDcommReqFanStatus) {
                            DeviceControlActivity.this.MakeTxFramePass(DeviceControlActivity.CMD2_REQ_RSTS_FAN, (byte) 1, cArr);
                        } else if (DeviceControlActivity.TxDcommSetPwrFan) {
                            if (FragmentControlFan.enableFanPwr) {
                                cArr[0] = 1;
                            } else {
                                cArr[0] = 0;
                            }
                            DeviceControlActivity.this.MakeTxFramePass(DeviceControlActivity.CMD2_REQ_CTR_FANPWR, (byte) 1, cArr);
                        } else if (DeviceControlActivity.TxDcommSetWindFan) {
                            cArr[0] = (char) FragmentControlFan.stepFanWind;
                            DeviceControlActivity.this.MakeTxFramePass(DeviceControlActivity.CMD2_REQ_CTR_FANWIND, (byte) 1, cArr);
                        } else if (DeviceControlActivity.TxDcommSetTimeFan) {
                            char parseInt16 = (char) (Integer.parseInt(DeviceControlActivity.strFanTime.substring(0, 1)) << 4);
                            cArr[0] = parseInt16;
                            cArr[0] = (char) (parseInt16 | ((char) (Integer.parseInt(DeviceControlActivity.strFanTime.substring(1, 2)) & 15)));
                            DeviceControlActivity.this.MakeTxFramePass(DeviceControlActivity.CMD2_REQ_CTR_FANTIME, (byte) 1, cArr);
                        } else if (DeviceControlActivity.txDcommReqSumPwr) {
                            cArr[0] = 0;
                            DeviceControlActivity.this.MakeTxFramePass(DeviceControlActivity.CMD_REQ_SUMPWR, (byte) 1, cArr);
                        } else {
                            boolean z = DeviceControlActivity.this.txDcommTest1;
                        }
                    }
                }
                try {
                    if (DeviceControlActivity.this.txRetryCnt > 1) {
                        Thread.sleep(500);
                    } else {
                        Thread.sleep(300);
                    }
                } catch (Exception e) {
                    e.getStackTrace();
                }
                DeviceControlActivity.ConnectTimeout++;
                if (DeviceControlActivity.ConnectTimeout % 240 == 0) {
                    Log.d(DeviceControlActivity.TAG, "#" + (DeviceControlActivity.ConnectTimeout / 240) + " Min");
                }
                if (DeviceControlActivity.ConnectTimeout == 2400) {
                    DeviceControlActivity.ConnectTimeout = 0;
                    Log.d(DeviceControlActivity.TAG, "Auto Disconnect IoT Switch");
                    DeviceControlActivity.this.finish();
                }
            }
            Log.d(DeviceControlActivity.TAG, "CommThread run() End!");
        }
    }

    class CommThread extends Thread {
        CommThread() {
        }

        public void run() {
            String str;
            String str2;
            String str3;
            char[] cArr = new char[20];
            while (DeviceControlActivity.isCommOk) {
                if (DeviceControlActivity.this.isTxDcommDummy) {
                    DeviceControlActivity.this.isTxDcommDummy = false;
                    Log.d(DeviceControlActivity.TAG, "isTxDcommDummy 300ms delay");
                } else if (DeviceControlActivity.this.txDcommSetWatch) {
                    Calendar instance = Calendar.getInstance();
                    String substring = MainActivity.strVerName.substring(0, 2);
                    int i = instance.get(11);
                    if (i == 0) {
                        i = 24;
                    }
                    int i2 = instance.get(12);
                    String str4 = (substring + String.format("%02d", new Object[]{Integer.valueOf(i)})) + String.format("%02d", new Object[]{Integer.valueOf(i2)});
                    int i3 = instance.get(13);
                    int i4 = instance.get(5);
                    int i5 = instance.get(7);
                    String str5 = (((str4 + String.format("%02d", new Object[]{Integer.valueOf(i3)})) + String.format("%02d", new Object[]{Integer.valueOf(instance.get(2) + 1)})) + String.format("%02d", new Object[]{Integer.valueOf(i4)})) + String.format("%02d", new Object[]{Integer.valueOf(i5)});
                    DeviceControlActivity.TxCnt++;
                    Log.d(DeviceControlActivity.TAG, "Current Time(my) = " + str5 + "  TxCnt=" + DeviceControlActivity.TxCnt);
                    int i6 = DeviceControlActivity.TxCnt;
                    if (i6 == 2 || i6 == 4 || i6 == 5 || i6 == 7 || i6 == 8 || i6 == 10 || i6 == 11) {
                        DeviceControlActivity.this.isTxDcommDummy = true;
                    } else {
                        DeviceControlActivity.this.MakeTxFrame(DeviceControlActivity.CMD2_REQ_SET_WATCH, (byte) 14, str5);
                    }
                } else {
                    String str6 = "1";
                    if (DeviceControlActivity.this.txDcommReqOtherDevInfo) {
                        DeviceControlActivity.this.MakeTxFrame(DeviceControlActivity.CMD2_REQ_OTHER_DEV, (byte) 1, str6);
                    } else if (DeviceControlActivity.this.txDcommReqSelfTest) {
                        DeviceControlActivity.this.MakeTxFrame(DeviceControlActivity.CMD2_REQ_SELFTEST, (byte) 1, str6);
                    } else if (DeviceControlActivity.this.txDcommReqDeviceInfo) {
                        DeviceControlActivity.this.MakeTxFrame((byte) 68, (byte) 1, str6);
                    } else if (DeviceControlActivity.txDcommReqWatt) {
                        DeviceControlActivity.this.MakeTxFrame((byte) 67, (byte) 1, str6);
                    } else if (DeviceControlActivity.this.txDcommReqTimeInfo) {
                        DeviceControlActivity.this.MakeTxFrame(DeviceControlActivity.CMD2_REQ_TIMEINFO, (byte) 1, str6);
                    } else if (DeviceControlActivity.this.txDcommSetLamp) {
                        cArr[0] = DeviceControlActivity.this.lampNumber;
                        switch (DeviceControlActivity.this.lampNumber) {
                            case '2':
                                cArr[1] = DeviceControlActivity.this.lampState[1];
                                break;
                            case '3':
                                cArr[1] = DeviceControlActivity.this.lampState[2];
                                break;
                            case '4':
                                cArr[1] = DeviceControlActivity.this.lampState[3];
                                break;
                            default:
                                cArr[1] = DeviceControlActivity.this.lampState[0];
                                break;
                        }
                        DeviceControlActivity.this.MakeTxFrame((byte) 65, (byte) 2, new String(cArr, 0, 2));
                    } else if (DeviceControlActivity.TxDcommSetPatten) {
                        for (int i7 = 0; i7 < 4; i7++) {
                            cArr[i7] = DeviceControlActivity.this.lampState[i7];
                        }
                        DeviceControlActivity.this.MakeTxFrame(DeviceControlActivity.CMD2_REQ_CTR_PATT, (byte) 4, new String(cArr, 0, 4));
                    } else {
                        String str7 = "0000";
                        if (DeviceControlActivity.TxDcommSetAlarm) {
                            if (FragmentCtlConvAlarm.AlarmNew) {
                                String str8 = FragmentCtlConvAlarm.select1 ? DeviceControlActivity.strTimeAlarm1 : str7;
                                if (FragmentCtlConvAlarm.select2) {
                                    str3 = str8 + DeviceControlActivity.strTimeAlarm2;
                                } else {
                                    str3 = str8 + str7;
                                }
                                if (FragmentCtlConvAlarm.select3) {
                                    str2 = str3 + DeviceControlActivity.strTimeAlarm3;
                                } else {
                                    str2 = str3 + str7;
                                }
                            } else if (FragmentCtlConvAlarm.enableAlarm) {
                                str2 = DeviceControlActivity.strTimeAlarm1 + DeviceControlActivity.strTimeAlarm2 + DeviceControlActivity.strTimeAlarm3;
                            } else {
                                str2 = "000000000000";
                            }
                            DeviceControlActivity.this.MakeTxFrame(DeviceControlActivity.CMD2_REQ_SET_WAKE, (byte) 12, str2);
                        } else if (DeviceControlActivity.TxDcommSetSleep) {
                            if (FragmentCtlConvSleep.enableSleep) {
                                str7 = DeviceControlActivity.strTimeSleep;
                            }
                            DeviceControlActivity.this.MakeTxFrame(DeviceControlActivity.CMD2_REQ_SET_SLEEP, (byte) 4, str7);
                        } else if (DeviceControlActivity.TxDcommSetBang) {
                            if (FragmentCtlConvSafe.enableSafe) {
                                str = DeviceControlActivity.strTimeSafeOn + DeviceControlActivity.strTimeSafeOff;
                            } else {
                                str = "00000000";
                            }
                            DeviceControlActivity.this.MakeTxFrame(DeviceControlActivity.CMD2_REQ_SET_BANG, (byte) 8, str);
                        } else if (!DeviceControlActivity.txDcommReqTempStatus) {
                            String str9 = "01";
                            if (DeviceControlActivity.TxDcommSetTemp) {
                                if (DeviceControlActivity.isDcomHexBCD) {
                                    if (DeviceControlActivity.strTempSetting.substring(2, 4).equals(".5")) {
                                        cArr[0] = 1;
                                    } else {
                                        cArr[0] = 0;
                                    }
                                    char parseInt = (char) (Integer.parseInt(DeviceControlActivity.strTempSetting.substring(0, 1)) << 4);
                                    cArr[1] = parseInt;
                                    cArr[1] = (char) (parseInt | ((char) (Integer.parseInt(DeviceControlActivity.strTempSetting.substring(1, 2)) & 15)));
                                    DeviceControlActivity.this.MakeTxFrame1(DeviceControlActivity.CMD2_REQ_CTR_TEMPCHG, (byte) 2, cArr);
                                } else {
                                    if (!DeviceControlActivity.strTempSetting.substring(2, 4).equals(".5")) {
                                        str9 = "00";
                                    }
                                    DeviceControlActivity.this.MakeTxFrame(DeviceControlActivity.CMD2_REQ_CTR_TEMPCHG, (byte) 4, str9 + DeviceControlActivity.strTempSetting.substring(0, 2));
                                }
                            } else if (DeviceControlActivity.TxDcommSetExit) {
                                if (DeviceControlActivity.isDcomHexBCD) {
                                    if (FragmentControlTemp.enableTempExit) {
                                        cArr[0] = 1;
                                    } else {
                                        cArr[0] = 0;
                                    }
                                    DeviceControlActivity.this.MakeTxFrame1(DeviceControlActivity.CMD2_REQ_CTR_TEMPEXIT, (byte) 1, cArr);
                                } else {
                                    if (!FragmentControlTemp.enableTempExit) {
                                        str6 = "0";
                                    }
                                    DeviceControlActivity.this.MakeTxFrame(DeviceControlActivity.CMD2_REQ_CTR_TEMPEXIT, (byte) 1, str6);
                                }
                            } else if (DeviceControlActivity.txDcommReqAirconStatus) {
                                if (DeviceControlActivity.isDcomHexBCD) {
                                    cArr[0] = 1;
                                    DeviceControlActivity.this.MakeTxFrame1(DeviceControlActivity.CMD2_REQ_RSTS_AIRC, (byte) 1, cArr);
                                } else {
                                    DeviceControlActivity.this.MakeTxFrame(DeviceControlActivity.CMD2_REQ_RSTS_AIRC, (byte) 1, str6);
                                }
                            } else if (DeviceControlActivity.TxDcommSetTempAir) {
                                if (DeviceControlActivity.isDcomHexBCD) {
                                    cArr[0] = 0;
                                    char parseInt2 = (char) (Integer.parseInt(DeviceControlActivity.strTAirSetting.substring(0, 1)) << 4);
                                    cArr[1] = parseInt2;
                                    cArr[1] = (char) (parseInt2 | ((char) (Integer.parseInt(DeviceControlActivity.strTAirSetting.substring(1, 2)) & 15)));
                                    DeviceControlActivity.this.MakeTxFrame1(DeviceControlActivity.CMD2_REQ_CTR_AIRCHG, (byte) 2, cArr);
                                } else {
                                    if (!DeviceControlActivity.strTAirSetting.substring(2, 4).equals(".5")) {
                                        str9 = "00";
                                    }
                                    DeviceControlActivity.this.MakeTxFrame(DeviceControlActivity.CMD2_REQ_CTR_AIRCHG, (byte) 4, str9 + DeviceControlActivity.strTAirSetting.substring(0, 2));
                                }
                            } else if (DeviceControlActivity.TxDcommSetPwrAir) {
                                if (DeviceControlActivity.isDcomHexBCD) {
                                    if (FragmentControlAircon.enableAirPwr) {
                                        cArr[0] = 1;
                                    } else {
                                        cArr[0] = 0;
                                    }
                                    DeviceControlActivity.this.MakeTxFrame1(DeviceControlActivity.CMD2_REQ_CTR_AIRPWR, (byte) 1, cArr);
                                } else {
                                    if (!FragmentControlAircon.enableAirPwr) {
                                        str6 = "0";
                                    }
                                    DeviceControlActivity.this.MakeTxFrame(DeviceControlActivity.CMD2_REQ_CTR_AIRPWR, (byte) 1, str6);
                                }
                            } else if (DeviceControlActivity.TxDcommSetWindAir) {
                                if (DeviceControlActivity.isDcomHexBCD) {
                                    cArr[0] = (char) FragmentControlAircon.stepWind;
                                    DeviceControlActivity.this.MakeTxFrame1(DeviceControlActivity.CMD2_REQ_CTR_AIRWIND, (byte) 1, cArr);
                                } else {
                                    DeviceControlActivity.this.MakeTxFrame(DeviceControlActivity.CMD2_REQ_CTR_AIRWIND, (byte) 1, String.valueOf(FragmentControlAircon.stepWind));
                                }
                            } else if (DeviceControlActivity.this.txDcommTest1) {
                                DeviceControlActivity.this.MakeTxFrame((byte) 121, (byte) 8, "12345678");
                            }
                        } else if (DeviceControlActivity.isDcomHexBCD) {
                            cArr[0] = 1;
                            DeviceControlActivity.this.MakeTxFrame1(DeviceControlActivity.CMD2_REQ_RSTS_TEMP, (byte) 1, cArr);
                        } else {
                            DeviceControlActivity.this.MakeTxFrame(DeviceControlActivity.CMD2_REQ_RSTS_TEMP, (byte) 1, str6);
                        }
                    }
                }
                try {
                    if (DeviceControlActivity.this.txRetryCnt > 1) {
                        Thread.sleep(500);
                    } else {
                        Thread.sleep(250);
                    }
                } catch (Exception e) {
                    e.getStackTrace();
                }
                DeviceControlActivity.ConnectTimeout++;
                if (DeviceControlActivity.ConnectTimeout % 240 == 0) {
                    Log.d(DeviceControlActivity.TAG, "#" + (DeviceControlActivity.ConnectTimeout / 240) + " Min");
                }
                if (DeviceControlActivity.ConnectTimeout == 2400) {
                    DeviceControlActivity.ConnectTimeout = 0;
                    Log.d(DeviceControlActivity.TAG, "Auto Disconnect IoT Switch");
                    DeviceControlActivity.this.finish();
                }
            }
            Log.d(DeviceControlActivity.TAG, "CommThread run() End!");
        }
    }

    public static String stringToHex0x(String str) {
        String str2 = "";
        for (int i = 0; i < str.length(); i++) {
            str2 = str2 + String.format("0x%02X ", new Object[]{Integer.valueOf(str.charAt(i))});
        }
        return str2;
    }
}
