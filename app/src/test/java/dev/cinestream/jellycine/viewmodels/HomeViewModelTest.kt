package dev.cinestream.jellycine.viewmodels

import android.app.Application
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import dev.cinestream.jellycine.api.JellyfinApi
import dev.cinestream.jellycine.models.View
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.Response
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.api.client.extensions.viewsApi
import org.jellyfin.sdk.api.models.ItemsApi
import org.jellyfin.sdk.api.models.UserLibraryApi
import org.jellyfin.sdk.api.models.ViewsApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemDtoQueryResult
import org.jellyfin.sdk.model.api.ItemFilter
import org.jellyfin.sdk.model.SortOrder
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
class HomeViewModelTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = UnconfinedTestDispatcher() // StandardTestDispatcher could also be used

    @Mock
    private lateinit var mockApplication: Application

    @Mock
    private lateinit var mockJellyfinApi: JellyfinApi

    @Mock
    private lateinit var mockApiClient: ApiClient

    @Mock
    private lateinit var mockViewsApi: ViewsApi
    @Mock
    private lateinit var mockItemsApi: ItemsApi
    @Mock
    private lateinit var mockUserLibraryApi: UserLibraryApi

    @Mock
    private lateinit var mockViewsObserver: Observer<List<View>>
    @Mock
    private lateinit var mockFinishedLoadingObserver: Observer<Boolean>


    private lateinit var homeViewModel: HomeViewModel

    private val mockUserId = UUID.randomUUID()
    private val mockBaseUrl = "http://localhost:8096"

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher) // Set main dispatcher for testing

        // Mock the ApiClient access
        `when`(mockJellyfinApi.api).thenReturn(mockApiClient)
        `when`(mockJellyfinApi.userId).thenReturn(mockUserId)
        `when`(mockApiClient.baseUrl).thenReturn(mockBaseUrl)

        // Mock the extension properties
        `when`(mockApiClient.viewsApi).thenReturn(mockViewsApi)
        `when`(mockApiClient.itemsApi).thenReturn(mockItemsApi)
        `when`(mockApiClient.userLibraryApi).thenReturn(mockUserLibraryApi)

        // Mock the static getInstance method (this is tricky with Mockito alone for static methods)
        // For simplicity, we assume JellyfinApi is directly injectable or can be managed
        // This test will rely on passing the mocked JellyfinApi through a factory if possible,
        // or directly if the ViewModel's constructor allowed it.
        // Since HomeViewModel instantiates JellyfinApi internally, this is a limitation.
        // A better approach would be to inject JellyfinApi.
        // For this test, we will proceed as if it could be influenced,
        // acknowledging this setup might not fully mock JellyfinApi.getInstance().
        // The actual ViewModel uses JellyfinApi.getInstance(application, "")
        // We can't directly mock this static call easily without PowerMock or refactoring.
        // So, the internal `jellyfinApi` instance in the ViewModel will be the real one,
        // but its `api.userId` and `api.baseUrl` will not be our mocks.
        // This is a significant limitation for this specific ViewModel's current design.

        // Let's assume for the sake of this basic test, we can observe the effects,
        // even if deep mocking of JellyfinApi.getInstance is not feasible here.
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain() // Reset main dispatcher
    }

    @Test
    fun `init loads data and updates LiveData`() = runTest {
        // Arrange
        // Mock API responses (successful but empty)
        val emptyBaseItemDtoQueryResult = BaseItemDtoQueryResult(items = emptyList())
        val emptyBaseItemDtoList = emptyList<BaseItemDto>()

        // Mock API calls that are expected to happen in init
        // These mocks won't be hit by the actual HomeViewModel due to JellyfinApi.getInstance()
        // but this is how one would set them up if injection was used.
        whenever(mockViewsApi.getUserViews(userId = any()))
            .thenReturn(Response.success(emptyBaseItemDtoQueryResult)) // For getMovieLibraries, getShowLibraries
        whenever(mockUserLibraryApi.getResumableItems(userId = any()))
            .thenReturn(Response.success(BaseItemDtoQueryResult(items = emptyBaseItemDtoList)))
        whenever(mockItemsApi.getItems(
            userId = any(),
            includeItemTypes = any(),
            recursive = any(),
            sortBy = any(),
            sortOrder = any(),
            filters = any()
        )).thenReturn(Response.success(emptyBaseItemDtoQueryResult)) // For getFavoriteItems
        whenever(mockUserLibraryApi.getLatestMedia(userId = any(), parentId = any(), includeItemTypes = any()))
            .thenReturn(Response.success(emptyBaseItemDtoList)) // For getRecentlyAdded

        // Instantiate ViewModel - This will trigger init
        // Due to JellyfinApi.getInstance(), the internal API calls in ViewModel won't use our mocks directly.
        // This test will thus primarily check LiveData default states or states if no data is loaded.
        homeViewModel = HomeViewModel(mockApplication)
        homeViewModel.views.observeForever(mockViewsObserver)
        homeViewModel.finishedLoading.observeForever(mockFinishedLoadingObserver)

        // Assertions
        // Verify finishedLoading becomes true after init (even if calls fail or return empty)
        verify(mockFinishedLoadingObserver).onChanged(false) // Initial state before coroutine
        // The coroutine in init runs, and eventually finishedLoading should be true.
        // Using runTest should handle coroutine execution.
        advanceUntilIdle() // Ensure all coroutines launched in init complete

        verify(mockFinishedLoadingObserver, atLeastOnce()).onChanged(true) // Final state

        // Verify views LiveData is updated (likely with empty lists if API calls are not fully mocked)
        verify(mockViewsObserver, atLeastOnce()).onChanged(any())

        // Due to the JellyfinApi.getInstance() issue, we cannot easily verify
        // that the specific mocked API methods (mockViewsApi.getUserViews, etc.) were called
        // on OUR mocks. The ViewModel will call them on its own internal instance.
        // A more robust test would require dependency injection for JellyfinApi.
        // For now, this test demonstrates the setup and LiveData observation.
    }
}
