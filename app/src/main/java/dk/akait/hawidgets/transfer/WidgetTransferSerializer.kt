package dk.akait.hawidgets.transfer

import dk.akait.hawidgets.data.WidgetConfig
import dk.akait.hawidgets.data.db.MultiWidgetSlotEntity
import dk.akait.hawidgets.widget.multientity.SecondaryColumns
import dk.akait.hawidgets.widget.multientity.secondaryColumns
import dk.akait.hawidgets.widget.multientity.withSecondaryColumns
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Rene (Android-fri) serialiserings-/parse-funktioner for eksport/import-filen.
 *
 * Filformat (version 1):
 * ```
 * { "version":1, "app":"ha-widgets", "exported":"<ISO8601>", "configs":[ … ] }
 * ```
 * `appWidgetId` gemmes bevidst IKKE (irrelevant i "anvend-på-denne-widget"-modellen). Hemmeligheder
 * (token/URL) er aldrig en del af en config og eksporteres derfor aldrig.
 *
 * [parseTransferBundle] er tolerant: manglende felter falder tilbage til entitetens defaults
 * (`optString`/opt-helpers) så en ældre fil stadig kan importeres efter modellen er vokset.
 */

// ── Serialisering ────────────────────────────────────────────────────────────

fun serializeTransferBundle(bundle: TransferBundle): String {
    val configs = JSONArray()
    bundle.configs.forEach { configs.put(it.toJson()) }
    return JSONObject()
        .put("version", TRANSFER_VERSION)
        .put("app", TRANSFER_APP_TAG)
        .put("exported", bundle.exported)
        .put("configs", configs)
        .toString(2)
}

private fun TransferConfig.toJson(): JSONObject = when (this) {
    is TransferConfig.Multi -> JSONObject()
        .put("type", "multi")
        .put("label", label)
        .put("showRefreshIcon", showRefreshIcon)
        .put("slots", JSONArray().also { arr -> slots.forEach { arr.put(it.toJson()) } })
    is TransferConfig.Shortcut -> JSONObject()
        .put("type", "shortcut")
        .put("label", label)
        // WidgetConfig har sin egen toJson() — genbruges 1:1 (én kilde til sandhed for felterne).
        .put("config", JSONObject(config.toJson()))
}

private fun MultiWidgetSlotEntity.toJson(): JSONObject {
    val secondaries = JSONArray()
    // Kun ikke-tomme sekundær-pladser eksporteres (en tom plads har null-visnings-entitet).
    secondaryColumns()
        .filter { it.displayEntityId != null }
        .forEach { secondaries.put(it.toJson()) }
    return JSONObject()
        .put("slotIndex", slotIndex)
        .put("displayEntityId", displayEntityId)
        .put("displayDomain", displayDomain)
        .put("actionEntityId", actionEntityId)
        .put("actionDomain", actionDomain)
        .put("action", action)
        .put("label", label)
        .put("confirmAction", confirmAction)
        .put("showIcon", showIcon)
        .putOpt("displayPrecision", displayPrecision)
        .putOpt("datetimeFormat", datetimeFormat)
        .putOpt("rangeInputMode", rangeInputMode)
        .putOpt("actionPackageName", actionPackageName)
        .put("secondaries", secondaries)
}

private fun SecondaryColumns.toJson(): JSONObject = JSONObject()
    .putOpt("displayEntityId", displayEntityId)
    .putOpt("displayDomain", displayDomain)
    .putOpt("actionEntityId", actionEntityId)
    .putOpt("actionDomain", actionDomain)
    .putOpt("action", action)
    .putOpt("showValue", showValue)
    .putOpt("confirmAction", confirmAction)
    .putOpt("displayPrecision", displayPrecision)
    .putOpt("datetimeFormat", datetimeFormat)
    .putOpt("rangeInputMode", rangeInputMode)
    .putOpt("label", label)
    .putOpt("showIcon", showIcon)

// ── Parse ────────────────────────────────────────────────────────────────────

