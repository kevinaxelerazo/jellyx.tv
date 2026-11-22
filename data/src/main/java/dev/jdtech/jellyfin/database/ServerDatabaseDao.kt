package dev.jdtech.jellyfin.database

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Update
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
import dev.jdtech.jellyfin.models.ServerWithAddressAndUser
import dev.jdtech.jellyfin.models.ServerWithAddresses
import dev.jdtech.jellyfin.models.ServerWithAddressesAndUsers
import dev.jdtech.jellyfin.models.User
import java.util.UUID
import kotlinx.coroutines.flow.Flow

@Dao
interface ServerDatabaseDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertServer(server: Server)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertServerAddress(address: ServerAddress)

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertUser(user: User)

    @Update suspend fun updateServer(server: Server)

    @Query("SELECT * FROM servers WHERE id = :id") suspend fun getServer(id: String): Server?

    @Query("SELECT * FROM users WHERE id = :id") suspend fun getUser(id: UUID): User?

    @Transaction
    @Query("SELECT * FROM servers WHERE id = :id")
    suspend fun getServerWithAddresses(id: String): ServerWithAddresses

    @Query("SELECT * FROM serverAddresses WHERE id = :id")
    suspend fun getAddress(id: UUID): ServerAddress

    @Query("SELECT * FROM users WHERE serverId = :serverId")
    suspend fun getUsers(serverId: String): List<User>

    @Transaction
    @Query("SELECT * FROM servers WHERE id = :id")
    suspend fun getServerWithAddressesAndUsers(id: String): ServerWithAddressesAndUsers?

    @Transaction
    @Query("SELECT * FROM servers WHERE id = :id")
    suspend fun getServerWithAddressAndUser(id: String): ServerWithAddressAndUser?

    @Transaction
    @Query("SELECT * FROM servers")
    suspend fun getServersWithAddresses(): List<ServerWithAddresses>

    @Query("SELECT * FROM servers") suspend fun getServers(): List<Server>

    @Query("SELECT COUNT(*) FROM servers") suspend fun getServersCount(): Int

    @Query("SELECT COUNT(*) FROM servers") fun observeServersCount(): Flow<Int>

    @Query("SELECT * FROM servers WHERE id = :id") fun observeServer(id: String): Flow<Server?>

    @Query("SELECT currentUserId FROM servers WHERE id = :id")
    fun observeServerCurrentUserId(id: String): Flow<UUID?>

    @Query("DELETE FROM servers WHERE id = :id") suspend fun deleteServer(id: String)

    @Query("DELETE FROM users WHERE id = :id") suspend fun deleteUser(id: UUID)

    @Query("DELETE FROM serverAddresses WHERE id = :id") suspend fun deleteServerAddress(id: UUID)

    @Query("UPDATE servers SET currentUserId = :userId WHERE id = :serverId")
    suspend fun updateServerCurrentUser(serverId: String, userId: UUID)

    @Query(
        "SELECT * FROM users WHERE id = (SELECT currentUserId FROM servers WHERE id = :serverId)"
    )
    suspend fun getServerCurrentUser(serverId: String): User?

    @Query(
        "SELECT * FROM serverAddresses WHERE id = (SELECT currentServerAddressId FROM servers WHERE id = :serverId)"
    )
    suspend fun getServerCurrentAddress(serverId: String): ServerAddress?

    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertMovie(movie: JellyxMovieDto)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSource(source: JellyxSourceDto)

    @Query("SELECT * FROM movies WHERE id = :id") suspend fun getMovie(id: UUID): JellyxMovieDto

    @Query("SELECT * FROM movies WHERE id = :id") suspend fun getMovieOrNull(id: UUID): JellyxMovieDto?

    @Query("SELECT * FROM sources WHERE itemId = :itemId")
    suspend fun getSources(itemId: UUID): List<JellyxSourceDto>

    @Query("SELECT * FROM sources WHERE path LIKE '%.download'")
    suspend fun getPendingDownloadSources(): List<JellyxSourceDto>

    @Query("SELECT * FROM sources WHERE downloadId = :downloadId")
    suspend fun getSourceByDownloadId(downloadId: Long): JellyxSourceDto?

    @Query("UPDATE sources SET path = :path WHERE id = :id")
    suspend fun setSourcePath(id: String, path: String)

    @Query("DELETE FROM sources WHERE id = :id") suspend fun deleteSource(id: String)

    @Query("DELETE FROM movies WHERE id = :id") suspend fun deleteMovie(id: UUID)

    @Query(
        "UPDATE userdata SET playbackPositionTicks = :playbackPositionTicks WHERE itemId = :itemId AND userid = :userId"
    )
    suspend fun setPlaybackPositionTicks(itemId: UUID, userId: UUID, playbackPositionTicks: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMediaStream(mediaStream: JellyxMediaStreamDto)

    @Query("SELECT * FROM ratings_cache WHERE itemId = :itemId")
    suspend fun getRatings(itemId: UUID): JellyxRatingsDto?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRatings(ratings: JellyxRatingsDto)

    @Query("SELECT * FROM mediastreams WHERE sourceId = :sourceId")
    suspend fun getMediaStreamsBySourceId(sourceId: String): List<JellyxMediaStreamDto>

    @Query("SELECT * FROM mediastreams WHERE downloadId = :downloadId")
    suspend fun getMediaStreamByDownloadId(downloadId: Long): JellyxMediaStreamDto?

    @Query("SELECT * FROM mediastreams WHERE path LIKE '%.download'")
    suspend fun getPendingDownloadMediaStreams(): List<JellyxMediaStreamDto>

    @Query("UPDATE mediastreams SET downloadId = :downloadId WHERE id = :id")
    suspend fun setMediaStreamDownloadId(id: UUID, downloadId: Long)

    @Query("UPDATE mediastreams SET path = :path WHERE id = :id")
    suspend fun setMediaStreamPath(id: UUID, path: String)

    @Query("DELETE FROM mediastreams WHERE id = :id") suspend fun deleteMediaStream(id: UUID)

    @Query("DELETE FROM mediastreams WHERE sourceId = :sourceId")
    suspend fun deleteMediaStreamsBySourceId(sourceId: String)

    @Query("UPDATE userdata SET played = :played WHERE userId = :userId AND itemId = :itemId")
    suspend fun setPlayed(userId: UUID, itemId: UUID, played: Boolean)

    @Query("UPDATE userdata SET favorite = :favorite WHERE userId = :userId AND itemId = :itemId")
    suspend fun setFavorite(userId: UUID, itemId: UUID, favorite: Boolean)

    @Query("SELECT * FROM movies ORDER BY name ASC") suspend fun getMovies(): List<JellyxMovieDto>

    @Query("SELECT * FROM movies WHERE serverId = :serverId ORDER BY name ASC")
    suspend fun getMoviesByServerId(serverId: String): List<JellyxMovieDto>

    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertShow(show: JellyxShowDto)

    @Query("SELECT * FROM shows WHERE id = :id") suspend fun getShow(id: UUID): JellyxShowDto

    @Query("SELECT * FROM shows WHERE id = :id") suspend fun getShowOrNull(id: UUID): JellyxShowDto?

    @Query("SELECT * FROM shows ORDER BY name ASC") suspend fun getShows(): List<JellyxShowDto>

    @Query("SELECT * FROM shows WHERE serverId = :serverId ORDER BY name ASC")
    suspend fun getShowsByServerId(serverId: String): List<JellyxShowDto>

    @Query("DELETE FROM shows WHERE id = :id") suspend fun deleteShow(id: UUID)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSeason(show: JellyxSeasonDto)

    @Query("SELECT * FROM seasons WHERE id = :id")
    suspend fun getSeason(id: UUID): JellyxSeasonDto

    @Query("SELECT * FROM seasons WHERE id = :id")
    suspend fun getSeasonOrNull(id: UUID): JellyxSeasonDto?

    @Query("SELECT * FROM seasons WHERE seriesId = :seriesId ORDER BY indexNumber ASC")
    suspend fun getSeasonsByShowId(seriesId: UUID): List<JellyxSeasonDto>

    @Query("DELETE FROM seasons WHERE id = :id") suspend fun deleteSeason(id: UUID)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEpisode(episode: JellyxEpisodeDto)

    @Query("SELECT * FROM episodes WHERE id = :id")
    suspend fun getEpisode(id: UUID): JellyxEpisodeDto

    @Query("SELECT * FROM episodes WHERE id = :id")
    suspend fun getEpisodeOrNull(id: UUID): JellyxEpisodeDto?

    @Query(
        "SELECT * FROM episodes WHERE seriesId = :seriesId ORDER BY parentIndexNumber ASC, indexNumber ASC"
    )
    suspend fun getEpisodesByShowId(seriesId: UUID): List<JellyxEpisodeDto>

    @Query("SELECT * FROM episodes WHERE seasonId = :seasonId ORDER BY indexNumber ASC")
    suspend fun getEpisodesBySeasonId(seasonId: UUID): List<JellyxEpisodeDto>

    @Query(
        "SELECT * FROM episodes WHERE serverId = :serverId ORDER BY seriesName ASC, parentIndexNumber ASC, indexNumber ASC"
    )
    suspend fun getEpisodesByServerId(serverId: String): List<JellyxEpisodeDto>

    @Query("DELETE FROM episodes WHERE id = :id") suspend fun deleteEpisode(id: UUID)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSegment(segment: JellyxSegmentDto)

    @Query("SELECT * FROM segments WHERE itemId = :itemId")
    suspend fun getSegments(itemId: UUID): List<JellyxSegmentDto>

    @Query("SELECT * FROM seasons") suspend fun getSeasons(): List<JellyxSeasonDto>

    @Query("SELECT * FROM episodes") suspend fun getEpisodes(): List<JellyxEpisodeDto>

    @Query("SELECT * FROM userdata WHERE itemId = :itemId AND userId = :userId")
    suspend fun getUserData(itemId: UUID, userId: UUID): JellyxUserDataDto?

    @Transaction
    suspend fun getUserDataOrCreateNew(itemId: UUID, userId: UUID): JellyxUserDataDto {
        var userData = getUserData(itemId, userId)

        // Create user data when there is none
        if (userData == null) {
            userData =
                JellyxUserDataDto(
                    userId = userId,
                    itemId = itemId,
                    played = false,
                    favorite = false,
                    playbackPositionTicks = 0L,
                )
            insertUserData(userData)
        }

        return userData
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserData(userData: JellyxUserDataDto)

    @Query("DELETE FROM userdata WHERE itemId = :itemId") suspend fun deleteUserData(itemId: UUID)

    @Query("SELECT * FROM userdata WHERE userId = :userId AND itemId = :itemId AND toBeSynced = 1")
    suspend fun getUserDataToBeSynced(userId: UUID, itemId: UUID): JellyxUserDataDto?

    @Query(
        "UPDATE userdata SET toBeSynced = :toBeSynced WHERE itemId = :itemId AND userId = :userId"
    )
    suspend fun setUserDataToBeSynced(userId: UUID, itemId: UUID, toBeSynced: Boolean)

    @Query("SELECT * FROM movies WHERE serverId = :serverId AND name LIKE '%' || :name || '%'")
    suspend fun searchMovies(serverId: String, name: String): List<JellyxMovieDto>

    @Query("SELECT * FROM shows WHERE serverId = :serverId AND name LIKE '%' || :name || '%'")
    suspend fun searchShows(serverId: String, name: String): List<JellyxShowDto>

    @Query("SELECT * FROM episodes WHERE serverId = :serverId AND name LIKE '%' || :name || '%'")
    suspend fun searchEpisodes(serverId: String, name: String): List<JellyxEpisodeDto>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrickplayInfo(trickplayInfoDto: JellyxTrickplayInfoDto)

    @Query("SELECT * FROM trickplayInfos WHERE sourceId = :sourceId")
    suspend fun getTrickplayInfo(sourceId: String): JellyxTrickplayInfoDto?
}
