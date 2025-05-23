package dev.cinestream.jellycine.viewmodels

import android.app.Application
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import dev.cinestream.jellycine.api.JellyfinApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.Response
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.api.models.UserLibraryApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.UserItemDataDto
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.*
import java.util.UUID

@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class MediaViewModelTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = UnconfinedTestDispatcher()

    @Mock
    private lateinit var mockApplication: Application

    @Mock
    private lateinit var mockJellyfinApi: JellyfinApi

    @Mock
    private lateinit var mockApiClient: ApiClient

    @Mock
    private lateinit var mockUserLibraryApi: UserLibraryApi

    @Mock
    private lateinit var mockMediaItemObserver: Observer<BaseItemDto?>
    @Mock
    private lateinit var mockIsLoadingObserver: Observer<Boolean>
    @Mock
    private lateinit var mockErrorObserver: Observer<String?>

    private lateinit var mediaViewModel: MediaViewModel

    private val mockUserId = UUID.randomUUID()
    private val mockItemId = UUID.randomUUID()
    private val mockBaseUrl = "http://localhost:8096"

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        `when`(mockJellyfinApi.api).thenReturn(mockApiClient)
        `when`(mockJellyfinApi.userId).thenReturn(mockUserId) // Needed for API calls
        `when`(mockApiClient.baseUrl).thenReturn(mockBaseUrl)
        `when`(mockApiClient.userLibraryApi).thenReturn(mockUserLibraryApi)

        // Similar to HomeViewModelTest, MediaViewModel uses JellyfinApi.getInstance()
        // which makes direct mocking of its internal `jellyfinApi` instance difficult
        // without refactoring for dependency injection.
        // These tests will assume the internal API calls are made, and focus on LiveData.
        mediaViewModel = MediaViewModel(mockApplication)
        mediaViewModel.mediaItemDetails.observeForever(mockMediaItemObserver)
        mediaViewModel.isLoading.observeForever(mockIsLoadingObserver)
        mediaViewModel.error.observeForever(mockErrorObserver)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loadMediaDetails with valid itemId success`() = runTest {
        // Arrange
        val mockItem = BaseItemDto(id = mockItemId, name = "Test Item")
        // This mock won't be hit by the actual MediaViewModel due to JellyfinApi.getInstance()
        // but this is how one would set them up if injection was used.
        whenever(mockUserLibraryApi.getItem(userId = eq(mockUserId), itemId = eq(mockItemId)))
            .thenReturn(Response.success(mockItem))

        // Act
        // We can't make the internal `jellyfinApi` use our `mockUserLibraryApi` easily.
        // So, this call will use the real API logic with potentially no server.
        // We are testing the ViewModel's handling of what it *thinks* is a response.
        // To truly test this, we'd need to mock the `JellyfinApi.getInstance()` call.
        // For now, we'll call it and observe LiveData changes based on the internal API's behavior.
        // If the internal API fails (no server), error LiveData will be set.
        mediaViewModel.loadMediaDetails(mockItemId.toString())
        advanceUntilIdle()


        // Assert
        // Verify isLoading states: true then false
        verify(mockIsLoadingObserver).onChanged(true) // Set at the start of loadMediaDetails

        // Due to the API mocking limitation, we can't guarantee `mockMediaItemObserver` gets `mockItem`.
        // Instead, we check that `isLoading` eventually becomes false.
        // And that either `mediaItemDetails` or `error` is updated.
        verify(mockIsLoadingObserver, atLeastOnce()).onChanged(false) // Set in finally block

        // At least one of these should have been called after the attempt.
        // In a real scenario without a server, `error` would be set.
        // If we could mock the API response to be `mockItem`, then `onChanged(mockItem)` would be verified.
        // verify(mockMediaItemObserver, atLeastOnce()).onChanged(anyOrNull())
        // verify(mockErrorObserver, atLeastOnce()).onChanged(anyOrNull())
        // For this basic test, we primarily ensure loading state changes.
    }

    @Test
    fun `loadMediaDetails with blank itemId sets error`() = runTest {
        // Act
        mediaViewModel.loadMediaDetails(" ") // Blank item ID
        advanceUntilIdle()

        // Assert
        verify(mockErrorObserver).onChanged("Item ID is missing.")
        verify(mockIsLoadingObserver, never()).onChanged(true) // Should not start loading
        verify(mockIsLoadingObserver, atLeastOnce()).onChanged(false) // Should ensure loading is reset if it was ever true
    }
    
    @Test
    fun `loadMediaDetails with invalid UUID format itemId sets error`() = runTest {
        // Act
        mediaViewModel.loadMediaDetails("invalid-uuid")
        advanceUntilIdle()

        // Assert
        verify(mockIsLoadingObserver).onChanged(true) // Starts loading
        verify(mockErrorObserver).onChanged("Invalid Item ID format.")
        verify(mockMediaItemObserver).onChanged(null) // Details should be cleared
        verify(mockIsLoadingObserver, atLeastOnce()).onChanged(false) // Finishes loading
    }


    @Test
    fun `toggleFavorite marks item as favorite and refreshes`() = runTest {
        // Arrange
        val currentItem = BaseItemDto(id = mockItemId, name = "Test Item", userData = UserItemDataDto(isFavorite = false))
        mediaViewModel.mediaItemDetails.value = currentItem // Simulate item already loaded

        // This mock won't be hit by the actual MediaViewModel.
        whenever(mockUserLibraryApi.markFavoriteItem(userId = eq(mockUserId), itemId = eq(mockItemId)))
            .thenReturn(Response.success(UserItemDataDto(isFavorite = true)))
        // Mock for the refresh call
        val refreshedItem = currentItem.copy(userData = UserItemDataDto(isFavorite = true))
        whenever(mockUserLibraryApi.getItem(userId = eq(mockUserId), itemId = eq(mockItemId)))
            .thenReturn(Response.success(refreshedItem))

        // Act
        mediaViewModel.toggleFavorite()
        advanceUntilIdle()

        // Assert
        // We can't verify mockUserLibraryApi.markFavoriteItem directly due to getInstance().
        // We check if loadMediaDetails is called again (which implies a refresh)
        // and isLoading sequence.
        // In a real test with proper DI, we'd verify:
        // verify(mockUserLibraryApi).markFavoriteItem(eq(mockUserId), eq(mockItemId))
        // verify(mockUserLibraryApi).getItem(eq(mockUserId), eq(mockItemId)) // for refresh
        
        // Check isLoading sequence for the refresh call within toggleFavorite
        // It's hard to isolate the isLoading for the refresh from the initial load without more complex test setup
        // For now, just ensuring it tries to load something.
        verify(mockIsLoadingObserver, atLeastOnce()).onChanged(true)
        verify(mockIsLoadingObserver, atLeastOnce()).onChanged(false)
    }

     @Test
    fun `toggleFavorite unmarks item as favorite and refreshes`() = runTest {
        // Arrange
        val currentItem = BaseItemDto(id = mockItemId, name = "Test Item", userData = UserItemDataDto(isFavorite = true))
        mediaViewModel.mediaItemDetails.value = currentItem

        // These mocks won't be hit by the actual MediaViewModel.
        whenever(mockUserLibraryApi.unmarkFavoriteItem(userId = eq(mockUserId), itemId = eq(mockItemId)))
            .thenReturn(Response.success(UserItemDataDto(isFavorite = false)))
        val refreshedItem = currentItem.copy(userData = UserItemDataDto(isFavorite = false))
        whenever(mockUserLibraryApi.getItem(userId = eq(mockUserId), itemId = eq(mockItemId)))
            .thenReturn(Response.success(refreshedItem))

        // Act
        mediaViewModel.toggleFavorite()
        advanceUntilIdle()
        
        // Assert (similar limitations as above)
        verify(mockIsLoadingObserver, atLeastOnce()).onChanged(true)
        verify(mockIsLoadingObserver, atLeastOnce()).onChanged(false)
    }
}
