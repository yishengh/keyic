package com.yishenghuang.keyic.scan

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.SystemClock
import android.util.Size
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as ComposeSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.yishenghuang.keyic.KeyicApp
import com.yishenghuang.keyic.R
import com.yishenghuang.keyic.core.scan.CardOcrParser
import com.yishenghuang.keyic.core.scan.CardOcrResult
import com.yishenghuang.keyic.core.scan.IdDocumentKind
import com.yishenghuang.keyic.core.scan.IdDocumentOcrParser
import com.yishenghuang.keyic.core.scan.IdDocumentOcrResult
import com.yishenghuang.keyic.core.scan.TotpUriParser
import com.yishenghuang.keyic.core.scan.WifiQrParser
import com.yishenghuang.keyic.ui.theme.KeyicTheme
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

class ScanCaptureActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (application as KeyicApp).beginExternalUi()
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE,
        )
        enableEdgeToEdge()
        val mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_TOTP_QR
        setContent {
            KeyicTheme {
                ScanCaptureScreen(
                    mode = mode,
                    onCancel = {
                        setResult(RESULT_CANCELED)
                        finish()
                    },
                    onTotp = { secret, issuer, account ->
                        setResult(
                            RESULT_OK,
                            Intent().apply {
                                putExtra(EXTRA_TOTP_SECRET, secret)
                                putExtra(EXTRA_TOTP_ISSUER, issuer)
                                putExtra(EXTRA_TOTP_ACCOUNT, account)
                            },
                        )
                        finish()
                    },
                    onCard = { number, expiry, holder, cvv ->
                        setResult(
                            RESULT_OK,
                            Intent().apply {
                                putExtra(EXTRA_CARD_NUMBER, number)
                                putExtra(EXTRA_CARD_EXPIRY, expiry)
                                putExtra(EXTRA_CARD_HOLDER, holder)
                                putExtra(EXTRA_CARD_CVV, cvv)
                            },
                        )
                        finish()
                    },
                    onIdDocument = { name, number, expiry, details, _ ->
                        val localizedTitle = when (mode) {
                            MODE_PASSPORT_OCR -> getString(R.string.doc_title_passport)
                            MODE_LICENSE_OCR -> getString(R.string.doc_title_license)
                            else -> null
                        }
                        setResult(
                            RESULT_OK,
                            Intent().apply {
                                putExtra(EXTRA_ID_NAME, name)
                                putExtra(EXTRA_ID_NUMBER, number)
                                putExtra(EXTRA_ID_EXPIRY, expiry)
                                putExtra(EXTRA_ID_DETAILS, details)
                                putExtra(EXTRA_ID_TITLE, localizedTitle)
                            },
                        )
                        finish()
                    },
                    onWifi = { ssid, password, security, hidden ->
                        setResult(
                            RESULT_OK,
                            Intent().apply {
                                putExtra(EXTRA_WIFI_SSID, ssid)
                                putExtra(EXTRA_WIFI_PASSWORD, password)
                                putExtra(EXTRA_WIFI_SECURITY, security)
                                putExtra(EXTRA_WIFI_HIDDEN, hidden)
                            },
                        )
                        finish()
                    },
                )
            }
        }
    }

    override fun onDestroy() {
        (application as KeyicApp).endExternalUi()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_MODE = "scan_mode"
        const val MODE_TOTP_QR = "totp_qr"
        const val MODE_CARD_OCR = "card_ocr"
        const val MODE_PASSPORT_OCR = "passport_ocr"
        const val MODE_LICENSE_OCR = "license_ocr"
        const val MODE_WIFI_QR = "wifi_qr"

        const val EXTRA_TOTP_SECRET = "totp_secret"
        const val EXTRA_TOTP_ISSUER = "totp_issuer"
        const val EXTRA_TOTP_ACCOUNT = "totp_account"
        const val EXTRA_CARD_NUMBER = "card_number"
        const val EXTRA_CARD_EXPIRY = "card_expiry"
        const val EXTRA_CARD_HOLDER = "card_holder"
        const val EXTRA_CARD_CVV = "card_cvv"
        const val EXTRA_ID_NAME = "id_name"
        const val EXTRA_ID_NUMBER = "id_number"
        const val EXTRA_ID_EXPIRY = "id_expiry"
        const val EXTRA_ID_DETAILS = "id_details"
        const val EXTRA_ID_TITLE = "id_title"
        const val EXTRA_WIFI_SSID = "wifi_ssid"
        const val EXTRA_WIFI_PASSWORD = "wifi_password"
        const val EXTRA_WIFI_SECURITY = "wifi_security"
        const val EXTRA_WIFI_HIDDEN = "wifi_hidden"

        fun intent(context: Context, mode: String): Intent =
            Intent(context, ScanCaptureActivity::class.java).putExtra(EXTRA_MODE, mode)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScanCaptureScreen(
    mode: String,
    onCancel: () -> Unit,
    onTotp: (secret: String, issuer: String?, account: String?) -> Unit,
    onCard: (number: String, expiry: String?, holder: String?, cvv: String?) -> Unit,
    onIdDocument: (
        name: String?,
        number: String?,
        expiry: String?,
        details: String?,
        suggestedTitle: String?,
    ) -> Unit,
    onWifi: (ssid: String, password: String, security: String, hidden: Boolean) -> Unit,
) {
    val context = LocalContext.current
    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionGranted = granted
        if (!granted) {
            Toast.makeText(context, context.getString(R.string.scan_camera_denied), Toast.LENGTH_SHORT).show()
            onCancel()
        }
    }
    LaunchedEffect(Unit) {
        if (!permissionGranted) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val title = when (mode) {
        ScanCaptureActivity.MODE_CARD_OCR -> stringResource(R.string.scan_card_title)
        ScanCaptureActivity.MODE_PASSPORT_OCR -> stringResource(R.string.scan_passport_title)
        ScanCaptureActivity.MODE_LICENSE_OCR -> stringResource(R.string.scan_license_title)
        ScanCaptureActivity.MODE_WIFI_QR -> stringResource(R.string.scan_wifi_title)
        else -> stringResource(R.string.scan_totp_title)
    }
    val hint = when (mode) {
        ScanCaptureActivity.MODE_CARD_OCR -> stringResource(R.string.scan_card_hint)
        ScanCaptureActivity.MODE_PASSPORT_OCR -> stringResource(R.string.scan_passport_hint)
        ScanCaptureActivity.MODE_LICENSE_OCR -> stringResource(R.string.scan_license_hint)
        ScanCaptureActivity.MODE_WIFI_QR -> stringResource(R.string.scan_wifi_hint)
        else -> stringResource(R.string.scan_totp_hint)
    }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                ),
                modifier = Modifier.statusBarsPadding(),
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (permissionGranted) {
                CameraAnalyzer(
                    mode = mode,
                    onTotp = onTotp,
                    onCard = onCard,
                    onIdDocument = onIdDocument,
                    onWifi = onWifi,
                    onError = { msg ->
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    },
                )
                if (mode == ScanCaptureActivity.MODE_TOTP_QR ||
                    mode == ScanCaptureActivity.MODE_WIFI_QR
                ) {
                    ScanFrameOverlay(modifier = Modifier.fillMaxSize())
                }
            }
            Text(
                text = hint,
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(24.dp),
            )
        }
    }
}