fun parseTransferBundle(raw: String): Result<TransferBundle> {
    val root = try {
        JSONObject(raw)
    } catch (_: JSONException) {
        return Result.failure(ImportException(ImportError.InvalidJson))
    }
    if (root.optString("app") != TRANSFER_APP_TAG) {
        return Result.failure(ImportException(ImportError.WrongApp))
    }
    val version = root.optInt("version", 1)
    if (version > TRANSFER_VERSION) {
        return Result.failure(ImportException(ImportError.UnsupportedVersion(version)))
    }
    val configsArray = root.optJSONArray("configs")
    if (configsArray == null || configsArray.length() == 0) {
        return Result.failure(ImportException(ImportError.NoConfigs))
    }
    val configs = buildList {
        for (i in 0 until configsArray.length()) {
            configsArray.optJSONObject(i)?.let { parseConfig(it)?.let(::add) }
        }
    }
    if (configs.isEmpty()) return Result.failure(ImportException(ImportError.NoConfigs))
    return Result.success(TransferBundle(exported = root.optString("exported"), configs = configs))
}

private fun parseConfig(obj: JSONObject): TransferConfig? = when (obj.optString("type")) {
    "multi" -> TransferConfig.Multi(
        label = obj.optString("label"),
        showRefreshIcon = obj.optBoolean("showRefreshIcon", true),
        slots = obj.optJSONArray("slots").parseSlots(),
    )
    "shortcut" -> {
        val configObj = obj.optJSONObject("config")
        if (configObj == null) null
        else TransferConfig.Shortcut(
            label = obj.optString("label"),
            config = WidgetConfig.fromJson(configObj.toString()),
        )
    }
    else -> null
}

private fun JSONArray?.parseSlots(): List<MultiWidgetSlotEntity> {
    if (this == null) return emptyList()
    return buildList {
        for (i in 0 until length()) {
            optJSONObject(i)?.let { add(it.parseSlot(fallbackIndex = i)) }
        }
    }
}

private fun JSONObject.parseSlot(fallbackIndex: Int): MultiWidgetSlotEntity {
    val secondaries = optJSONArray("secondaries").parseSecondaryColumns()
    return MultiWidgetSlotEntity(
        appWidgetId = 0, // placeholder — sættes ved anvendelse
        slotIndex = optInt("slotIndex", fallbackIndex),
        displayEntityId = optString("displayEntityId"),
        displayDomain = optString("displayDomain"),
        actionEntityId = optString("actionEntityId"),
        actionDomain = optString("actionDomain"),
        action = optString("action", "NONE"),
        label = optString("label"),
        confirmAction = optBoolean("confirmAction", false),
        showIcon = optBoolean("showIcon", true),
        displayPrecision = optIntOrNull("displayPrecision"),
        datetimeFormat = optStringOrNull("datetimeFormat"),
        rangeInputMode = optStringOrNull("rangeInputMode"),
        actionPackageName = optStringOrNull("actionPackageName"),
    ).withSecondaryColumns(secondaries)
}

private fun JSONArray?.parseSecondaryColumns(): List<SecondaryColumns> {
    if (this == null) return emptyList()
    return buildList {
        for (i in 0 until length()) {
            optJSONObject(i)?.let { add(it.parseSecondaryColumns()) }
        }
    }
}

private fun JSONObject.parseSecondaryColumns(): SecondaryColumns = SecondaryColumns(
    displayEntityId = optStringOrNull("displayEntityId"),
    displayDomain = optStringOrNull("displayDomain"),
    actionEntityId = optStringOrNull("actionEntityId"),
    actionDomain = optStringOrNull("actionDomain"),
    action = optStringOrNull("action"),
    showValue = optBooleanOrNull("showValue"),
    confirmAction = optBooleanOrNull("confirmAction"),
    displayPrecision = optIntOrNull("displayPrecision"),
    datetimeFormat = optStringOrNull("datetimeFormat"),
    rangeInputMode = optStringOrNull("rangeInputMode"),
    label = optStringOrNull("label"),
    showIcon = optBooleanOrNull("showIcon"),
)

// ── Nullable JSON-helpers ────────────────────────────────────────────────────
// org.json udelader/normaliserer null forskelligt; disse giver konsekvent null-tilbagefald.

private fun JSONObject.optStringOrNull(key: String): String? =
    if (has(key) && !isNull(key)) optString(key) else null

private fun JSONObject.optIntOrNull(key: String): Int? =
    if (has(key) && !isNull(key)) optInt(key) else null

private fun JSONObject.optBooleanOrNull(key: String): Boolean? =
    if (has(key) && !isNull(key)) optBoolean(key) else null

/** Bærer-exception så [parseTransferBundle] kan returnere en [ImportError] via [Result.failure]. */
class ImportException(val error: ImportError) : Exception()
