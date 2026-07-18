package dk.rtr.hawidgets.widget.common

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import dk.rtr.hawidgets.data.EntityRepository
import dk.rtr.hawidgets.data.HaApiClient
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Redigér en input_datetime-entitets værdi via Androids indbyggede dato-/tidsvælgere —
 * viser DatePickerDialog og/eller TimePickerDialog afhængig af entitetens has_date/has_time,
 * kalder input_datetime.set_datetime. Ingen egen UI — de native dialoger ER skærmen.
 */
class DateTimeControlActivity : ComponentActivity() {

    companion object {
        const val EXTRA_ENTITY_ID = "entity_id"
        const val EXTRA_HAS_DATE = "has_date"
        const val EXTRA_HAS_TIME = "has_time"
        /** Rå HA-state, fx "2026-07-04 15:30:00" / "2026-07-04" / "15:30:00" — bruges som default. */
        const val EXTRA_CURRENT_VALUE = "current_value"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val entityId = intent.getStringExtra(EXTRA_ENTITY_ID) ?: return finish()
        val hasDate = intent.getBooleanExtra(EXTRA_HAS_DATE, true)
        val hasTime = intent.getBooleanExtra(EXTRA_HAS_TIME, true)
        val current = intent.getStringExtra(EXTRA_CURRENT_VALUE)

        val cal = Calendar.getInstance().apply {
            parseCurrentValue(current, hasDate, hasTime)?.let { time = it }
        }

        fun submit(date: String?, time: String?) {
            lifecycleScope.launch {
                val api = resolveHaApiClient(applicationContext)
                if (api != null) {
                    val extraData = buildMap<String, Any> {
                        date?.let { put("date", it) }
                        time?.let { put("time", it) }
                    }
                    val result = api.callService(
                        "input_datetime", "set_datetime", entityId, extraData = extraData,
                    )
                    if (result is HaApiClient.Result.Ok) {
                        EntityRepository.refresh(applicationContext, entityId)
                    } else {
                        showActionError(applicationContext)
                    }
                }
                finish()
            }
        }

        fun showTimePicker(dateStr: String?) {
            if (hasTime) {
                TimePickerDialog(
                    this,
                    { _, hour, minute -> submit(dateStr, "%02d:%02d:00".format(hour, minute)) },
                    cal.get(Calendar.HOUR_OF_DAY),
                    cal.get(Calendar.MINUTE),
                    true,
                ).apply {
                    setOnCancelListener { finish() }
                    show()
                }
            } else {
                submit(dateStr, null)
            }
        }

        if (hasDate) {
            DatePickerDialog(
                this,
                { _, year, month, day -> showTimePicker("%04d-%02d-%02d".format(year, month + 1, day)) },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH),
            ).apply {
                setOnCancelListener { finish() }
                show()
            }
        } else {
            showTimePicker(null)
        }
    }
}

private fun parseCurrentValue(current: String?, hasDate: Boolean, hasTime: Boolean) = current?.let {
    val pattern = when {
        hasDate && hasTime -> "yyyy-MM-dd HH:mm:ss"
        hasDate -> "yyyy-MM-dd"
        else -> "HH:mm:ss"
    }
    try { SimpleDateFormat(pattern, Locale.US).parse(it) } catch (_: Exception) { null }
}
