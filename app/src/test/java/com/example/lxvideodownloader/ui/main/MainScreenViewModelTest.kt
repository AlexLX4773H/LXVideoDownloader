package com.example.lxvideodownloader.ui.main

import android.content.Context
import com.example.lxvideodownloader.core.model.DownloadTask
import com.example.lxvideodownloader.core.model.HlsPlaylist
import com.example.lxvideodownloader.core.model.StreamVariant
import com.example.lxvideodownloader.core.storage.CompletedVideo
import com.example.lxvideodownloader.data.DataRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MainScreenViewModelTest {

    @Test
    fun uiState_initialState() = runTest {
        val viewModel = MainScreenViewModel(FakeDataRepository())
        val state = viewModel.uiState.first()
        assertEquals("", state.urlInput)
        assertEquals(0, state.selectedTab)
        assertNull(state.errorMessage)
    }

    @Test
    fun onUrlChanged_updatesUrlInput() = runTest {
        val viewModel = MainScreenViewModel(FakeDataRepository())
        viewModel.onUrlChanged("https://example.com/stream.m3u8")
        val state = viewModel.uiState.value
        assertEquals("https://example.com/stream.m3u8", state.urlInput)
    }

    @Test
    fun selectTab_updatesActiveTab() = runTest {
        val viewModel = MainScreenViewModel(FakeDataRepository())
        viewModel.selectTab(2)
        assertEquals(2, viewModel.uiState.value.selectedTab)
    }

    @Test
    fun dismissError_clearsErrorMessage() = runTest {
        val viewModel = MainScreenViewModel(FakeDataRepository())
        viewModel.dismissError()
        assertNull(viewModel.uiState.value.errorMessage)
    }
}

private class FakeDataRepository : DataRepository {
    override val tasks: StateFlow<List<DownloadTask>> = MutableStateFlow(emptyList())
    override val completedVideos: StateFlow<List<CompletedVideo>> = MutableStateFlow(emptyList())

    override fun refreshVideos(context: Context) {}

    override suspend fun inspectUrl(url: String): HlsPlaylist {
        return HlsPlaylist.Media(
            targetDuration = 10.0,
            segments = emptyList()
        )
    }

    override fun startDownload(
        context: Context,
        url: String,
        title: String,
        variant: StreamVariant?
    ): String = "fake_task_id"

    override fun startDirectDownload(
        context: Context,
        url: String,
        title: String
    ): String = "fake_direct_task_id"

    override fun cancelDownload(taskId: String) {}

    override fun removeTask(taskId: String) {}

    override fun deleteVideo(context: Context, video: CompletedVideo) {}
}