@Composable
private fun ScanFrameOverlay(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val frame = ComposeSize(size.width * 0.72f, size.width * 0.72f)
        val left = (size.width - frame.width) / 2f
        val top = (size.height - frame.height) / 2.4f
        drawRoundRect(
            color = Color.White.copy(alpha = 0.85f),
            topLeft = Offset(left, top),
            size = frame,
            cornerRadius = CornerRadius(36f, 36f),
            style = Stroke(width = 4.dp.toPx()),
        )
    }
}

@Composable
private fun CameraAnalyzer(
    mode: String,
    onTotp: (secret: String, issuer: String?, account: String?) -> Unit,
    onCard: (number: String, expiry: String?, holder: String?, cvv: String?) -> Unit,
    onIdDocument: (
        name: String?,
        number: String?,
        expiry: String?,
        details: String?,
        suggestedTitle: String?,
    ) -> Unit,
    onWifi: (ssid: String, password: String, security: String, hidden: Boolean) -> Unit,
    onError: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    val done = remember { AtomicBoolean(false) }

    DisposableEffect(mode, lifecycleOwner) {
        val analysisExecutor = Executors.newSingleThreadExecutor()
        val barcodeScanner = BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build(),
        )
        val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        val mainExecutor = ContextCompat.getMainExecutor(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }
            val resolutionSelector = ResolutionSelector.Builder()
                .setResolutionStrategy(
                    ResolutionStrategy(
                        Size(1920, 1080),
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                    ),
                )
                .build()
            val analysis = ImageAnalysis.Builder()
                .setResolutionSelector(resolutionSelector)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .build()

            val cardAccumulated = AtomicReference(CardOcrResult())
            val idAccumulated = AtomicReference(IdDocumentOcrResult())
            val seenAt = AtomicLong(0L)
            val preferredKind = when (mode) {
                ScanCaptureActivity.MODE_PASSPORT_OCR -> IdDocumentKind.PASSPORT
                ScanCaptureActivity.MODE_LICENSE_OCR -> IdDocumentKind.DRIVERS_LICENSE
                else -> null
            }

            analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                if (done.get()) {
                    imageProxy.close()
                    return@setAnalyzer
                }
                val mediaImage = imageProxy.image
                if (mediaImage == null) {
                    imageProxy.close()
                    return@setAnalyzer
                }
                val image = InputImage.fromMediaImage(
                    mediaImage,
                    imageProxy.imageInfo.rotationDegrees,
                )
                when (mode) {
                    ScanCaptureActivity.MODE_TOTP_QR -> {
                        barcodeScanner.process(image)
                            .addOnSuccessListener { barcodes ->
                                if (done.get()) return@addOnSuccessListener
                                val raw = barcodes.firstOrNull()?.rawValue ?: return@addOnSuccessListener
                                TotpUriParser.parse(raw).onSuccess { parsed ->
                                    if (done.compareAndSet(false, true)) {
                                        previewView.post {
                                            onTotp(parsed.secret, parsed.issuer, parsed.account)
                                        }
                                    }
                                }
                            }
                            .addOnCompleteListener { imageProxy.close() }
                    }
                    ScanCaptureActivity.MODE_WIFI_QR -> {
                        barcodeScanner.process(image)
                            .addOnSuccessListener { barcodes ->
                                if (done.get()) return@addOnSuccessListener
                                val raw = barcodes.firstOrNull()?.rawValue ?: return@addOnSuccessListener
                                WifiQrParser.parse(raw).onSuccess { parsed ->
                                    if (done.compareAndSet(false, true)) {
                                        previewView.post {
                                            onWifi(
                                                parsed.ssid,
                                                parsed.password,
                                                parsed.security,
                                                parsed.hidden,
                                            )
                                        }
                                    }
                                }
                            }
                            .addOnCompleteListener { imageProxy.close() }
                    }
                    ScanCaptureActivity.MODE_CARD_OCR -> {
                        textRecognizer.process(image)
                            .addOnSuccessListener { visionText ->
                                if (done.get()) return@addOnSuccessListener
                                val parsed = CardOcrParser.parse(visionText.text)
                                if (!parsed.hasNumber && cardAccumulated.get().number == null) {
                                    return@addOnSuccessListener
                                }
                                val merged = cardAccumulated.updateAndGet { it.merge(parsed) }
                                val now = SystemClock.elapsedRealtime()
                                if (merged.hasNumber && seenAt.get() == 0L) seenAt.set(now)
                                val waitMs = if (seenAt.get() > 0) now - seenAt.get() else 0L
                                val ready = when {
                                    merged.hasNumber && merged.hasExpiry && !merged.cvv.isNullOrBlank() -> true
                                    merged.hasNumber && merged.hasExpiry && waitMs >= 1_200L -> true
                                    merged.hasNumber && waitMs >= 3_500L -> true
                                    else -> false
                                }
                                if (ready && done.compareAndSet(false, true)) {
                                    previewView.post {
                                        onCard(
                                            merged.number.orEmpty(),
                                            merged.expiry,
                                            merged.holder,
                                            merged.cvv,
                                        )
                                    }
                                }
                            }
                            .addOnCompleteListener { imageProxy.close() }
                    }
                    else -> {
                        textRecognizer.process(image)
                            .addOnSuccessListener { visionText ->
                                if (done.get()) return@addOnSuccessListener
                                val parsed = IdDocumentOcrParser.parse(visionText.text, preferredKind)
                                if (parsed.documentNumber.isNullOrBlank() &&
                                    idAccumulated.get().documentNumber.isNullOrBlank()
                                ) {
                                    return@addOnSuccessListener
                                }
                                val merged = idAccumulated.updateAndGet { it.merge(parsed) }
                                val now = SystemClock.elapsedRealtime()
                                if (!merged.documentNumber.isNullOrBlank() && seenAt.get() == 0L) {
                                    seenAt.set(now)
                                }
                                val waitMs = if (seenAt.get() > 0) now - seenAt.get() else 0L
                                val ready = when {
                                    merged.isCompleteEnough && !merged.expiry.isNullOrBlank() && waitMs >= 800L -> true
                                    merged.isCompleteEnough && waitMs >= 1_500L -> true
                                    !merged.documentNumber.isNullOrBlank() && waitMs >= 4_000L -> true
                                    else -> false
                                }
                                if (ready && done.compareAndSet(false, true)) {
                                    previewView.post {
                                        onIdDocument(
                                            merged.fullName,
                                            merged.documentNumber,
                                            merged.expiry,
                                            merged.details,
                                            merged.suggestedTitle,
                                        )
                                    }
                                }
                            }
                            .addOnCompleteListener { imageProxy.close() }
                    }
                }
            }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis,
                )
            } catch (e: Exception) {
                onError(e.message ?: context.getString(R.string.scan_camera_failed))
            }
        }, mainExecutor)

        onDispose {
            runCatching { cameraProviderFuture.get().unbindAll() }
            analysisExecutor.shutdown()
            barcodeScanner.close()
            textRecognizer.close()
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = Modifier.fillMaxSize(),
    )
}
