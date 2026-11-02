package app.duinch.agentremote.features.settings.presentation

import app.duinch.agentremote.core.domain.repository.FakeAgentGatewayClient
import app.duinch.agentremote.features.settings.presentation.contract.SettingsIntent
import app.duinch.agentremote.features.updater.domain.model.AppRelease
import app.duinch.agentremote.features.updater.domain.model.UpdateStatus
import app.duinch.agentremote.features.updater.domain.repository.FakeAppUpdateRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeUpdateRepo: FakeAppUpdateRepository
    private lateinit var fakeGatewayClient: FakeAgentGatewayClient
    private lateinit var viewModel: SettingsViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeUpdateRepo = FakeAppUpdateRepository()
        fakeGatewayClient = FakeAgentGatewayClient()
        viewModel = SettingsViewModel(
            updateRepository = fakeUpdateRepo,
            gatewayClient = fakeGatewayClient,
            scope = kotlinx.coroutines.CoroutineScope(testDispatcher)
        )
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testCheckUpdateWhenNoReleaseExists() = runTest(testDispatcher) {
        fakeUpdateRepo.mockRelease = null
        viewModel.processIntent(SettingsIntent.CheckUpdate)
        advanceUntilIdle()

        val status = viewModel.uiState.value.updateStatus
        assertIs<UpdateStatus.UpToDate>(status)
        assertTrue(status.currentVersion.contains("0.1.3"))
    }

    @Test
    fun testCheckUpdateWhenNewerReleaseAvailable() = runTest(testDispatcher) {
        fakeUpdateRepo.mockRelease = AppRelease(
            tagName = "v0.2.0",
            versionName = "0.2.0",
            versionCode = 2,
            releaseNotes = "New features",
            downloadUrl = "https://github.com/.../app.apk",
            publishedAt = "2026-10-31",
            isNewerThanCurrent = true
        )

        viewModel.processIntent(SettingsIntent.CheckUpdate)
        advanceUntilIdle()

        val status = viewModel.uiState.value.updateStatus
        assertIs<UpdateStatus.Available>(status)
        assertEquals("v0.2.0", status.release.tagName)
    }

    @Test
    fun testDownloadAndInstallTriggersApkInstaller() = runTest(testDispatcher) {
        fakeUpdateRepo.mockRelease = AppRelease(
            tagName = "v0.2.0",
            versionName = "0.2.0",
            versionCode = 2,
            releaseNotes = "New features",
            downloadUrl = "https://github.com/.../app.apk",
            publishedAt = "2026-10-31",
            isNewerThanCurrent = true
        )

        viewModel.processIntent(SettingsIntent.CheckUpdate)
        advanceUntilIdle()

        viewModel.processIntent(SettingsIntent.DownloadAndInstallUpdate)
        advanceUntilIdle()

        assertEquals("fake_downloaded.apk", fakeUpdateRepo.installApkCalledWith)
        assertIs<UpdateStatus.ReadyToInstall>(viewModel.uiState.value.updateStatus)
    }

    @Test
    fun testTestPingSuccess() = runTest(testDispatcher) {
        viewModel.processIntent(SettingsIntent.TestPing)
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.pingMs)
    }
}
