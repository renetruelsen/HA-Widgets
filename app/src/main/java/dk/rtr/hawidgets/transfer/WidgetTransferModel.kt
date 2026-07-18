package dk.rtr.hawidgets.transfer

import dk.rtr.hawidgets.data.WidgetConfig
import dk.rtr.hawidgets.data.db.MultiSlotWithChips

/** App-tag i filens `app`-felt — afviser import af en anden apps eksport-fil. */
const val TRANSFER_APP_TAG = "ha-widgets"

/** Nuværende filformat-version. Bumpes ved brydende format-ændringer; [parseTransferBundle]
 * afviser filer med en HØJERE version (lavet af en nyere app-udgave). */
const val TRANSFER_VERSION = 1

/**
 * Én widget-konfiguration i en eksport-fil. [label] er auto-udledt ved eksport og vises i
 * import-vælgeren (multi-widgets har ingen egen titel). [MultiSlotWithChips]/[WidgetConfig]
 * genbruges som transport-repræsentation — de er rene data-klasser (ingen Room-runtime kræves
 * for at konstruere dem), og er den kanoniske form config'en alligevel gemmes i.
 */
sealed interface TransferConfig {
    val label: String

    data class Multi(
        override val label: String,
        val showRefreshIcon: Boolean,
        /** Række-densitet ("COMPACT"/"NORMAL"/"LARGE"). Ældre eksport-filer uden feltet → "NORMAL". */
        val rowDensity: String,
        /** Slots (+ deres chips) i rækkefølge. `appWidgetId` er en placeholder (0) — sættes ved
         * import til den widget der anvender config'en. */
        val slots: List<MultiSlotWithChips>,
    ) : TransferConfig

    data class Shortcut(
        override val label: String,
        val config: WidgetConfig,
    ) : TransferConfig
}

/** Hele eksport-filens indhold. */
data class TransferBundle(
    val exported: String,
    val configs: List<TransferConfig>,
) {
    val multiConfigs: List<TransferConfig.Multi> get() = configs.filterIsInstance<TransferConfig.Multi>()
    val shortcutConfigs: List<TransferConfig.Shortcut> get() = configs.filterIsInstance<TransferConfig.Shortcut>()
}

/** Typed import-fejl → hver sin lokaliserede besked i UI'et. */
sealed interface ImportError {
    /** Filen kunne ikke parses som JSON, eller kunne slet ikke læses. */
    data object InvalidJson : ImportError
    /** `app`-taget matcher ikke [TRANSFER_APP_TAG] — en anden apps fil. */
    data object WrongApp : ImportError
    /** Filens `version` er nyere end denne app forstår. */
    data class UnsupportedVersion(val version: Int) : ImportError
    /** Filen indeholder ingen configs. */
    data object NoConfigs : ImportError
    /** Filen indeholder ingen config af den type den aktuelle skærm kan bruge
     * (fx en ren genvej-fil åbnet i en multi-widgets config-skærm). */
    data object NoMatchingType : ImportError
}
