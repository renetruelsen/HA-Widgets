package dk.rtr.hawidgets.transfer

import dk.rtr.hawidgets.data.WidgetConfig
import dk.rtr.hawidgets.data.db.MultiSlotWithChips
import dk.rtr.hawidgets.data.db.MultiWidgetChipEntity
import dk.rtr.hawidgets.data.db.MultiWidgetSlotEntity
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
        .put("rowDensity", rowDensity)
        .put("slots", JSONArray().also { arr -> slots.forEach { arr.put(it.toJson()) } })
    is TransferConfig.Shortcut -> JSONObject()
        .put("type", "shortcut")
        .put("label", label)
        // WidgetConfig har sin egen toJson() — genbruges 1:1 (én kilde til sandhed for felterne).
        .put("config", JSONObject(config.toJson()))
}

private fun MultiSlotWithChips.toJson(): JSONObject {
    val secondaries = JSONArray()
    chips.sortedBy { it.chipIndex }.forEach { secondaries.put(it.toJson()) }
    return JSONObject()
        .put("slotIndex", slot.slotIndex)
        .putOpt("displayEntityId", slot.displayEntityId)
        .putOpt("displayDomain", slot.displayDomain)
        .putOpt("actionEntityId", slot.actionEntityId)
        .putOpt("actionDomain", slot.actionDomain)
        .putOpt("action", slot.action)
        .put("label", slot.label)
        .put("confirmAction", slot.confirmAction)
        .put("showIcon", slot.showIcon)
        .putOpt("displayPrecision", slot.displayPrecision)
        .putOpt("datetimeFormat", slot.datetimeFormat)
        .putOpt("rangeInputMode", slot.rangeInputMode)
        .putOpt("actionPackageName", slot.actionPackageName)
        .putOpt("showRangeValue", slot.showRangeValue)
        .put("secondaries", secondaries)
}

private fun MultiWidgetChipEntity.toJson(): JSONObject = JSONObject()
    .put("displayEntityId", displayEntityId)
    .put("displayDomain", displayDomain)
    .put("actionEntityId", actionEntityId)
    .put("actionDomain", actionDomain)
    .put("action", action)
    .putOpt("showValue", showValue)
    .putOpt("confirmAction", confirmAction)
    .putOpt("displayPrecision", displayPrecision)
    .putOpt("datetimeFormat", datetimeFormat)
    .putOpt("rangeInputMode", rangeInputMode)
    .putOpt("label", label)
    .putOpt("showIcon", showIcon)
    .putOpt("showRangeValue", showRangeValue)

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
        rowDensity = obj.optString("rowDensity", "NORMAL"),
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

private fun JSONArray?.parseSlots(): List<MultiSlotWithChips> {
    if (this == null) return emptyList()
    return buildList {
        for (i in 0 until length()) {
            optJSONObject(i)?.let { add(it.parseSlotWithChips(fallbackIndex = i)) }
        }
    }
}

private fun JSONObject.parseSlotWithChips(fallbackIndex: Int): MultiSlotWithChips {
    val slotIndex = optInt("slotIndex", fallbackIndex)
    val displayEntityId = optStringOrNull("displayEntityId")
    val slot = MultiWidgetSlotEntity(
        appWidgetId = 0, // placeholder — sættes ved anvendelse
        slotIndex = slotIndex,
        displayEntityId = displayEntityId,
        displayDomain = optStringOrNull("displayDomain"),
        actionEntityId = optStringOrNull("actionEntityId"),
        actionDomain = optStringOrNull("actionDomain"),
        // En slot med en hoved-entitet men uden gemt action (ældre/minimal fil) defaulter til
        // NONE (uændret fra den gamle flade models NOT NULL DEFAULT-adfærd). Ingen hoved-entitet
        // ⇒ ingen action (chips-only).
        action = if (displayEntityId == null) null else (optStringOrNull("action") ?: "NONE"),
        label = optString("label"),
        confirmAction = optBoolean("confirmAction", false),
        showIcon = optBoolean("showIcon", true),
        displayPrecision = optIntOrNull("displayPrecision"),
        datetimeFormat = optStringOrNull("datetimeFormat"),
        rangeInputMode = optStringOrNull("rangeInputMode"),
        actionPackageName = optStringOrNull("actionPackageName"),
        showRangeValue = optBooleanOrNull("showRangeValue"),
    )
    val chips = optJSONArray("secondaries").parseChips(appWidgetId = 0, slotIndex = slotIndex)
    return MultiSlotWithChips(slot, chips)
}

private fun JSONArray?.parseChips(appWidgetId: Int, slotIndex: Int): List<MultiWidgetChipEntity> {
    if (this == null) return emptyList()
    return buildList {
        for (i in 0 until length()) {
            val obj = optJSONObject(i) ?: continue
            obj.parseChip(appWidgetId, slotIndex, chipIndex = size)?.let(::add)
        }
    }
}

private fun JSONObject.parseChip(appWidgetId: Int, slotIndex: Int, chipIndex: Int): MultiWidgetChipEntity? {
    val displayEntityId = optStringOrNull("displayEntityId") ?: return null
    val displayDomain = optStringOrNull("displayDomain") ?: return null
    val actionEntityId = optStringOrNull("actionEntityId") ?: displayEntityId
    val actionDomain = optStringOrNull("actionDomain") ?: displayDomain
    val action = optStringOrNull("action") ?: "NONE"
    return MultiWidgetChipEntity(
        appWidgetId = appWidgetId,
        slotIndex = slotIndex,
        chipIndex = chipIndex,
        displayEntityId = displayEntityId,
        displayDomain = displayDomain,
        actionEntityId = actionEntityId,
        actionDomain = actionDomain,
        action = action,
        showValue = optBooleanOrNull("showValue"),
        confirmAction = optBooleanOrNull("confirmAction"),
        displayPrecision = optIntOrNull("displayPrecision"),
        datetimeFormat = optStringOrNull("datetimeFormat"),
        rangeInputMode = optStringOrNull("rangeInputMode"),
        label = optStringOrNull("label"),
        showIcon = optBooleanOrNull("showIcon"),
        showRangeValue = optBooleanOrNull("showRangeValue"),
    )
}

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
