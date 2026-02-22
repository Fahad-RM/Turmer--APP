package com.tts.fieldsales;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Set;

public class PrinterSelectionActivity extends Activity {

    private ListView devicesListView;
    private RadioGroup printerWidthGroup;
    private TextView statusText;
    private Button cancelBtn;
    private BluetoothAdapter bluetoothAdapter;
    private static final int PERMISSION_REQUEST_CODE = 1002;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_printer_selection);

        devicesListView = findViewById(R.id.devicesListView);
        printerWidthGroup = findViewById(R.id.printerWidthGroup);
        statusText = findViewById(R.id.statusText);
        cancelBtn = findViewById(R.id.cancelBtn);

        // Load saved width
        SharedPreferences prefs = getSharedPreferences("TTS_Prefs", MODE_PRIVATE);
        int savedWidth = prefs.getInt("printer_width_px", 576);
        if (savedWidth == 832) {
            printerWidthGroup.check(R.id.radio4inch);
        } else {
            printerWidthGroup.check(R.id.radio3inch);
        }

        cancelBtn.setOnClickListener(v -> finish());

        checkAndRequestPermissions();
    }

    private void checkAndRequestPermissions() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            if (checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{
                        android.Manifest.permission.BLUETOOTH_CONNECT,
                        android.Manifest.permission.BLUETOOTH_SCAN
                }, PERMISSION_REQUEST_CODE);
                return;
            }
        }
        showPairedDevices();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                showPairedDevices();
            } else {
                Toast.makeText(this, "Bluetooth Permission Required!", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    @SuppressLint("MissingPermission")
    private void showPairedDevices() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth not supported", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        if (!bluetoothAdapter.isEnabled()) {
            Toast.makeText(this, "Please enable Bluetooth", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        ArrayList<String> deviceNames = new ArrayList<>();
        ArrayList<BluetoothDevice> devicesList = new ArrayList<>();

        if (pairedDevices != null && pairedDevices.size() > 0) {
            for (BluetoothDevice device : pairedDevices) {
                deviceNames.add(device.getName() + "\n" + device.getAddress());
                devicesList.add(device);
            }
        } else {
            statusText.setText("No Paired Printers Found");
            return;
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, deviceNames);
        devicesListView.setAdapter(adapter);

        devicesListView.setOnItemClickListener((parent, view, position, id) -> {
            BluetoothDevice selectedDevice = devicesList.get(position);
            saveDefaultPrinter(selectedDevice);
        });
    }

    private void saveDefaultPrinter(BluetoothDevice device) {
        SharedPreferences prefs = getSharedPreferences("TTS_Prefs", MODE_PRIVATE);
        
        // Save Width
        int widthPx = 576; // Default 3"
        if (printerWidthGroup.getCheckedRadioButtonId() == R.id.radio4inch) {
            widthPx = 832; // 4"
        }
        
        prefs.edit()
                .putString("default_printer_mac", device.getAddress())
                .putInt("printer_width_px", widthPx)
                .apply();
        
        Toast.makeText(this, "Settings Saved", Toast.LENGTH_SHORT).show();
        finish();
    }
}
