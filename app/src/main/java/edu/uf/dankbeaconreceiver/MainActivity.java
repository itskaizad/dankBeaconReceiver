package edu.uf.dankbeaconreceiver;

import android.Manifest;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.neovisionaries.bluetooth.ble.advertising.ADPayloadParser;
import com.neovisionaries.bluetooth.ble.advertising.ADStructure;
import com.neovisionaries.bluetooth.ble.advertising.IBeacon;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity //implements BluetoothAdapter.LeScanCallback
{
    // Stops scanning after 10 seconds.
    private static final int PERMISSION_REQUEST_COARSE_LOCATION = 1;
    private static final int REQUEST_ENABLE_BT = 8;
    static String URL;
    static String BEACON_UUID;
    private static final long SCAN_PERIOD = 10000;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;
    private boolean mScanning;
    private Handler mHandler;

    public TextView deviceCount, progressText;
    public Button scanToggleButton;
    public EditText urlField, uuidField;
    Context ctxt;

    HashMap<String, String> uids = new HashMap<>();

    static RequestQueue requestQueue;

    Set<BluetoothDevice> beacons;
    private ArrayList<ScanFilter> filters;
    private ScanSettings settings;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        uids.put("E4:AE:DF:33:31:59", "1234");
        uids.put("E0:23:10:18:4D:C3", "4321");
        uids.put("C1:79:C8:58:37:A3", "6789");
        uids.put("F4:0F:C2:B0:B7:A2", "9876");

        beacons = new HashSet<BluetoothDevice>();

        requestQueue = Volley.newRequestQueue(this);

        ctxt = this;
        deviceCount = (TextView)findViewById(R.id.deviceCountView);
        progressText = (TextView)findViewById(R.id.statusText);
        scanToggleButton = (Button)findViewById(R.id.startStopButton);
        urlField = (EditText)findViewById(R.id.urlTextBox);
        uuidField = (EditText)findViewById(R.id.uuidTextBox);

        URL = urlField.getText().toString();
        BEACON_UUID = uuidField.getText().toString();

        urlField.setSelectAllOnFocus(true);
        urlField.addTextChangedListener(new TextWatcher()
        {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {}

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2)
            {
                URL = charSequence.toString();
            }

            @Override
            public void afterTextChanged(Editable editable) {}
        });

        uuidField.addTextChangedListener(new TextWatcher()
        {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                BEACON_UUID = charSequence.toString();
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }
        });

        //Get permission BS
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
        {
            // Android M Permission check 
            if (this.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            {
                final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("This app needs location access");
                builder.setMessage("Please grant location access so this app can detect beacons.");
                builder.setPositiveButton(android.R.string.ok, null);
                builder.setOnDismissListener(new DialogInterface.OnDismissListener()
                {
                    public void onDismiss(DialogInterface dialog) {
                    requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSION_REQUEST_COARSE_LOCATION);
                    }
                });
                builder.show();
            }
        }

        // Use this check to determine whether BLE is supported on the device. Then
        // you can selectively disable BLE-related features.
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "This app requires Bluetooth LE!", Toast.LENGTH_SHORT).show();
            finish();
        }

        // Initializes Bluetooth adapter.
        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        bluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();
        settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();
        filters = new ArrayList<ScanFilter>();

        // Ensures Bluetooth is available on the device and it is enabled. If not,
        // displays a dialog requesting user permission to enable Bluetooth.
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }

        //Toggle scan on and off on button click.
        scanToggleButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(mScanning)
                {
                    //mBluetoothAdapter.stopLeScan((BluetoothAdapter.LeScanCallback) ctxt);
                    bluetoothLeScanner.stopScan(mScanCallback);
                    progressText.setText("SCAN STOPPED");
                    scanToggleButton.setText("START SCAN");
                    //deviceCount.setText(beacons.size() + "");
                    mScanning = false;
                    Toast.makeText(ctxt, "Beacons found last: " + beacons.size(), Toast.LENGTH_SHORT).show();
                }
                else
                {
                    mScanning = true;
                    beacons = new HashSet<>();  //Reset before a fresh scan!
                    //mBluetoothAdapter.startLeScan((BluetoothAdapter.LeScanCallback) ctxt);
                    bluetoothLeScanner.startScan(filters, settings, mScanCallback);
                    progressText.setText("SCANNING");
                    scanToggleButton.setText("STOP SCAN");
                    deviceCount.setText("0");
                }
            }
        });

    }

    @Override
    protected void onStop() {
        super.onStop();
        if(bluetoothLeScanner != null)
            bluetoothLeScanner.stopScan(mScanCallback);
    }

    private ScanCallback mScanCallback = new ScanCallback()
    {
        @Override
        public void onScanResult(int callbackType, ScanResult result)
        {
            Log.i("callbackType", String.valueOf(callbackType));
            Log.i("result", result.toString());
            final BluetoothDevice bluetoothDevice = result.getDevice();
            ScanRecord scanRecord = result.getScanRecord();
            byte[] bytes = scanRecord.getBytes();

            // Parse the payload of the advertising packet.
            List<ADStructure> structures = ADPayloadParser.getInstance().parse(bytes);

            // For each AD structure contained in the advertising packet.
            //There will probably be only one for our project.
            for (ADStructure structure : structures)
            {
                if (structure instanceof IBeacon)
                {
                    // iBeacon was found.
                    IBeacon iBeacon = (IBeacon) structure;

                    // Proximity UUID, major number, minor number and power.
                    UUID uuid = iBeacon.getUUID();

                    if(!uuid.toString().equals(BEACON_UUID))
                        return;

                    Log.i("UUID", bluetoothDevice.getName() + " ---- " + uuid.toString());

                    if(beacons.contains(bluetoothDevice))
                        return;

                    beacons.add(bluetoothDevice);

                    int cnt = Integer.parseInt(deviceCount.getText().toString());
                    deviceCount.setText(cnt + 1 + "");

                    Toast.makeText(ctxt, bluetoothDevice.getName() + " " + uuid, Toast.LENGTH_SHORT).show();

                    Log.d("[OMG-------]", bluetoothDevice.getName() + " and " + bluetoothDevice.getAddress());
                    Log.d("[OMG----------]", scanRecord.getDeviceName() + " and " + bluetoothDevice.getAddress());
                    postBeaconWithNameUUID(bluetoothDevice, uuid);
                }
            }
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            for (ScanResult sr : results) {
                Log.i("ScanResult - Results", sr.toString());
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.e("Scan Failed", "Error Code: " + errorCode);
        }
    };

    //For Android ver. < 5.1 only.
//    @Override
//    public void onLeScan(final BluetoothDevice bluetoothDevice, int i, byte[] bytes)
//    {
//
//        if(beacons.contains(bluetoothDevice))
//            return;
//
//        beacons.add(bluetoothDevice);
//
//        // Parse the payload of the advertising packet.
//        List<ADStructure> structures = ADPayloadParser.getInstance().parse(bytes);
//
//        // For each AD structure contained in the advertising packet.
//        //There will probably be only one for our project.
//        for (ADStructure structure : structures)
//        {
//            if (structure instanceof IBeacon)
//            {
//                // iBeacon was found.
//                IBeacon iBeacon = (IBeacon) structure;
//
//                // Proximity UUID, major number, minor number and power.
//                UUID uuid = iBeacon.getUUID();
//
////                int cnt = Integer.parseInt(deviceCount.getText().toString());
////                deviceCount.setText(cnt + 1);
//                Toast.makeText(ctxt, bluetoothDevice.getName() + " " + uuid, Toast.LENGTH_SHORT).show();
//
//                postBeaconWithNameUUID(bluetoothDevice.getName(), uuid);
//            }
//        }
//    }

    private void postBeaconWithNameUUID(final BluetoothDevice bd, final UUID uuid)
    {
        StringRequest postRequest = new StringRequest(Request.Method.POST, URL,
                new Response.Listener<String>()
                {
                    @Override
                    public void onResponse(String response)
                    {
                        try {
                            JSONObject jsonResponse = new JSONObject(response).getJSONObject("form");
                            String site = jsonResponse.getString("site"),
                                    network = jsonResponse.getString("network");
                            System.out.println("Site: "+site+"\nNetwork: "+network);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        error.printStackTrace();
                    }
                }
        ) {
            @Override
            protected Map<String, String> getParams()
            {
                Map<String, String>  params = new HashMap<>();
                // the POST parameters:
                if(bd.getName() == null)
                    params.put("name", "");
                else
                    params.put("name", bd.getName());
                params.put("uuid", uuid.toString());
                params.put("address", bd.getAddress());
                params.put("mbedid", uids.get(bd.getAddress()));
                return params;
            }
        };

        requestQueue.add(postRequest);
    }

    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults)
    {
        switch (requestCode) {
            case PERMISSION_REQUEST_COARSE_LOCATION: {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED)
                {
                    Log.d("[BEEKS]", "coarse location permission granted");
                } else
                {
                    final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle("Functionality limited");
                    builder.setMessage("Since location access has not been granted, this app will not be able to discover beacons when in the background.");
                    builder.setPositiveButton(android.R.string.ok, null);
                    builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                        @Override
                        public void onDismiss(DialogInterface dialog) {
                        }
                    });
                    builder.show();
                }
                return;
            }
        }
    }
}
