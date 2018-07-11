package jakllsch.android.cyclingsmartdemo;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import java.util.GregorianCalendar;
import java.util.UUID;


public class MainActivity extends ActionBarActivity {

    Handler myHandler;
    Thread myThread;
    BluetoothGattService cccsGattService;
    BluetoothGattCharacteristic cccdCharacteristic;

    private int displayMode = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private static final UUID CCCS_UUID = UUID.fromString("00004005-ca7e-4e00-8000-00805f9b34fb");
    private static final UUID CCCD_UUID = UUID.fromString("00004055-ca7e-4e00-8000-00805f9b34fb");
    private BluetoothGatt cccGatt;

    private int d2s(int digit) {
        final int[] segments = {
                0x3f, 0x06, 0x5b, 0x4f, 0x66, 0x6d, 0x7d, 0x07,
                0x7f, 0x6f, 0x77, 0x7c, 0x39, 0x5e, 0x79, 0x71
        };
        if (digit < 0x0 || digit > 0xf) {
            return 0x00;
        } else {
            return segments[digit];
        }
    }

    private static byte toggle;
    private byte[] digitseg = {0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
    private byte[] miscsegs = {0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};

    private static final int SEGATTR_BLANK = 0;
    private static final int SEGATTR_SOLID = 1;
    private static final int SEGATTR_SLOWF = 2;
    private static final int SEGATTR_FASTF = 3;

    private static final int SEVEN_SEGMENT_A = 0b0000001;
    private static final int SEVEN_SEGMENT_B = 0b0000010;
    private static final int SEVEN_SEGMENT_C = 0b0000100;
    private static final int SEVEN_SEGMENT_D = 0b0001000;
    private static final int SEVEN_SEGMENT_E = 0b0010000;
    private static final int SEVEN_SEGMENT_F = 0b0100000;
    private static final int SEVEN_SEGMENT_G = 0b1000000;

    private static final int SEGMENT_ALT = 12;
    private static final int SEGMENT_ABOVE = 15;
    private static final int SEGMENT_BELOW = 16;
    private static final int SEGMENT_LOCK = 17;
    private static final int SEGMENT_SIGNAL_S = 18;
    private static final int SEGMENT_SIGNAL_C = 19;
    private static final int SEGMENT_SIGNAL_WAVES = 20;
    private static final int SEGMENT_SIGNAL_H = 21;
    private static final int SEGMENT_SIGNAL_P = 22;
    private static final int SEGMENT_WHEEL = 23;
    private static final int SEGMENT_SMARTPHONE = 24;
    private static final int SEGMENT_ENVELOPE = 25;
    private static final int SEGMENT_HAND_SET = 26;
    private static final int SEGMENT_AVERAGE = 27;
    private static final int SEGMENT_MAXIMUM = 28;
    private static final int SEGMENT_TOP_COLON = 29;
    private static final int SEGMENT_BPM = 30;
    private static final int SEGMENT_RPM = 31;
    private static final int SEGMENT_K = 32;
    private static final int SEGMENT_M_H = 33;
    private static final int SEGMENT_ODO = 34;
    private static final int SEGMENT_TM = 35;
    private static final int SEGMENT_HEART = 36;
    private static final int SEGMENT_DST = 37;
    private static final int SEGMENT_DST2 = 38;
    private static final int SEGMENT_RIGHT_S = 39;
    private static final int SEGMENT_CADENCE = 40;
    private static final int SEGMENT_W = 41;
    private static final int SEGMENT_LEFT_S = 42;
    private static final int SEGMENT_CLOCK = 43;
    private static final int SEGMENT_AM = 44;
    private static final int SEGMENT_PM = 45;
    private static final int SEGMENT_LEFT_COLON = 46;
    private static final int SEGMENT_UPPER_COLON = 47;
    private static final int SEGMENT_LOWER_COLON = 48;
    private static final int SEGMENT_LEFT_DECIMAL = 49;
    private static final int SEGMENT_RIGHT_DECIMAL = 50;
    private static final int SEGMENT_ONE_HUNDRED = 51;
    private static final int SEGMENT_NOTIFICATION = 52;
    private static final int SEGMENT_NOTIFICATION_MAIL = 53;
    private static final int SEGMENT_NOTIFICATION_CALL = 54;

    private void initsegs() {
        int i;

        digitseg[0] = 0x00;
        miscsegs[0] = 0x01;

        for (i = 0; i < 56; i++) {
            if (i < 10)
                set_digitseg(i, 0x00, SEGATTR_BLANK);
            else
                set_miscsegs(i, SEGATTR_BLANK);
        }
    }

    private void set_miscsegs(int segment, int attribute) {
        final int NBBY = 8;
        int index_bits;
        int index_byte;

        if (segment < 0 || segment > 55)
            return;

        index_bits = segment * 2;
        index_byte = index_bits / NBBY;
        index_bits %= NBBY;

        miscsegs[1 + index_byte] &= ~(0b11 << index_bits);
        miscsegs[1 + index_byte] |= (attribute & 0b11) << index_bits;
    }

    private void set_digitseg(int digit, int sevenseg, int attribute) {
        if (digit < 0 || digit > 9)
            return;
        if (sevenseg == 0x00) {
            attribute = SEGATTR_BLANK;
        }
        digitseg[1 + digit] = (byte)sevenseg;
        set_miscsegs(digit, attribute);
    }

    private void updateClock() {
        final GregorianCalendar now = new GregorianCalendar();
        final boolean twentyfour = true;
        int hour, mins, secs;

        set_miscsegs(SEGMENT_CLOCK, SEGATTR_SOLID); /* clock icon */
        set_miscsegs(SEGMENT_AM, SEGATTR_BLANK);
        set_miscsegs(SEGMENT_PM, SEGATTR_BLANK);
        if (twentyfour == false) {
            hour = now.get(GregorianCalendar.HOUR);
            if (now.get(GregorianCalendar.AM_PM) == GregorianCalendar.AM) {
                set_miscsegs(SEGMENT_AM, SEGATTR_SOLID);
            } else if (now.get(GregorianCalendar.AM_PM) == GregorianCalendar.PM) {
                set_miscsegs(SEGMENT_PM, SEGATTR_SOLID);
            }
        } else {
            hour = now.get(GregorianCalendar.HOUR_OF_DAY);
        }
        mins = now.get(GregorianCalendar.MINUTE);
        secs = now.get(GregorianCalendar.SECOND);

        final int hour_10s = hour / 10 % 10;
        final int hour_1s = hour / 1 % 10;
        final int mins_10s = mins / 10 % 10;
        final int mins_1s = mins / 1 % 10;
        final int secs_10s = secs / 10 % 10;
        final int secs_1s = secs / 1 % 10;

        if (twentyfour == false) {
            if (hour_10s > 0) {
                set_miscsegs(SEGMENT_ONE_HUNDRED, SEGATTR_SOLID);

            } else {
                set_miscsegs(SEGMENT_ONE_HUNDRED, SEGATTR_BLANK);
            }
            set_digitseg(5 + 0, d2s(hour_1s), SEGATTR_SOLID);
            set_miscsegs(SEGMENT_LEFT_COLON, SEGATTR_SOLID);
            set_digitseg(5 + 1, d2s(mins_10s), SEGATTR_SOLID);
            set_digitseg(5 + 2, d2s(mins_1s), SEGATTR_SOLID);
            set_miscsegs(SEGMENT_LEFT_DECIMAL, SEGATTR_SOLID);
            set_digitseg(5 + 3, d2s(secs_10s), SEGATTR_SOLID);
            set_digitseg(5 + 4, d2s(secs_1s), SEGATTR_SOLID);
        } else {
            set_digitseg(5 + 0, d2s(hour_10s), SEGATTR_SOLID);
            set_digitseg(5 + 1, d2s(hour_1s), SEGATTR_SOLID);
            set_miscsegs(SEGMENT_LOWER_COLON, SEGATTR_SOLID);
            set_digitseg(5 + 2, d2s(mins_10s), SEGATTR_SOLID);
            set_digitseg(5 + 3, d2s(mins_1s), SEGATTR_SOLID);
            set_miscsegs(SEGMENT_RIGHT_DECIMAL, SEGATTR_BLANK);
            set_digitseg(5 + 4, 0x00, SEGATTR_BLANK);
        }
    }

    private void updateTopValue(int value) {
        final int hundreds = value / 10000 % 10;
        final int tens = value / 1000 % 10;
        final int ones = value / 100 % 10;
        final int tenths = value / 10 % 10;
        final int hundredths = value / 1 % 10;

        set_digitseg(0 + 0, d2s(hundreds), (value >= 10000) ? SEGATTR_SOLID : SEGATTR_BLANK);
        set_digitseg(0 + 1, d2s(tens), (value >= 1000) ? SEGATTR_SOLID : SEGATTR_BLANK);
        set_digitseg(0 + 2, d2s(ones), SEGATTR_SOLID);
        set_digitseg(0 + 3, d2s(tenths), SEGATTR_SOLID);
        set_digitseg(0 + 4, d2s(hundredths), SEGATTR_BLANK);
    }

    private void updateLeftValue(int value) {
        final int hundreds = value / 100 % 10;
        final int tens = value / 10 % 10;
        final int ones = value / 1 % 10;

        set_miscsegs(SEGMENT_ONE_HUNDRED, (value >= 100) ? SEGATTR_SOLID : SEGATTR_BLANK);
        set_digitseg(5+0, d2s(tens), (value >= 10) ? SEGATTR_SOLID : SEGATTR_BLANK);
        set_digitseg(5+1, d2s(ones), SEGATTR_SOLID);
    }

    private void updateRightValue(int value) {
        final int hundreds = value / 100 % 10;
        final int tens = value / 10 % 10;
        final int ones = value / 1 % 10;

        set_digitseg(7 + 0, d2s(hundreds), (value >= 100) ? SEGATTR_SOLID : SEGATTR_BLANK);
        set_digitseg(7 + 1, d2s(tens), (value >= 10) ? SEGATTR_SOLID : SEGATTR_BLANK);
        set_digitseg(7 + 2, d2s(ones), SEGATTR_SOLID);
    }

    private void updateBottomValue(int value) {
        final int hundred_thousands = value / 100000 % 10;
        final int ten_thousands = value / 10000 % 10;
        final int thousands = value / 1000 % 10;
        final int hundreds = value / 100 % 10;
        final int tens = value / 10 % 10;
        final int ones = value / 1 % 10;

        set_miscsegs(SEGMENT_ONE_HUNDRED, (value >= 100000) ? SEGATTR_SOLID : SEGATTR_BLANK);
        set_digitseg(5 + 0, d2s(ten_thousands), (value >= 10000) ? SEGATTR_SOLID : SEGATTR_BLANK);
        set_digitseg(5 + 1, d2s(thousands), (value >= 1000) ? SEGATTR_SOLID : SEGATTR_BLANK);
        value = 100; /* XXX */
        set_digitseg(5 + 2, d2s(hundreds), (value >= 100) ? SEGATTR_SOLID : SEGATTR_BLANK);
        set_digitseg(5 + 3, d2s(tens), (value >= 10) ? SEGATTR_SOLID : SEGATTR_BLANK);
        set_digitseg(5 + 4, d2s(ones), (value >= 0) ? SEGATTR_SOLID : SEGATTR_BLANK);
    }

    private void updateTopSpeed(double speed, boolean k) {
        int value = (int)(speed * 10.0 * 2.0);
        value = value / 2 + (value & 1);
        updateTopValue(value * 10);
        set_miscsegs(SEGMENT_K, k ? SEGATTR_SOLID : SEGATTR_BLANK);
        set_miscsegs(SEGMENT_M_H, SEGATTR_SOLID);
    }

    private void updateLeft(int unitsegment, int value) {
        updateLeftValue(value);
        set_miscsegs(unitsegment, SEGATTR_SOLID);
    }

    private void updateRight(int unitsegment, int value) {
        updateRightValue(value);
        set_miscsegs(unitsegment, SEGATTR_SOLID);
    }

    private void updateBottomDistance(double distance) {
        int value = (int)(distance * 200.0);
        value = value / 2 + (value & 1);
        updateBottomValue(value);
        set_miscsegs(SEGMENT_LEFT_DECIMAL, SEGATTR_SOLID);
        set_miscsegs(SEGMENT_DST, SEGATTR_SOLID);
    }

    private void updateBottomRideTime(int time) {
        final int hours = time / 3600;
        final int minutes = time / 60 % 60;
        final int seconds = time / 1 % 60;

        final int hour_10s = hours / 10 % 10;
        final int hour_1s = hours / 1 % 10;
        final int mins_10s = minutes / 10 % 10;
        final int mins_1s = minutes / 1 % 10;
        final int secs_10s = seconds / 10 % 10;
        final int secs_1s = seconds / 1 % 10;

        if (hour_10s > 0) {
            set_miscsegs(SEGMENT_ONE_HUNDRED, SEGATTR_SOLID);

        } else {
            set_miscsegs(SEGMENT_ONE_HUNDRED, SEGATTR_BLANK);
        }
        set_digitseg(5 + 0, d2s(hour_1s), SEGATTR_SOLID);
        set_miscsegs(SEGMENT_LEFT_COLON, SEGATTR_SOLID);
        set_digitseg(5 + 1, d2s(mins_10s), SEGATTR_SOLID);
        set_digitseg(5 + 2, d2s(mins_1s), SEGATTR_SOLID);
        set_miscsegs(SEGMENT_LEFT_DECIMAL, SEGATTR_SOLID);
        set_digitseg(5 + 3, d2s(secs_10s), SEGATTR_SOLID);
        set_digitseg(5 + 4, d2s(secs_1s), SEGATTR_SOLID);

        set_miscsegs(SEGMENT_TM, SEGATTR_SOLID);
    }

    private void updateDisplay() {
        initsegs();

        updateTopSpeed(0.15, true);

        if (displayMode == 0) {
            set_miscsegs(SEGMENT_LOWER_COLON, SEGATTR_SOLID);
            set_miscsegs(SEGMENT_UPPER_COLON, SEGATTR_SOLID);
            updateLeft(SEGMENT_HEART, 0);
            updateRight(SEGMENT_CADENCE, 10);
        } else if (displayMode == 1) {
            updateBottomDistance(.005);
        } else if (displayMode == 2) {
            updateBottomRideTime(54321);
        } else {
            updateClock();
        }
    }

    BluetoothGattCallback cccGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(final BluetoothGatt gatt, final int status, final int newState) {
            Log.i(MainActivity.class.getSimpleName(), "onConnectionStateChange gatt " + gatt + " status " + status + " newState " + newState);
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                gatt.discoverServices();
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            Log.i(MainActivity.class.getSimpleName(), "onServicesDiscovered gatt " + gatt + " status " + status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                cccsGattService = gatt.getService(CCCS_UUID);
                cccdCharacteristic = cccsGattService.getCharacteristic(CCCD_UUID);
                Log.i(MainActivity.class.getSimpleName(), "service " + cccsGattService + " characteristic " + cccdCharacteristic);

                toggle = 0;
                boolean ret;

                Log.i(MainActivity.class.getSimpleName(), "instance " + cccdCharacteristic.getInstanceId());
                updateDisplay();
                cccdCharacteristic.setValue(digitseg);
                ret = cccGatt.writeCharacteristic(cccdCharacteristic);
                Log.i(MainActivity.class.getSimpleName(), "write0 returned " + ret);

            } else {
                Log.w(MainActivity.class.getSimpleName(), "onServicesDiscovered received: " + status);
            }
            Log.i(MainActivity.class.getSimpleName(), "onServicesDiscovered gatt " + gatt + " done");
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic, int status) {
            //Log.i(MainActivity.class.getSimpleName(), "onCharacteristicWrite gatt " + gatt + " characteristic " + characteristic + " status " + status);
            if (characteristic.getUuid().equals(CCCD_UUID)) {
                if ((toggle & 0x01) == 0) {
                    boolean ret;
                    toggle = 0x01;
                    characteristic.setValue(miscsegs);
                    ret = cccGatt.writeCharacteristic(characteristic);
                    //Log.i(MainActivity.class.getSimpleName(), "write2 returned " + ret);

                    myHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            boolean ret;
                            displayMode++;
                            if (displayMode >= 4) {
                                displayMode = 0;
                            }
                            updateDisplay();
                            toggle = 0x00;
                            cccdCharacteristic.setValue(digitseg);
                            ret = cccGatt.writeCharacteristic(cccdCharacteristic);
                            //Log.i(MainActivity.class.getSimpleName(), "write1 returned " + ret);
                        }
                    }, 1000);
                }
            }
            //Log.i(MainActivity.class.getSimpleName(), "onCharacteristicWrite gatt " + gatt + " characteristic " + characteristic + " status " + status + " done");
        }

    };

    public void buttonOnClick(View view) {
        System.out.println("buttonOnClick");
        Context context = getApplicationContext();
        BluetoothAdapter bluetoothAdapter = ((BluetoothManager)context.getSystemService(Activity.BLUETOOTH_SERVICE)).getAdapter();
        BluetoothDevice cccDevice = bluetoothAdapter.getRemoteDevice("CA:7E:4E:02:19:7D");

        myThread = new Thread() {
            @Override
            public void run() {
                Looper.prepare();
                myHandler = new Handler();
                Looper.loop();
            }
        };

        myThread.start();

        cccGatt = cccDevice.connectGatt(context, true, cccGattCallback);
    }
}
