package fr.deleguefootball.android8;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.media.MediaScannerConnection;
import android.widget.Toast;
import android.print.PrintManager;
import android.webkit.JavascriptInterface;
import android.print.PrintDocumentAdapter;
import android.content.Context;
import android.util.Base64;
import android.content.ContentValues;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.IOException;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private WebView webView;
    private ValueCallback<Uri[]> fileCallback;
    private Uri cameraOutputUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (android.os.Build.VERSION.SDK_INT >= 23) {
            java.util.ArrayList<String> needed = new java.util.ArrayList<>();
            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
                needed.add(Manifest.permission.CAMERA);
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
                needed.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            if (!needed.isEmpty()) requestPermissions(needed.toArray(new String[0]), 1001);
        }

        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        webView = new WebView(this);
        webView.setFocusable(true);
        webView.setFocusableInTouchMode(true);
        setContentView(webView);
        webView.requestFocus();

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setSupportZoom(false);
        settings.setLoadsImagesAutomatically(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setUserAgentString(settings.getUserAgentString() + " DelegueFootballAndroid8/1");

        CookieManager.getInstance().setAcceptCookie(true);
        webView.addJavascriptInterface(new AndroidBridge(), "AndroidBridge");

        webView.setWebViewClient(new android.webkit.WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return false;
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                try {
                    if (params.isCaptureEnabled() && acceptsImages(params)) {
                        launchCamera();
                    } else {
                        Intent intent = params.createIntent();
                        startActivityForResult(intent, 2001);
                    }
                    return true;
                } catch (Exception e) {
                    fileCallback = null;
                    callback.onReceiveValue(null);
                    return false;
                }
            }
        });

        webView.loadUrl("file:///android_asset/index.html");
    }

    private class AndroidBridge {
        @JavascriptInterface
        public void editMinute(String boxId, int index, String currentValue) {
            runOnUiThread(() -> {
                android.widget.EditText input = new android.widget.EditText(MainActivity.this);
                input.setSingleLine(true);
                input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
                input.setText(currentValue == null ? "" : currentValue);
                input.setSelection(input.getText().length());
                input.setHint("Ex. 45 ou 45+2");
                android.widget.LinearLayout layout = new android.widget.LinearLayout(MainActivity.this);
                layout.setOrientation(android.widget.LinearLayout.VERTICAL);
                int pad = (int) (20 * getResources().getDisplayMetrics().density);
                layout.setPadding(pad, pad / 2, pad, 0);
                layout.addView(input);
                new android.app.AlertDialog.Builder(MainActivity.this)
                    .setTitle("Corriger la minute")
                    .setMessage("Entrez la minute, par exemple 45 ou 45+2.")
                    .setView(layout)
                    .setNegativeButton("Annuler", (dialog, which) -> dialog.dismiss())
                    .setPositiveButton("Enregistrer", (dialog, which) -> {
                        String value = input.getText().toString().trim();
                        String safeBox = "goalHistory".equals(boxId) ? "goalHistory" :
                                         ("cardHistory".equals(boxId) ? "cardHistory" : "");
                        if (safeBox.isEmpty()) return;
                        String safeValue = value.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "");
                        webView.evaluateJavascript("window.applyNativeMinuteEdit('" + safeBox + "'," + index + ",'" + safeValue + "')", null);
                    })
                    .show();
                input.requestFocus();
            });
        }

        @JavascriptInterface
        public void showKeyboard() {
            runOnUiThread(() -> {
                try {
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) {
                        webView.postDelayed(() -> imm.showSoftInput(webView, InputMethodManager.SHOW_IMPLICIT), 120);
                    }
                } catch (Exception e) {
                    android.util.Log.e("DelegueFootball", "Ouverture du clavier impossible", e);
                }
            });
        }

        @JavascriptInterface
        public void printPage() {
            runOnUiThread(() -> {
                try {
                    PrintManager printManager = (PrintManager) getSystemService(Context.PRINT_SERVICE);
                    if (printManager != null) {
                        PrintDocumentAdapter adapter = webView.createPrintDocumentAdapter("DelegueFootball");
                        printManager.print("Délégué Football", adapter, new android.print.PrintAttributes.Builder().build());
                    }
                } catch (Exception e) {
                    android.util.Log.e("DelegueFootball", "Impression impossible", e);
                }
            });
        }

        @JavascriptInterface
        public void saveImageToPictures(String base64, String filename, String mimeType) {
            runOnUiThread(() -> {
                try {
                    if (android.os.Build.VERSION.SDK_INT >= 23 &&
                            checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                        Toast.makeText(MainActivity.this, "Autorisez l’accès au stockage puis réessayez.", Toast.LENGTH_LONG).show();
                        requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1002);
                        return;
                    }
                    byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
                    File pictures = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "DelegueFootball");
                    if (!pictures.exists() && !pictures.mkdirs()) throw new IOException("Dossier photo indisponible");
                    String safeName = filename == null ? "Composition.png" : filename.replaceAll("[^a-zA-Z0-9._-]", "_");
                    File file = new File(pictures, safeName);
                    try (FileOutputStream stream = new FileOutputStream(file)) { stream.write(bytes); }
                    MediaScannerConnection.scanFile(MainActivity.this, new String[]{file.getAbsolutePath()}, new String[]{mimeType == null ? "image/png" : mimeType}, null);
                    Toast.makeText(MainActivity.this, "Photo enregistrée dans Images/DelegueFootball", Toast.LENGTH_LONG).show();
                } catch (Exception e) {
                    android.util.Log.e("DelegueFootball", "Enregistrement de la photo impossible", e);
                    Toast.makeText(MainActivity.this, "Erreur : photo non enregistrée.", Toast.LENGTH_LONG).show();
                }
            });
        }

        @JavascriptInterface
        public void shareFile(String base64, String filename, String mimeType) {
            runOnUiThread(() -> {
                try {
                    byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
                    File file = new File(getCacheDir(), filename.replaceAll("[^a-zA-Z0-9._-]", "_"));
                    try (FileOutputStream stream = new FileOutputStream(file)) {
                        stream.write(bytes);
                    }
                    Uri uri = FileProvider.getUriForFile(MainActivity.this, getPackageName() + ".fileprovider", file);
                    Intent intent = new Intent(Intent.ACTION_SEND);
                    intent.setType(mimeType);
                    intent.putExtra(Intent.EXTRA_STREAM, uri);
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(Intent.createChooser(intent, "Enregistrer ou partager le document"));
                } catch (Exception e) {
                    android.util.Log.e("DelegueFootball", "Partage du fichier impossible", e);
                }
            });
        }
    }

    private boolean acceptsImages(WebChromeClient.FileChooserParams params) {
        String[] types = params.getAcceptTypes();
        if (types == null || types.length == 0) return false;
        for (String type : types) {
            if (type != null && (type.contains("image") || type.equals("*/*"))) return true;
        }
        return false;
    }

    private void launchCamera() throws IOException {
        File directory = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        if (directory == null) throw new IOException("Dossier photo indisponible");
        if (!directory.exists() && !directory.mkdirs()) throw new IOException("Impossible de créer le dossier photo");
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.FRANCE).format(new Date());
        File photo = File.createTempFile("DELEGUE_" + stamp + "_", ".jpg", directory);
        cameraOutputUri = FileProvider.getUriForFile(
                this, getPackageName() + ".fileprovider", photo);
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, cameraOutputUri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        startActivityForResult(intent, 2002);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if ((requestCode == 2001 || requestCode == 2002) && fileCallback != null) {
            Uri[] result = null;
            if (resultCode == RESULT_OK) {
                if (requestCode == 2002 && cameraOutputUri != null) {
                    result = new Uri[]{cameraOutputUri};
                } else if (data != null && data.getClipData() != null) {
                    int n = data.getClipData().getItemCount();
                    result = new Uri[n];
                    for (int i = 0; i < n; i++) result[i] = data.getClipData().getItemAt(i).getUri();
                } else if (data != null && data.getData() != null) {
                    result = new Uri[]{data.getData()};
                }
            }
            fileCallback.onReceiveValue(result);
            fileCallback = null;
            cameraOutputUri = null;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (fileCallback != null) {
            fileCallback.onReceiveValue(null);
            fileCallback = null;
        }
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
        }
        super.onDestroy();
    }
}