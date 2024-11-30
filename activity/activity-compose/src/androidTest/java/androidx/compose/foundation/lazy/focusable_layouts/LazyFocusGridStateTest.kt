/*
 * Copyright 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.compose.foundation.lazy.focusable_layouts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.Card
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LazyFocusGridStateTest {
    @get:Rule
    val rule = createComposeRule()

    val gridState: LazyFocusGridState = LazyFocusGridState()


    @OptIn(ExperimentalMaterialApi::class)
    @Composable
    private fun SampleRowList(
        listAdapterState: LazyFocusGridState,
        listSize: Int = 30,
        gridCells: GridCells = GridCells.Fixed(4),
        onFocusChange: (index: Int, focusState: FocusState, key: String?) -> Unit = { _, _, _ -> }
    ) {
        LazyVerticalGrid(
            modifier = Modifier
                .fillMaxSize()
                .focusTarget(),
            state = listAdapterState.lazyGridState,
            columns = gridCells
        ) {
            items(listSize) { index ->
                val isFocused = remember { mutableStateOf(false) }
                Card(
                    onClick = {},
                    modifier = Modifier
                        .width(100.dp)
                        .aspectRatio(16 / 9f)
                        .background(if (isFocused.value) Color.Red else Color.Blue)
                        .onFocusChanged {
                            onFocusChange(index, it, index.toString())
                        }
                        .focusChild(
                            listAdapterState,
                            remember {
                                FocusItem(
                                    focusRequester = FocusRequester(),
                                    index = index,
                                    key = "$index"
                                )
                            }
                        )
//                        .focusChild(
//                            listAdapterState,
//                            remember {
//                                FocusItem(
//                                    focusRequester = FocusRequester(),
//                                    index = index,
//                                    key = "$index"
//                                )
//                            }
//                        )
                ) {
                    Text("index = $index || column = ${index / 4} row = ${index % 4}")
                }
            }
        }
    }

    @Test
    fun restoreFocusOnInitalBoot() {
        var focusedIndex: Int = -1


        rule.setContent {
            SampleRowList(gridState, onFocusChange = { index, state, key ->
                if (state.isFocused) {
                    focusedIndex = index
                }
            })
        }
        rule.runOnIdle {
            gridState.restoreFocus()
            assertTrue(focusedIndex == 0)
        }
    }


    @Test
    fun requestFocusToSpecificIndex() {
        var focusedIndex: Int = -1
        val requestFocusTarget = 9
        rule.setContent {
            SampleRowList(gridState, onFocusChange = { index, state, key ->
                if (state.isFocused) {
                    focusedIndex = index
                }
            })
        }
        rule.runOnIdle {
            gridState.requestFocus(requestFocusTarget)
            assertTrue(focusedIndex == requestFocusTarget)
        }
    }

    @Test
    fun requestFocusToOutOfBoundsIndexReturnFalse() {
        rule.setContent {
            SampleRowList(gridState)
        }
        rule.runOnIdle {
            val requestFocusTarget = gridState.lazyGridState.layoutInfo.visibleItemsInfo.size + 1
            assertFalse(gridState.requestFocus(requestFocusTarget))
        }
    }

    @Test
    fun scrollAndRequestFocusSucceed() {
        var focusedIndex: Int = -1
        val requestFocusTarget = 29
        rule.setContent {
            SampleRowList(gridState, onFocusChange = { index, state, key ->
                if (state.isFocused) {
                    focusedIndex = index
                }
            })
        }
        rule.runOnIdle {
            runBlocking {
                gridState.scrollToAndRequestFocus(requestFocusTarget, 0)
                assertTrue(requestFocusTarget == focusedIndex)
            }
        }
    }

    @Test
    fun scrollAndRequestFocusToNegativeNumberThrowsException() {
        var focusedIndex: Int = -1
        val requestFocusTarget = -1
        rule.setContent {
            SampleRowList(gridState, onFocusChange = { index, state, key ->
                if (state.isFocused) {
                    focusedIndex = index
                }
            })
        }
        rule.runOnIdle {
            assertThrows(IllegalStateException::class.java, {
                runTest {
                    gridState.scrollToAndRequestFocus(requestFocusTarget, 0)
                }
            })
            assertTrue(focusedIndex == -1)
        }
    }


    @Test
    fun scrollToOutOfBoundsThrowsException() {
        rule.setContent {
            SampleRowList(gridState, onFocusChange = { index, state, key ->

            })
        }
        rule.runOnIdle {
            assertThrows(IllegalStateException::class.java) {
                runBlocking {
                    gridState.scrollToAndRequestFocus(
                        gridState.lazyGridState.layoutInfo.totalItemsCount + 1,
                        0
                    )
                }
            }
        }
    }


    @Test
    fun requestFocusToVisibleItemByKeySucceed() {
        val targetKeyFocus = "5"
        lateinit var focusedKey: String
        rule.setContent {
            SampleRowList(gridState, onFocusChange = { index, state, key ->
                if (state.isFocused && key != null) {
                    focusedKey = key
                }
            })
        }
        rule.runOnIdle {
            assertTrue(gridState.requestFocus(targetKeyFocus))
            assertTrue(focusedKey == targetKeyFocus)
        }
    }

    @Test
    fun restoreFocusWithNegativeDefaultIndexThrowsException() {
        val restoreToDefaultIndex = -1
        rule.setContent {
            SampleRowList(gridState)
        }
        rule.runOnIdle {
            assertThrows(IllegalStateException::class.java) {
                gridState.restoreFocus(restoreToDefaultIndex)
            }
        }
    }


    @Test
    fun requestFocusToEachVisibleItemSucceed() {
        gridState
        val listSize = 30
        rule.setContent {
            SampleRowList(gridState, listSize = listSize)
        }
        rule.runOnIdle {
            for (i in gridState.lazyGridState.layoutInfo.visibleItemsInfo.indices) {
                assertTrue(gridState.requestFocus(i))

            }
        }
    }

    @Test
    fun requestFocusToEachItemWithoutScrollFails() {
        val listAdapterState = LazyFocusListState(index = 0)
        rule.setContent {
            SampleRowList(gridState)
        }
        rule.runOnIdle {
            for (i in 0 until listAdapterState.lazyList.layoutInfo.totalItemsCount) {
                if (i in listAdapterState.lazyList.layoutInfo.visibleItemsInfo.indices) {
                    assertTrue(listAdapterState.requestFocus(i))
                } else {
                    assertFalse(listAdapterState.requestFocus(i))
                }
            }
        }
    }

    @Test
    fun requestFocusToEachItemWithScrollSucceed() {
        val listAdapterState = LazyFocusListState(index = 0)
        rule.setContent {
            SampleRowList(gridState)
        }
        rule.runOnIdle {
            runBlocking {
                for (i in 0 until listAdapterState.lazyList.layoutInfo.totalItemsCount) {
                    assertTrue(listAdapterState.scrollToAndRequestFocus(i, 0))
                }
            }
        }
    }


    @Test
    fun restoreFocusWithDefaultEmptyKeyThrowsError() {
        val targetKeyFocus = ""
        lateinit var focusedKey: String
        rule.setContent {
            SampleRowList(gridState, onFocusChange = { index, state, key ->
                if (state.isFocused && key != null) {
                    focusedKey = key
                }
            })
        }
        rule.runOnIdle {
            assertThrows(IllegalStateException::class.java) {
                gridState.restoreFocus(targetKeyFocus)
            }
            assertThrows(UninitializedPropertyAccessException::class.java) {
                @Suppress("UNUSED_EXPRESSION")
                focusedKey
            }
        }
    }


    @Test
    fun requestFocusByBlankKeyThrowsException() {
        val targetKeyFocus = ""
        lateinit var focusedKey: String
        rule.setContent {
            SampleRowList(gridState, onFocusChange = { index, state, key ->
                if (state.isFocused && key != null) {
                    focusedKey = key
                }
            })
        }
        rule.runOnIdle {
            assertThrows(IllegalStateException::class.java) {
                gridState.requestFocus(targetKeyFocus)
            }
            assertThrows(UninitializedPropertyAccessException::class.java) {
                @Suppress("UNUSED_EXPRESSION")
                focusedKey
            }
        }
    }


    @Test
    fun requestFocusToUnVisibleItemByKeyFailed() {
        lateinit var focusedKey: String
        val itemsSize = 60
        rule.setContent {
            SampleRowList(gridState, listSize = itemsSize, onFocusChange = { index, state, key ->
                if (state.isFocused && key != null) {
                    focusedKey = key
                }
            })
        }
        rule.runOnIdle {
            val unVisibleItem =
                (gridState.lazyGridState.layoutInfo.visibleItemsInfo.size + 1).toString()
            assertFalse(gridState.requestFocus(unVisibleItem))
            assertThrows(UninitializedPropertyAccessException::class.java) {
                @Suppress("UNUSED_EXPRESSION")
                focusedKey
            }
        }
    }

    @Test
    fun requestFocusToOfBoundsThrowsException() {
        val listSize = 30
        rule.setContent {
            SampleRowList(gridState, listSize = listSize, onFocusChange = { index, state, key ->

            })
        }
        rule.runOnIdle {
            assertThrows(IllegalStateException::class.java) {
                gridState.requestFocus(listSize + 1)
            }
        }
    }

    @Test
    fun scrollAndRequestFocusMultipleTimesSucceed() {
        var focusedIndex: Int = -1
        var requestFocusTarget = 29
        rule.setContent {
            SampleRowList(gridState, onFocusChange = { index, state, key ->
                if (state.isFocused) {
                    focusedIndex = index
                }
            })
        }
        rule.runOnIdle {
            runBlocking {
                gridState.scrollToAndRequestFocus(requestFocusTarget, 0)
                assertTrue(requestFocusTarget == focusedIndex)
                requestFocusTarget = 15
                gridState.scrollToAndRequestFocus(requestFocusTarget, 0)
                assertTrue(requestFocusTarget == focusedIndex)
                requestFocusTarget = 0
                gridState.scrollToAndRequestFocus(requestFocusTarget, 0)
                assertTrue(requestFocusTarget == focusedIndex)
            }
        }
    }


    @Test
    fun requestFocusToUnInitializeListThrowsException() {
        val requestFocusTarget = 11
        assertThrows(IllegalStateException::class.java) {
            gridState.requestFocus(requestFocusTarget)
        }
    }

    @Test
    fun requestFocusToVisibleItemByColumnAndRow() {
        val (row, column) = 1 to 2
        var actualFocusedRow = -1
        var actualFocusedColumn = -1
        val fixedColumn = GridCells.Fixed(4)
        rule.setContent {
            SampleRowList(gridState, onFocusChange = { index, state, key ->
                if (state.isFocused) {
//                    focusedIndex = index
                    actualFocusedRow = index / 4
                    actualFocusedColumn = index % 4
                }
            })
        }

        rule.runOnIdle {
            gridState.requestFocusByRowAndColumn(row, column, 4)
            assertTrue(row == actualFocusedRow && column == actualFocusedColumn)
        }
    }


    @Test
    fun requestFocusOutOfBoundsRowThrowsException() {

        val gridCells = GridCells.Fixed(4)
        rule.setContent {
            SampleRowList(gridState, gridCells = gridCells)
        }

        rule.runOnIdle {
            assertThrows(IllegalStateException::class.java) {
                val (column, row) = 2 to 5
                gridState.requestFocusByRowAndColumn(
                    row = row,
                    column = column,
                    fixedItemsInEachColumn = 4
                )
            }
        }
    }

    @Test
    fun retrieveFocusRequesterByPositionAndRequestFocusSucceed() {
        var focusedIndex = -1
        rule.setContent {
            SampleRowList(gridState, onFocusChange = { index, focusState, key ->
                if (focusState.isFocused) {
                    focusedIndex = index
                }
            })
        }

        rule.runOnIdle {
            val focusRequester = gridState.getFocusRequesterForIndexOrNull(index = 4)
            assertNotNull(focusRequester)
            focusRequester!!.requestFocus()
            assertTrue(focusedIndex == 4)
        }
    }

    @Test
    fun tryToRetrieveFocusRequesterOutOfBoundsReturnNull() {
        val gridCells = GridCells.Fixed(4)
        rule.setContent {
            SampleRowList(gridState, gridCells = gridCells)
        }

        rule.runOnIdle {
            assertNull(gridState.getFocusRequesterForIndexOrNull(25))
        }
    }

//
//    @Test
//    fun scrollNavigateAndRestoreSucceed() {
//        fun randomMove(focusManager: FocusManager) {
//            for (i in 0..10) {
//                if ((0..1).random() % 2 == 0) {
//                    focusManager.moveFocus(FocusDirection.Down)
//                } else {
//                    focusManager.moveFocus(FocusDirection.Left)
//                }
//            }
//        }
//
//        var focusedIndex = -1
//        lateinit var navController: NavHostController
//        lateinit var focusManager: FocusManager
//        lateinit var gridState: LazyFocusGridState
//        rule.setContent {
//
//            navController = rememberNavController()
//            gridState = rememberFocusGridModifier()
//            focusManager = LocalFocusManager.current
//            NavHost(navController = navController, startDestination = "firstScreen") {
//                composable("firstScreen") {
//                    SampleRowList(gridState, onFocusChange = { index, state, key ->
//                        if (state.isFocused) {
//                            focusedIndex = index
//                        }
//                    })
//                }
//                composable("secondScreen") {
//                    LaunchedEffect(true) {
//                        navController.navigateUp()
//                    }
//                }
//            }
//        }
//        rule.runOnIdle {
//            //positionBeforeRestore
//            runBlocking {
//                gridState.restoreFocus(0)
//                randomMove(focusManager)
//                navController.navigate("secondScreen")
//                val focusIndexBeforeRestore = focusedIndex
//                println("focus index before restore $focusedIndex")
//                gridState.restoreFocus()
//                println("focus index after restore $focusedIndex")
//                assertTrue(focusIndexBeforeRestore == focusedIndex)
//            }
//        }
//
//    }

}