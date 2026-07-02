package dk.akait.hawidgets.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** v1 → v2: tilføjer multi_widget + multi_widget_slot. Rører IKKE entity_widget/entity_state. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `multi_widget` (
                `appWidgetId` INTEGER NOT NULL,
                `title` TEXT NOT NULL,
                PRIMARY KEY(`appWidgetId`)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `multi_widget_slot` (
                `appWidgetId` INTEGER NOT NULL,
                `slotIndex` INTEGER NOT NULL,
                `displayEntityId` TEXT NOT NULL,
                `displayDomain` TEXT NOT NULL,
                `actionEntityId` TEXT NOT NULL,
                `actionDomain` TEXT NOT NULL,
                `action` TEXT NOT NULL,
                `label` TEXT NOT NULL,
                PRIMARY KEY(`appWidgetId`, `slotIndex`)
            )
            """.trimIndent()
        )
    }
}
