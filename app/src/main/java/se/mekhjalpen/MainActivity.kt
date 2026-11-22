package se.mekhjalpen

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintManager
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.webkit.WebViewAssetLoader
import java.io.File

// Gör att window.print() i appen går via Androids utskriftstjänst
// med rätt etikettstorlek (läses ur @page size i sidan).
private const val PRINT_JS = """
window.print=function(){
  var m=/size:\s*([\d.]+)mm\s+([\d.]+)mm/.exec((document.getElementById('pagestyle')||{}).textContent||'');
  Android.print(m?+m[1]:101.6,m?+m[2]:152.4);
};
"""

class MainActivity : AppCompatActivity() {

    private lateinit var web: WebView
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var cameraUri: Uri? = null

    private val cameraLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
            val uri = cameraUri
            fileCallback?.onReceiveValue(if (ok && uri != null) arrayOf(uri) else null)
            fileCallback = null
        }

    private val pickLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
            fileCallback?.onReceiveValue(
                WebChromeClient.FileChooserParams.parseResult(r.resultCode, r.data)
            )
            fileCallback = null
        }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        // Kant-till-kant (Android 15 på Pixel 9 Pro), transparenta systemfält
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        web = WebView(this)
        val container = FrameLayout(this)
        container.addView(
            web,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        setContentView(container)

        // Håll innehållet utanför statusfält, gestfält, hålkamera och tangentbord
        ViewCompat.setOnApplyWindowInsetsListener(container) { v, insets ->
            val b = insets.getInsets(
                WindowInsetsCompat.Type.systemBars()
                    or WindowInsetsCompat.Type.displayCutout()
                    or WindowInsetsCompat.Type.ime()
            )
            v.setPadding(b.left, b.top, b.right, b.bottom)
            WindowInsetsCompat.CONSUMED
        }

        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.allowFileAccess = false
        web.settings.allowContentAccess = true
        web.settings.setSupportZoom(false)

        val loader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? = loader.shouldInterceptRequest(request.url)

            override fun onPageFinished(view: WebView, url: String?) {
                view.evaluateJavascript(PRINT_JS, null)
            }
        }

        web.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView,
                callback: ValueCallback<Array<Uri>>,
                params: FileChooserParams
            ): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = callback
                if (params.isCaptureEnabled) {
                    val dir = File(cacheDir, "images").apply { mkdirs() }
                    val f = File.createTempFile("del_", ".jpg", dir)
                    val uri = FileProvider.getUriForFile(
                        this@MainActivity, "$packageName.fileprovider", f
                    )
                    cameraUri = uri
                    cameraLauncher.launch(uri)
                } else {
                    pickLauncher.launch(params.createIntent())
                }
                return true
            }
        }

        web.addJavascriptInterface(Bridge(), "Android")
        web.loadUrl("https://appassets.androidplatform.net/assets/index.html")

        // Bakåtgest: stäng formulär/dialog först, avsluta först när inget är öppet
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                web.evaluateJavascript("(window.__back&&window.__back())||false") { r ->
                    if (r != "true") finish()
                }
            }
        })
    }

    inner class Bridge {
        @JavascriptInterface
        fun share(json: String) {
            runOnUiThread {
                val dir = File(cacheDir, "backup").apply { mkdirs() }
                val f = File(dir, "mek-hjalpen-sakerhetskopia.json")
                f.writeText(json)
                val uri = FileProvider.getUriForFile(this@MainActivity, "$packageName.fileprovider", f)
                val i = Intent(Intent.ACTION_SEND).apply {
                    type = "application/json"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(i, "Spara säkerhetskopia"))
            }
        }

        @JavascriptInterface
        fun print(wMm: Double, hMm: Double) {
            runOnUiThread {
                val pm = getSystemService(PRINT_SERVICE) as PrintManager
                val wMils = (wMm / 25.4 * 1000).toInt()
                val hMils = (hMm / 25.4 * 1000).toInt()
                val is46 = minOf(wMm, hMm) in 100.0..103.0 && maxOf(wMm, hMm) in 151.0..154.0
                var size = if (is46) PrintAttributes.MediaSize.NA_INDEX_4X6 else PrintAttributes.MediaSize(
                    "LABEL_${wMils}x$hMils", "Etikett ${wMm.toInt()} x ${hMm.toInt()} mm",
                    minOf(wMils, hMils), maxOf(wMils, hMils)
                )
                if (wMm > hMm) size = size.asLandscape()
                val attrs = PrintAttributes.Builder()
                    .setMediaSize(size)
                    .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                    .build()
                val inner = web.createPrintDocumentAdapter("etikett")
                // Anpassa textstorleken efter den pappersstorlek som faktiskt är vald i dialogen
                val adapter = object : PrintDocumentAdapter() {
                    override fun onStart() = inner.onStart()
                    override fun onLayout(
                        oldAttributes: PrintAttributes?,
                        newAttributes: PrintAttributes,
                        cancellationSignal: CancellationSignal?,
                        callback: LayoutResultCallback,
                        extras: Bundle?
                    ) {
                        val ms = newAttributes.mediaSize
                        val w = ms?.widthMils ?: wMils
                        val h = ms?.heightMils ?: hMils
                        web.evaluateJavascript("window.__fit&&window.__fit($w,$h)") {
                            inner.onLayout(oldAttributes, newAttributes, cancellationSignal, callback, extras)
                        }
                    }
                    override fun onWrite(
                        pages: Array<out PageRange>,
                        destination: ParcelFileDescriptor,
                        cancellationSignal: CancellationSignal?,
                        callback: WriteResultCallback
                    ) = inner.onWrite(pages, destination, cancellationSignal, callback)
                    override fun onFinish() = inner.onFinish()
                }
                pm.print("Mek-hjälpen etikett", adapter, attrs)
            }
        }
    }
}
