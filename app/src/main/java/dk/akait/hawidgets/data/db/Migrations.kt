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

/** v4 → v5: widget-niveau "vis refresh-ikon"-indstilling, default true (v0.2.35). */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE multi_widget ADD COLUMN showRefreshIcon INTEGER NOT NULL DEFAULT 1")
    }
}

/** v5 → v6: bekræft-ved-tryk + værdi-formatering (præcision/datetime-format) pr. slot+chips (v0.3.0). */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE multi_widget_slot ADD COLUMN confirmAction INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE multi_widget_slot ADD COLUMN displayPrecision INTEGER")
        db.execSQL("ALTER TABLE multi_widget_slot ADD COLUMN datetimeFormat TEXT")
        for (n in 1..3) {
            db.execSQL("ALTER TABLE multi_widget_slot ADD COLUMN secondary${n}ConfirmAction INTEGER")
            db.execSQL("ALTER TABLE multi_widget_slot ADD COLUMN secondary${n}DisplayPrecision INTEGER")
            db.execSQL("ALTER TABLE multi_widget_slot ADD COLUMN secondary${n}DatetimeFormat TEXT")
        }
    }
}

/** v6 → v7: RANGE input-tilstand (skyder/felt) pr. slot + chips (Task 13, del A).
 * null = "SLIDER" = uændret nuværende adfærd for alle eksisterende rækker. */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE multi_widget_slot ADD COLUMN rangeInputMode TEXT")
        for (n in 1..3) {
            db.execSQL("ALTER TABLE multi_widget_slot ADD COLUMN secondary${n}RangeInputMode TEXT")
        }
    }
}

/** v7 → v8: custom chip-label pr. sekundær-chip (v0.2.42). null = ingen label = uændret adfærd. */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        for (n in 1..3) {
            db.execSQL("ALTER TABLE multi_widget_slot ADD COLUMN secondary${n}Label TEXT")
        }
    }
}

/** v8 → v9: fjerner den ubrugte `title`-kolonne (aldrig vist af UI siden v0.2.23, se v0.2.45-oprydning).
 * SQLite's `DROP COLUMN` er ikke tilgængeligt på alle Android-versioner (minSdk 26) — genskaber
 * tabellen uden kolonnen i stedet, hvilket virker på alle SQLite-versioner. */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE `multi_widget_new` (
                `appWidgetId` INTEGER NOT NULL,
                `showRefreshIcon` INTEGER NOT NULL DEFAULT 1,
                PRIMARY KEY(`appWidgetId`)
            )
            """.trimIndent()
        )
        db.execSQL(
            "INSERT INTO `multi_widget_new` (appWidgetId, showRefreshIcon) " +
                "SELECT appWidgetId, showRefreshIcon FROM `multi_widget`"
        )
        db.execSQL("DROP TABLE `multi_widget`")
        db.execSQL("ALTER TABLE `multi_widget_new` RENAME TO `multi_widget`")
    }
}

/** v9 → v10: brugervalgt "skjul ikon" pr. hoved-slot + sekundær-chip. `showIcon` er ikke-nullable
 * med DEFAULT 1 (vist) — eksisterende rækker viser ikonet uændret. `secondaryNShowIcon` er nullable,
 * samme mønster som `secondaryNShowValue` (null = default = vist). */
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE multi_widget_slot ADD COLUMN showIcon INTEGER NOT NULL DEFAULT 1")
        for (n in 1..3) {
            db.execSQL("ALTER TABLE multi_widget_slot ADD COLUMN secondary${n}ShowIcon INTEGER")
        }
    }
}

/** v10 → v11: fjerner den nu-ubrugte entity_widget-tabel (single-widgets slettet). Rører IKKE
 * entity_state/multi_widget/multi_widget_slot. */
val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS entity_widget")
    }
}

/** v11 → v12: 4. sekundær-chip pr. slot (12 nye nullable kolonner, samme mønster som secondary1-3).
 * `secondary4ShowIcon` er nullable (null = default = vist), præcis som secondary1-3ShowIcon fra v10.
 * Additiv — eksisterende slots får null i alle secondary4-felter (chip ikke i brug). */
val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE multi_widget_slot ADD COLUMN secondary4DisplayEntityId TEXT")
        db.execSQL("ALTER TABLE multi_widget_slot ADD COLUMN secondary4DisplayDomain TEXT")
        db.execSQL("ALTER TABLE multi_widget_slot ADD COLUMN secondary4ActionEntityId TEXT")
        db.execSQL("ALTER TABLE multi_widget_slot ADD COLUMN secondary4ActionDomain TEXT")
        db.execSQL("ALTER TABLE multi_widget_slot ADD COLUMN secondary4Action TEXT")
        db.execSQL("ALTER TABLE multi_widget_slot ADD COLUMN secondary4ShowValue INTEGER")
        db.execSQL("ALTER TABLE multi_widget_slot ADD COLUMN secondary4ConfirmAction INTEGER")
        db.execSQL("ALTER TABLE multi_widget_slot ADD COLUMN secondary4DisplayPrecision INTEGER")
        db.execSQL("ALTER TABLE multi_widget_slot ADD COLUMN secondary4DatetimeFormat TEXT")
        db.execSQL("ALTER TABLE multi_widget_slot ADD COLUMN secondary4RangeInputMode TEXT")
        db.execSQL("ALTER TABLE multi_widget_slot ADD COLUMN secondary4Label TEXT")
        db.execSQL("ALTER TABLE multi_widget_slot ADD COLUMN secondary4ShowIcon INTEGER")
    }
}

/** v12 → v13: "Åbn app"-handling på hoved-slotten. Én ny nullable kolonne — additiv, eksisterende
 * slots får null (ingen app-handling). */
val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE multi_widget_slot ADD COLUMN actionPackageName TEXT")
    }
}
