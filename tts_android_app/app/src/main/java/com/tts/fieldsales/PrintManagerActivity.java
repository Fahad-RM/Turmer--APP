package com.tts.fieldsales;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

public class PrintManagerActivity extends Activity {

    private WebView offscreenWebView;
    private ProgressBar printProgressBar;
    private TextView statusText;
    private LinearLayout devicesLayout;
    private ListView devicesListView;
    private Button cancelBtn;

    private String htmlDataToPrint;
    private Bitmap receiptBitmap;

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket bluetoothSocket;
    private OutputStream outputStream;

    // Standard SPP UUID for Bluetooth Serial Printers
    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private static final int PERMISSION_REQUEST_CODE = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_print_manager);

        offscreenWebView = findViewById(R.id.offscreenWebView);
        printProgressBar = findViewById(R.id.printProgressBar);
        statusText = findViewById(R.id.statusText);
        devicesLayout = findViewById(R.id.devicesLayout);
        devicesListView = findViewById(R.id.devicesListView);
        cancelBtn = findViewById(R.id.cancelBtn);

        htmlDataToPrint = getIntent().getStringExtra("HTML_DATA");

        if (htmlDataToPrint == null || htmlDataToPrint.isEmpty()) {
            Toast.makeText(this, "No data to print", Toast.LENGTH_SHORT).show();
            finish();
            return;
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
        proceedWithPrinting();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                proceedWithPrinting();
            } else {
                Toast.makeText(this, "Bluetooth Permission Required to Print!", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    private void proceedWithPrinting() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth not supported on this device", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        if (!bluetoothAdapter.isEnabled()) {
            statusText.setText("Please enable Bluetooth first.");
            Toast.makeText(this, "Please enable Bluetooth in settings", Toast.LENGTH_LONG).show();
            return;
        }

        renderHtmlToBitmap();
    }

    /**
     * 1. Load the exact HTML into a hidden WebView sized for a 58/80mm printer
     * (e.g., width 384 pixels for 58mm printer @ 203 DPI)
     */
    @SuppressLint("SetJavaScriptEnabled")
    private void renderHtmlToBitmap() {
        statusText.setText("Rendering receipt...");

        WebSettings settings = offscreenWebView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setDefaultFontSize(14); // Keep text readable but compact
        settings.setMinimumFontSize(10);
        // Force text size to normal
        settings.setTextZoom(100);

        offscreenWebView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                // Wait a tiny bit for CSS to fully apply
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    takeSnapshot();
                }, 500);
            }
        });

        // Add some basic CSS wrapper if body margins exist
        String fullHtml = "<html><head><style>body { margin: 0; padding: 0; background: white; width: 540px; }</style></head><body>" 
                          + htmlDataToPrint + "</body></html>";
                          
        offscreenWebView.loadDataWithBaseURL(null, fullHtml, "text/html", "UTF-8", null);
    }

    /**
     * 2. Take the Snapshot of the WebView content
     */
    private void takeSnapshot() {
        offscreenWebView.measure(
                View.MeasureSpec.makeMeasureSpec(540, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(10000, View.MeasureSpec.AT_MOST)
        );
        
        int width = offscreenWebView.getMeasuredWidth();
        int height = offscreenWebView.getMeasuredHeight();
        
        // Prevent IllegalArgumentException if layout hasn't fully propagated
        if (width <= 0) width = 540;
        if (height <= 0) height = 800;
        
        offscreenWebView.layout(0, 0, width, height);

        try {
            receiptBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(receiptBitmap);
            canvas.drawColor(Color.WHITE);
            offscreenWebView.draw(canvas);

            runOnUiThread(() -> {
                String targetMac = getIntent().getStringExtra("PRINTER_MAC");
                if (targetMac != null && !targetMac.isEmpty()) {
                    try {
                        BluetoothDevice device = bluetoothAdapter.getRemoteDevice(targetMac);
                        if (device != null) {
                            connectAndPrint(device);
                        } else {
                            statusText.setText("Select Printer");
                            showPairedDevices();
                        }
                    } catch (Exception e) {
                        statusText.setText("Select Printer");
                        showPairedDevices();
                    }
                } else {
                    statusText.setText("Select Printer");
                    showPairedDevices();
                }
            });
            
        } catch (Exception e) {
            runOnUiThread(() -> statusText.setText("Render Error: " + e.getMessage()));
            e.printStackTrace();
        }
    }

    /**
     * 3. Show Paired Bluetooth Devices for selection
     */
    @SuppressLint("MissingPermission")
    private void showPairedDevices() {
        printProgressBar.setVisibility(View.GONE);
        devicesLayout.setVisibility(View.VISIBLE);

        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        ArrayList<String> deviceNames = new ArrayList<>();
        ArrayList<BluetoothDevice> devicesList = new ArrayList<>();

        if (pairedDevices.size() > 0) {
            for (BluetoothDevice device : pairedDevices) {
                deviceNames.add(device.getName() + "\n" + device.getAddress());
                devicesList.add(device);
            }
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, deviceNames);
        devicesListView.setAdapter(adapter);

        devicesListView.setOnItemClickListener((parent, view, position, id) -> {
            BluetoothDevice selectedDevice = devicesList.get(position);
            connectAndPrint(selectedDevice);
        });
    }

    /**
     * 4. Connect and send bytes
     */
    @SuppressLint("MissingPermission")
    private void connectAndPrint(BluetoothDevice device) {
        devicesLayout.setVisibility(View.GONE);
        printProgressBar.setVisibility(View.VISIBLE);
        statusText.setText("Connecting to " + device.getName() + "...");

        new Thread(() -> {
            try {
                bluetoothSocket = device.createRfcommSocketToServiceRecord(SPP_UUID);
                bluetoothSocket.connect();
                outputStream = bluetoothSocket.getOutputStream();

                runOnUiThread(() -> statusText.setText("Printing..."));

                // Convert Bitmap to ESC/POS bytes 
                byte[] printData = decodeBitmap(receiptBitmap);
                
                // Initialize Printer
                outputStream.write(new byte[]{0x1B, 0x40}); 
                
                // Print Image
                outputStream.write(printData);
                
                // Feed and cut
                outputStream.write(new byte[]{0x0A, 0x0A, 0x0A, 0x0A}); 
                outputStream.write(new byte[]{0x1D, 0x56, 0x41, 0x00}); 
                
                outputStream.flush();
                bluetoothSocket.close();

                runOnUiThread(() -> {
                    Toast.makeText(PrintManagerActivity.this, "Printed Successfully", Toast.LENGTH_SHORT).show();
                    finish();
                });

            } catch (IOException e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    statusText.setText("Connection failed. Turn on printer and try again.");
                    printProgressBar.setVisibility(View.GONE);
                    devicesLayout.setVisibility(View.VISIBLE);
                });
            }
        }).start();
    }

    /**
     * 5. Convert Image to Monochrome Thermal GS v 0 byte array 
     */
    public static byte[] decodeBitmap(Bitmap bmp) {
        int bmpWidth = bmp.getWidth();
        int bmpHeight = bmp.getHeight();

        int listLen = (bmpWidth + 7) / 8;
        int maxLen = listLen * bmpHeight;
        byte[] data = new byte[maxLen + 8];

        data[0] = 0x1D; // GS
        data[1] = 0x76; // v
        data[2] = 0x30; // 0
        data[3] = 0x00; // m
        data[4] = (byte) (listLen % 256);
        data[5] = (byte) (listLen / 256);
        data[6] = (byte) (bmpHeight % 256);
        data[7] = (byte) (bmpHeight / 256);

        int k = 8;
        for (int i = 0; i < bmpHeight; i++) {
            for (int j = 0; j < listLen; j++) {
                data[k++] = 0x00;
            }
        }

        k = 8;
        for (int i = 0; i < bmpHeight; i++) {
            for (int j = 0; j < bmpWidth; j++) {
                int color = bmp.getPixel(j, i);
                int r = Color.red(color);
                int g = Color.green(color);
                int b = Color.blue(color);
                
                // Luminance calculation
                int luminance = (int) (0.299 * r + 0.587 * g + 0.114 * b);
                
                // If pixel is dark, set the bit to 1
                if (luminance < 180) {
                    data[k + j / 8] |= (1 << (7 - j % 8));
                }
            }
            k += listLen;
        }
        return data;
    }
}
