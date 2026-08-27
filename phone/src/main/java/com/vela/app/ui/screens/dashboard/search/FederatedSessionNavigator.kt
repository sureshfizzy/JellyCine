package com.vela.app.ui.screens.dashboard.search

import android.app.Application
import com.vela.app.ui.screens.dashboard.home.CachedData
import com.vela.data.repository.AuthRepositoryProvider
import com.vela.data.repository.MediaRepositoryProvider
import kotlinx.coroutines.CancellationException

/**
 * 聚合内容只有在用户打开条目时才切活动会话，并统一清理与旧服务器绑定的首页缓存。
 */
internal class FederatedSessionNavigator(application: Application) {
    private val authRepository = AuthRepositoryProvider.getInstance(application)
    private val mediaRepository = MediaRepositoryProvider.getInstance(application)

    suspend fun activate(serverId: String): Result<Unit> {
        if (serverId.isBlank()) return Result.failure(IllegalArgumentException("Missing server id"))
        if (authRepository.getActiveSessionSnapshot().activeServerId == serverId) {
            return Result.success(Unit)
        }

        return try {
            authRepository.savedServer()
            authRepository.switchServer(serverId).map {
                mediaRepository.clearPersistedHomeSnapshot()
                CachedData.clearAllCache()
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(error)
        }
    }
}
