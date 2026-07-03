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

/** v2 → v3: tilføjer op til 3 sekundære info/handlings-chips pr. slot (v0.2.28). */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        for (n in 1..3) {
            db.execSQL("ALTER TABLE multi_widget_slot ADD COLUMN secondary${n}DisplayEntityId TEXT")
            db.execSQL("ALTER TABLE multi_widget_slot ADD COLUMN secondary${n}DisplayDomain TEXT")
            db.execSQL("ALTER TABLE multi_widget_slot ADD COLUMN secondary${n}ActionEntityId TEXT")
            db.execSQL("ALTER TABLE multi_widget_slot ADD COLUMN secondary${n}ActionDomain TEXT")
            db.execSQL("ALTER TABLE multi_widget_slot ADD COLUMN secondary${n}Action TEXT")
        }
    }
}

/** v3 → v4: brugervalgt "vis værdi"-indstilling pr. sekundær-chip (v0.2.32). */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        for (n in 1..3) {
            db.execSQL("ALTER TABLE multi_widget_slot ADD COLUMN secondary${n}ShowValue INTEGER")
        }
    }
}
