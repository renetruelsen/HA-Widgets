package dk.akait.hawidgets.transfer

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import dk.akait.hawidgets.R
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Oversæt en [ImportError] til en lokaliseret bruger-besked (vises som Toast i config-skærmene). */
fun importErrorMessage(context: Context, error: ImportError): String = when (error) {
    ImportError.InvalidJson -> context.getString(R.string.import_error_invalid_json)
    ImportError.WrongApp -> context.getString(R.string.import_error_wrong_app)
    is ImportError.UnsupportedVersion -> context.getString(R.string.import_error_unsupported_version, error.version)
    ImportError.NoConfigs -> context.getString(R.string.import_error_no_configs)
    ImportError.NoMatchingType -> context.getString(R.string.import_error_no_matching_type)
}

/**
 * Android-siden af eksport/import: systemets share-sheet (eksport) og SAF-læsning (import).
 * Tynd og bevidst uden forretningslogik — serialisering/parse bor i [serializeTransferBundle]/
 * [parseTransferBundle], som er unit-testet.
 */
object WidgetTransferIo {

    /** Skal matche `<provider android:authorities>` i AndroidManifest.xml. */
    private const val FILE_PROVIDER_AUTHORITY = "dk.akait.hawidgets.fileprovider"
    private const val EXPORT_SUBDIR = "exports"

    /**
     * Serialiser [bundle] og åbn systemets share-sheet (`ACTION_SEND`) så brugeren selv vælger
     * destination (Gem i Filer, Drive, mail …). Filen skrives til et privat cache-underbibliotek
     * og eksponeres midlertidigt via [FileProvider].
     */
    fun shareBundle(context: Context, bundle: TransferBundle) {
        val json = serializeTransferBundle(bundle)
        val dir = File(context.cacheDir, EXPORT_SUBDIR).apply { mkdirs() }
        val file = File(dir, exportFileName())
        file.writeText(json)

        val uri: Uri = FileProvider.getUriForFile(context, FILE_PROVIDER_AUTHORITY, file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            // ClipData sikrer at BÅDE share-sheet-previewen og den valgte målapp får læse-adgang
            // (FLAG_GRANT_READ_URI_PERMISSION alene dækker ikke intentresolver-previewen).
            clipData = ClipData.newUri(context.contentResolver, file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, null).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    /** Læs hele indholdet af en SAF-valgt fil som tekst. Returnerer null hvis den ikke kan læses. */
    fun readDocument(context: Context, uri: Uri): String? =
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
        }.getOrNull()

    private fun exportFileName(): String {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        return "ha-widgets-config-$stamp.json"
    }
}
