package fr.deleguefootball.android8;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.print.PrintManager;
import android.webkit.JavascriptInterface;
import android.print.PrintDocumentAdapter;
import android.print.PrintAttributes;
import android.print.PrintDocumentInfo;
import android.print.PageRange;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.content.Context;
import android.util.Base64;
import android.content.ContentValues;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;

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

        if (android.os.Build.VERSION.SDK_INT >= 23 &&
                checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, 1001);
        }

        webView = new WebView(this);
        setContentView(webView);

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

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                // Le HTML attribue deux fois le bouton PDF; ce correctif natif rétablit
                // l'impression Android après l'exécution de tous les scripts de la page.
                view.evaluateJavascript("(function(){"
                    + "window.__deleguePrint=function(kind){"
                    + "try{if(kind==='report'){document.body.classList.add('print-report');}"
                    + "if(kind==='composition'){document.body.classList.add('print-composition');}"
                    + "if(kind==='sheet'){document.body.classList.add('print-sheet');}"
                    + "if(window.AndroidBridge&&window.AndroidBridge.printPage){window.AndroidBridge.printPage();}"
                    + "else{window.print();}}catch(e){alert('Impression impossible : '+e.message);}};"
                    + "var p=document.getElementById('printReport');if(p)p.onclick=function(){try{if(typeof renderReport==='function')renderReport();}catch(e){}document.body.classList.add('print-report');if(window.AndroidBridge&&window.AndroidBridge.exportPdf)window.AndroidBridge.exportPdf('rapport-match.pdf','report');else window.__deleguePrint('report');};"
                    + "var c=document.getElementById('printSheet');if(c)c.onclick=function(){try{if(typeof renderSheet==='function')renderSheet();}catch(e){}document.body.classList.add('print-sheet');if(window.AndroidBridge&&window.AndroidBridge.exportPdf)window.AndroidBridge.exportPdf('feuille-match.pdf','sheet');else window.__deleguePrint('sheet');};"
                    + "var comp=document.getElementById('printCompositionOnly');if(comp)comp.onclick=function(){document.body.classList.add('print-composition');if(window.AndroidBridge&&window.AndroidBridge.exportPdf)window.AndroidBridge.exportPdf('composition-equipes.pdf','composition');else window.__deleguePrint('composition');};"
                    + "window.addEventListener('afterprint',function(){document.body.classList.remove('print-sheet','print-composition','print-report');});"
                    + "})()", null);
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
        public String getWordTemplateBase64() {
            try (java.io.InputStream input = getAssets().open("rapport-modele.docx.docx");
                 java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
                return Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP);
            } catch (Exception e) {
                android.util.Log.e("DelegueFootball", "Lecture du modèle Word impossible", e);
                return "";
            }
        }

        @JavascriptInterface
        public void exportPdf(String filename, String kind) {
            runOnUiThread(() -> {
                try {
                    PrintManager printManager = (PrintManager) getSystemService(Context.PRINT_SERVICE);
                    if (printManager == null) {
                        android.widget.Toast.makeText(MainActivity.this, "Service PDF indisponible", android.widget.Toast.LENGTH_LONG).show();
                        return;
                    }
                    PrintDocumentAdapter adapter = webView.createPrintDocumentAdapter(filename);
                    printManager.print("Enregistrer en PDF", adapter,
                        new PrintAttributes.Builder()
                            .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                            .build());
                } catch (Exception e) {
                    android.util.Log.e("DelegueFootball", "Ouverture de l'export PDF impossible", e);
                    android.widget.Toast.makeText(MainActivity.this, "Impossible d'ouvrir l'export PDF", android.widget.Toast.LENGTH_LONG).show();
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