package dk.akait.hawidgets.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        EntityStateEntity::class,
        MultiWidgetEntity::class,
        MultiWidgetSlotEntity::class,
        MultiWidgetChipEntity::class,
    ],
    version = 15,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun entityStateDao(): EntityStateDao
    abstract fun multiWidgetDao(): MultiWidgetDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ha_widgets.db",
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15)
                    .build().also { instance = it }
            }
    }
}

