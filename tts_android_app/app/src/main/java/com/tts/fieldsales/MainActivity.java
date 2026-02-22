package com.tts.fieldsales;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private ProgressBar progressBar;
    private SharedPreferences prefs;
    
    // Default URL if not set
    private static final String DEFAULT_URL = "https://your-odoo-instance.com/web#action=tts_field_sales.action_tts_field_sales";
    private static final String PREF_URL_KEY = "odoo_url";

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webView);
        progressBar = findViewById(R.id.progressBar);
        prefs = getSharedPreferences("TTS_Prefs", MODE_PRIVATE);

        setupWebView();

        // Check if URL is saved, otherwise ask user
        String savedUrl = prefs.getString(PREF_URL_KEY, "");
        if (savedUrl.isEmpty()) {
            promptForUrl();
        } else {
            webView.loadUrl(savedUrl);
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setCacheMode(WebSettings.LOAD_DEFAULT);
        webSettings.setAllowFileAccess(true);

        // Inject the Javascript Interface
        webView.addJavascriptInterface(new WebAppInterface(this), "AndroidApp");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                view.loadUrl(request.getUrl().toString());
                return false;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                progressBar.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                progressBar.setVisibility(View.GONE);
            }
        });

        webView.setWebChromeClient(new WebChromeClient());
    }

    public void promptForUrl() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Enter Odoo App URL");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);

        final EditText urlInput = new EditText(this);
        String currentUrl = prefs.getString(PREF_URL_KEY, DEFAULT_URL);
        urlInput.setText(currentUrl);
        layout.addView(urlInput);

        builder.setView(layout);

        builder.setPositiveButton("Save", (dialog, which) -> {
            String newUrl = urlInput.getText().toString().trim();
            if (!newUrl.isEmpty()) {
                if (!newUrl.startsWith("http")) {
                    newUrl = "https://" + newUrl;
                }
                prefs.edit().putString(PREF_URL_KEY, newUrl).apply();
                webView.loadUrl(newUrl);
            }
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.setCancelable(false);
        builder.show();
    }

    // Handles the device back button to navigate WebView history instead of closing app
    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    /**
     * Interface to bridge Javascript in the WebView to Android Java code
     */
    public class WebAppInterface {
        Context mContext;

        WebAppInterface(Context c) {
            mContext = c;
        }

        /**
         * The Odoo JS code will call this to print the HTML template.
         * Example usage in JS: window.AndroidApp.printThermalReceipt("<html>...</html>");
         */
        @JavascriptInterface
        public void printThermalReceipt(String htmlContent) {
            Log.d("TTS_PRINT", "Received Print request from WebApp");
            
            // Post to main thread to handle the hidden WebView rendering
            new Handler(Looper.getMainLooper()).post(() -> {
                Toast.makeText(mContext, "Preparing Print...", Toast.LENGTH_SHORT).show();
                
                // Launch the Print Manager Activity/Service here
                Intent printIntent = new Intent(mContext, PrintManagerActivity.class);
                printIntent.putExtra("HTML_DATA", htmlContent);
                
                SharedPreferences prefs = mContext.getSharedPreferences("TTS_Prefs", MODE_PRIVATE);
                String defaultPrinterMac = prefs.getString("default_printer_mac", null);
                if (defaultPrinterMac != null && !defaultPrinterMac.isEmpty()) {
                    printIntent.putExtra("PRINTER_MAC", defaultPrinterMac);
                }
                
                mContext.startActivity(printIntent);
            });
        }

        @JavascriptInterface
        public void selectBluetoothPrinter() {
            new Handler(Looper.getMainLooper()).post(() -> {
                Intent selectIntent = new Intent(mContext, PrinterSelectionActivity.class);
                mContext.startActivity(selectIntent);
            });
        }
        
        /**
         * Optional: Allow Odoo JS to trigger the URL change prompt
         */
        @JavascriptInterface
        public void openSettings() {
             new Handler(Looper.getMainLooper()).post(MainActivity.this::promptForUrl);
        }
    }
}
