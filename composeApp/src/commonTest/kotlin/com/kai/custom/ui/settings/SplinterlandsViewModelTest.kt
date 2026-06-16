package com.kai.custom.ui.settings

import app.cash.turbine.test
import com.kai.custom.DaemonController
import com.kai.custom.data.Service
import com.kai.custom.splinterlands.SplinterlandsApi
import com.kai.custom.splinterlands.SplinterlandsBattleRunner
import com.kai.custom.splinterlands.SplinterlandsStore
import com.kai.custom.testutil.FakeDataRepository
import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SplinterlandsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepository: FakeDataRepository
    private lateinit var fakeSplinterlandsStore: SplinterlandsStore
    private lateinit var fakeSplinterlandsBattleRunner: SplinterlandsBattleRunner

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakeDataRepository()
        val testSettings = com.kai.custom.data.AppSettings(MapSettings())
        fakeSplinterlandsStore = SplinterlandsStore(testSettings)
        fakeSplinterlandsBattleRunner = SplinterlandsBattleRunner(
            fakeSplinterlandsStore,
            SplinterlandsApi(),
            fakeRepository,
            object : DaemonController {
                override fun start() {}
                override fun stop() {}
            },
        )
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial splinterlands instanceIds is empty`() = runTest {
        val viewModel = SplinterlandsViewModel(fakeRepository, fakeSplinterlandsStore, fakeSplinterlandsBattleRunner, SplinterlandsApi())

        viewModel.state.test {
            val state = awaitItem()
            assertTrue(state.splinterlandsInstanceIds.isEmpty())
        }
    }

    @Test
    fun `onAddSplinterlandsService adds service to list`() = runTest {
        fakeRepository.setConfiguredServices(Service.Gemini)
        val viewModel = SplinterlandsViewModel(fakeRepository, fakeSplinterlandsStore, fakeSplinterlandsBattleRunner, SplinterlandsApi())

        viewModel.state.test {
            val initialState = awaitItem()
            assertTrue(initialState.splinterlandsInstanceIds.isEmpty())

            initialState.onAddSplinterlandsService("gemini")

            val updatedState = awaitItem()
            assertEquals(listOf("gemini"), updatedState.splinterlandsInstanceIds)
        }
    }

    @Test
    fun `onRemoveSplinterlandsService removes service from list`() = runTest {
        fakeRepository.setConfiguredServices(Service.Gemini, Service.OpenAI)
        fakeSplinterlandsStore.setInstanceIds(listOf("gemini", "openai"))
        val viewModel = SplinterlandsViewModel(fakeRepository, fakeSplinterlandsStore, fakeSplinterlandsBattleRunner, SplinterlandsApi())

        viewModel.state.test {
            val initialState = awaitItem()
            assertEquals(listOf("gemini", "openai"), initialState.splinterlandsInstanceIds)

            initialState.onRemoveSplinterlandsService("gemini")

            val updatedState = awaitItem()
            assertEquals(listOf("openai"), updatedState.splinterlandsInstanceIds)
        }
    }

    @Test
    fun `onReorderSplinterlandsServices changes priority order`() = runTest {
        fakeRepository.setConfiguredServices(Service.Gemini, Service.OpenAI)
        fakeSplinterlandsStore.setInstanceIds(listOf("gemini", "openai"))
        val viewModel = SplinterlandsViewModel(fakeRepository, fakeSplinterlandsStore, fakeSplinterlandsBattleRunner, SplinterlandsApi())

        viewModel.state.test {
            val initialState = awaitItem()
            assertEquals(listOf("gemini", "openai"), initialState.splinterlandsInstanceIds)

            initialState.onReorderSplinterlandsServices(listOf("openai", "gemini"))

            val updatedState = awaitItem()
            assertEquals(listOf("openai", "gemini"), updatedState.splinterlandsInstanceIds)
            // Verify persisted
            assertEquals(listOf("openai", "gemini"), fakeSplinterlandsStore.getInstanceIds())
        }
    }
}
