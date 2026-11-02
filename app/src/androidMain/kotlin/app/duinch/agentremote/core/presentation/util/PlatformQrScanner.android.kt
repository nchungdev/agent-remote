package app.duinch.agentremote.core.presentation.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

@Composable
actual fun rememberPlatformQrScanner(): PlatformQrScanner {
    val context = LocalContext.current
    return remember(context) {
        object : PlatformQrScanner {
            override val isSupported: Boolean = true

            override fun startScan(onResult: (String) -> Unit) {
                try {
                    val options = GmsBarcodeScannerOptions.Builder()
                        .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                        .enableAutoZoom()
                        .build()
                    val scanner = GmsBarcodeScanning.getClient(context, options)
                    scanner.startScan()
                        .addOnSuccessListener { barcode ->
                            barcode.rawValue?.let(onResult)
                        }
                } catch (_: Exception) {
                }
            }
        }
    }
}
