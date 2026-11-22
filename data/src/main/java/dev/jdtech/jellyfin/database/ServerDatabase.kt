package dev.jdtech.jellyfin.database

import androidx.room3.AutoMigration
import androidx.room3.ColumnTypeConverters
import androidx.room3.Database
import androidx.room3.DeleteTable
import androidx.room3.RoomDatabase
import androidx.room3.migration.AutoMigrationSpec
import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import dev.jdtech.jellyfin.models.JellyxEpisodeDto
import dev.jdtech.jellyfin.models.JellyxMediaStreamDto
import dev.jdtech.jellyfin.models.JellyxMovieDto
import dev.jdtech.jellyfin.models.JellyxRatingsDto
import dev.jdtech.jellyfin.models.JellyxSeasonDto
import dev.jdtech.jellyfin.models.JellyxSegmentDto
import dev.jdtech.jellyfin.models.JellyxShowDto
import dev.jdtech.jellyfin.models.JellyxSourceDto
import dev.jdtech.jellyfin.models.JellyxTrickplayInfoDto
import dev.jdtech.jellyfin.models.JellyxUserDataDto
import dev.jdtech.jellyfin.models.Server
import dev.jdtech.jellyfin.models.ServerAddress
import dev.jdtech.jellyfin.models.User

@Database(
    entities =
        [
            Server::class,
            ServerAddress::class,
            User::class,
            JellyxMovieDto::class,
            JellyxShowDto::class,
            JellyxSeasonDto::class,
            JellyxEpisodeDto::class,
            JellyxSourceDto::class,
            JellyxMediaStreamDto::class,
            JellyxUserDataDto::class,
            JellyxTrickplayInfoDto::class,
            JellyxSegmentDto::class,
            JellyxRatingsDto::class,
        ],
    version = 9,
    autoMigrations =
        [
            AutoMigration(from = 2, to = 3),
            AutoMigration(from = 3, to = 4),
            AutoMigration(from = 4, to = 5, spec = ServerDatabase.TrickplayMigration::class),
            AutoMigration(from = 5, to = 6, spec = ServerDatabase.IntrosMigration::class),
            AutoMigration(from = 7, to = 8),
            AutoMigration(from = 8, to = 9),
        ],
)
@ColumnTypeConverters(Converters::class)
abstract class ServerDatabase : RoomDatabase() {
    abstract fun getServerDatabaseDao(): ServerDatabaseDao

    @DeleteTable(tableName = "trickPlayManifests") class TrickplayMigration : AutoMigrationSpec

    @DeleteTable(tableName = "intros") class IntrosMigration : AutoMigrationSpec
}

val MIGRATION_6_7 =
    object : Migration(startVersion = 6, endVersion = 7) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL("DROP TABLE segments")
            connection.execSQL(
                "CREATE TABLE segments (`itemId` TEXT NOT NULL, `type` TEXT NOT NULL, `startTicks` INTEGER NOT NULL, `endTicks` INTEGER NOT NULL, PRIMARY KEY(`itemId`, `type`), FOREIGN KEY(`itemId`) REFERENCES `episodes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
            )
        }
    }
