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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.Card
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.style.TextAlign
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
class LazyFocusListsHostStateTest {
    @get:Rule
    val rule = createComposeRule()

    private val columnAdapter: LazyFocusListsHostState = LazyFocusListsHostState()


    @Composable
    private fun SampleRowList(
        listAdapterState: LazyFocusListsHostState,
        listSize: Int = 30,
        onFocusChange: (cIndex: Int, rIndex: Int, focusState: FocusState, key: String?) -> Unit = { _, _, _, _ -> }
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            state = listAdapterState.parentLazyList
        ) {
            items(listSize, key = { columnIndex -> columnIndex }) { columnIndex ->
                val rowAdapter = rememberLazyFocusListState(columnIndex)
                val childFR = remember {
                    FocusRequester()
                }
                val cardSize = remember {
                    if (columnIndex % 2 == 0) {
                        200.dp
                    } else {
                        150.dp
                    }
                }
                LazyRow(
                    modifier = Modifier
                        .fillMaxSize()
                        .focusChildList(listAdapterState, rowAdapter, childFR),
                    verticalAlignment = Alignment.CenterVertically,
                    state = rowAdapter.lazyList
                ) {
                    items(listSize, key = { rowIndex ->
                        keyPattern(
                            columnIndex,
                            rowIndex
                        )
                    }) { rowIndex ->

                        Card(
                            modifier = Modifier
                                .height(cardSize)
                                .aspectRatio(16 / 9f)
                                .focusChild(
                                    parentState = rowAdapter,
                                    remember {
                                        FocusItem(
                                            focusRequester = if (rowIndex == 0) childFR else FocusRequester(),
                                            index = rowIndex,
                                            key = keyPattern(columnIndex, rowIndex)
                                        )
                                    }
                                )
                                .onFocusChanged {
                                    onFocusChange(
                                        columnIndex,
                                        rowIndex,
                                        it,
                                        keyPattern(columnIndex, rowIndex)
                                    )
                                }

                        ) {
                            Text(
                                keyPattern(columnIndex, rowIndex),
                                modifier = Modifier.fillMaxSize(),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

    }


    private fun keyPattern(columnIndex: Int, rowIndex: Int) =
        "column = $columnIndex| row = $rowIndex"


    @Test
    fun restoreFocusOnInitalBoot() {
        var rowIndex = -1
        var columnIndex = -1


        rule.setContent {
            SampleRowList(
                listAdapterState = columnAdapter,
                onFocusChange = { cIndex, rIndex, state, key ->
                    if (state.isFocused) {
                        columnIndex = cIndex
                        rowIndex = rIndex

                    }
                })
        }
        rule.runOnIdle {
            columnAdapter.restoreFocus(0, 0)
            assertTrue(columnIndex == 0 && rowIndex == 0)
        }
    }


    @Test
    fun requestFocusToSpecificIndex() {
        val (column, row) = 2 to 0
        var rowIndex = -1
        var columnIndex = -1


        rule.setContent {
            SampleRowList(
                listAdapterState = columnAdapter,
                onFocusChange = { cIndex, rIndex, state, key ->
                    if (state.isFocused) {
                        columnIndex = cIndex
                        rowIndex = rIndex

                    }
                })
        }
        rule.runOnIdle {
            columnAdapter.requestFocus(column, row)
            assertTrue(columnIndex == column && rowIndex == row)
        }
    }

    @Test
    fun requestFocusToOutOfBoundsIndexReturnFalse() {
        rule.setContent {
            SampleRowList(columnAdapter)
        }
        rule.runOnIdle {
            val requestFocusTarget = columnAdapter.parentLazyList.layoutInfo.visibleItemsInfo.size
            assertFalse(columnAdapter.requestFocus(column = requestFocusTarget, 0))
        }
    }

    @Test
    fun scrollAndRequestFocusSucceed() {
        val (column, row) = 10 to 0
        var rowIndex = -1
        var columnIndex = -1
        rule.setContent {
            SampleRowList(columnAdapter, onFocusChange = { cIndex, rIndex, state, key ->
                if (state.isFocused) {
                    columnIndex = cIndex
                    rowIndex = rIndex
                }
            })
        }
        rule.runOnIdle {
            runBlocking {
                columnAdapter.scrollAndFocus(columnIndexToScroll = column, row = row)
                assertTrue(rowIndex == row && columnIndex == column)
            }
        }
    }


    @Test
    fun scrollToOutOfBoundsThrowsException() {
        rule.setContent {
            SampleRowList(columnAdapter, onFocusChange = { cIndex, rIndex, state, key ->

            })
        }
        rule.runOnIdle {
            assertThrows(IllegalStateException::class.java) {
                runBlocking {
                    columnAdapter.scrollAndFocus(
                        columnAdapter.parentLazyList.layoutInfo.totalItemsCount + 1,
                        0
                    )
                }
            }
        }
    }

    //todo - request focus to visible columns/rows is not available for index that is not row 0
    @Test
    fun requestFocusToVisibleItemByKeySucceed() {
        lateinit var keyToRequestFocus: String
        lateinit var focusedKey: String
        rule.setContent {
            SampleRowList(columnAdapter, onFocusChange = { cIndex, rIndex, state, key ->
                if (state.isFocused && key != null) {
                    focusedKey = key
                }
            })
        }
        rule.runOnIdle {
            runBlocking {
                keyToRequestFocus = keyPattern(1, 0)
                assertTrue(columnAdapter.requestFocus(keyToRequestFocus))
                assertTrue(focusedKey == keyToRequestFocus)
            }
        }
    }

    @Test
    fun restoreFocusWithNegativeDefaultIndexThrowsException() {
        var rowIndex = -1
        var columnIndex = -1
        rule.setContent {
            SampleRowList(columnAdapter, onFocusChange = { cIndex, rIndex, focusState, key ->
                if (focusState.isFocused) {
                    columnIndex = cIndex
                    rowIndex = rIndex
                }
            })
        }
        rule.runOnIdle {
            assertTrue(columnAdapter.restoreFocus(1, 0))
            assertTrue(columnIndex == 1 && rowIndex == 0)
        }
    }


    @Test
    fun requestFocusToEachVisibleItemSucceed() {
        var columnIndex = -1
        var rowIndex = -1

        val listSize = 10
        rule.setContent {
            SampleRowList(
                columnAdapter,
                listSize = listSize,
                onFocusChange = { cIndex, rIndex, focusState, key ->
                    if (focusState.isFocused) {
                        columnIndex = cIndex
                        rowIndex = rIndex
                        println("magmagen focus at column $cIndex | row $rIndex")
                    }
                })
        }
        rule.runOnIdle {
            for (i in columnAdapter.parentLazyList.layoutInfo.visibleItemsInfo.indices) {
                for (j in 0 until 3) {
                    assertTrue(columnAdapter.requestFocus(i, j))
                    assertTrue(columnIndex == i && rowIndex == j)
                }
            }
        }
    }

    @Test
    fun requestFocusToEachItemWithoutScrollFails() {
        var columnIndex = -1
        var rowIndex = -1

        val listSize = 30
        rule.setContent {
            SampleRowList(
                columnAdapter,
                listSize = listSize,
                onFocusChange = { cIndex, rIndex, focusState, key ->
                    if (focusState.isFocused) {
                        columnIndex = cIndex
                        rowIndex = rIndex
                    }
                })
        }
        rule.runOnIdle {
            for (i in 0 until columnAdapter.parentLazyList.layoutInfo.totalItemsCount) {
                for (j in 0 until 3) {
                    if (i in columnAdapter.parentLazyList.layoutInfo.visibleItemsInfo.indices) {
                        assertTrue(columnAdapter.requestFocus(i, j))
                        assertTrue(columnIndex == i && rowIndex == j)
                    } else {
                        assertFalse(columnAdapter.requestFocus(i, j))
                    }
                }
            }
        }
    }

    @Test
    fun requestFocusToEachItemWithScrollSucceed() {
        val listSize = 30
        rule.setContent {
            SampleRowList(columnAdapter, listSize = listSize)
        }
        rule.runOnIdle {
            runTest {
                for (i in 0 until columnAdapter.parentLazyList.layoutInfo.visibleItemsInfo.size) {
                    for(j in 0 until listSize) {
                        columnAdapter.scrollAndFocus(i,j)
                    }
                }
            }
        }
    }


    @Test
    fun restoreFocusWithDefaultEmptyKeyThrowsError() {
        val targetKeyFocus = ""
        lateinit var focusedKey: String
        rule.setContent {
            SampleRowList(columnAdapter, onFocusChange = { cIndex, rIndex, state, key ->
                if (state.isFocused && key != null) {
                    focusedKey = key
                }
            })
        }
        rule.runOnIdle {
            assertThrows(IllegalStateException::class.java) {
                columnAdapter.requestFocus(targetKeyFocus)
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
            SampleRowList(columnAdapter, onFocusChange = { cIndex, rIndex, state, key ->
                if (state.isFocused && key != null) {
                    focusedKey = key
                }
            })
        }
        rule.runOnIdle {
            assertThrows(IllegalStateException::class.java) {
                columnAdapter.requestFocus(targetKeyFocus)
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
            SampleRowList(
                columnAdapter,
                listSize = itemsSize,
                onFocusChange = { cIndex, rIndex, state, key ->
                    if (state.isFocused && key != null) {
                        focusedKey = key
                    }
                })
        }
        rule.runOnIdle {
            val unVisibleItem =
                keyPattern(10, 10)
            assertFalse(columnAdapter.requestFocus(unVisibleItem))
            assertThrows(UninitializedPropertyAccessException::class.java) {
                @Suppress("UNUSED_EXPRESSION")
                focusedKey
            }
        }
    }

    @Test
    fun requestFocusToOutOfBoundsThrowsException() {
        val listSize = 30
        rule.setContent {
            SampleRowList(
                columnAdapter,
                listSize = listSize,
                onFocusChange = { cIndex, rIndex, state, key ->

                })
        }
        rule.runOnIdle {
            assertThrows(IllegalStateException::class.java) {
                columnAdapter.requestFocus(listSize, 0)
            }
        }
    }

//    @Test
//    fun scrollNavigateAndRestoreSucceed() {
//        var columnIndex = -1
//        var rowIndex = -1
//        lateinit var navController: NavHostController
//        lateinit var focusManager: FocusManager
//        lateinit var columnAdapter: LazyFocusListsHostState
//        rule.setContent {
//            columnAdapter = rememberLazyFocusListsHostState()
//            navController = rememberNavController()
//            focusManager = LocalFocusManager.current
//            NavHost(navController = navController, startDestination = "firstScreen") {
//                composable("firstScreen") {
//                    SampleRowList(columnAdapter, onFocusChange = { cIndex, rIndex, state, key ->
//                        if (state.isFocused) {
//                            println("focus = current values column $columnIndex row $rowIndex")
//                            columnIndex = cIndex
//                            rowIndex = rIndex
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
//                assertTrue(columnAdapter.restoreFocus(0, 0))
//                focusManager.moveFocus(FocusDirection.Down)
//                focusManager.moveFocus(FocusDirection.Down)
//                focusManager.moveFocus(FocusDirection.Left)
//                focusManager.moveFocus(FocusDirection.Left)
//                //position to c2|r2
//                println("current values column $columnIndex row $rowIndex")
//                navController.navigate("secondScreen")
//                assertTrue(columnAdapter.restoreFocus())
//                println("current values column $columnIndex row $rowIndex")
//                assertTrue(2 == columnIndex && 2 == rowIndex)
//            }
//        }
//
//    }


    @Test
    fun scrollAndRequestFocusMultipleTimesSucceed() {
        var (requestColumn, requestedRow) = -1 to -1
        var (focusedColumn, focusedRow) = -1 to -1

        rule.setContent {
            SampleRowList(columnAdapter, onFocusChange = {cIndex: Int, rIndex: Int, focusState: FocusState, key: String? ->
                focusedColumn = cIndex
                focusedRow = rIndex
            })
        }
        rule.runOnIdle {
            runBlocking {
                requestColumn = 1; requestedRow = 0
                assertTrue(columnAdapter.scrollAndFocus(requestColumn, requestedRow))
                assertTrue(requestColumn == focusedColumn && requestedRow == focusedRow)
                requestColumn = 1; requestedRow = 10
                assertTrue(columnAdapter.scrollAndFocus(requestColumn, requestedRow))
                assertTrue(requestColumn == focusedColumn && requestedRow == focusedRow)
                requestColumn = 0; requestedRow = 0
                assertTrue(columnAdapter.scrollAndFocus(requestColumn, requestedRow))
                assertTrue(requestColumn == focusedColumn && requestedRow == focusedRow)
            }
        }
    }


    @Test
    fun requestFocusToUnInitializeListThrowsException() {
        assertThrows(IllegalStateException::class.java) {
            columnAdapter.requestFocus(1, 0)
        }
    }

    @Test
    fun retrieveFocusRequesterByPositionSucceed() {
        rule.setContent {
            SampleRowList(columnAdapter)
        }

        rule.runOnIdle {
            assertNotNull(columnAdapter.getFocusRequester(1, 1))
        }
    }

    @Test
    fun retrieveFocusRequesterByKeyFocusSucceed() {
        rule.setContent {
            SampleRowList(columnAdapter)
        }

        rule.runOnIdle {
            assertNotNull(columnAdapter.getFocusRequester(keyPattern(1, 1)))
        }
    }

    @Test
    fun tryToRetrieveFocusRequesterOutOfBoundsReturnNull() {
        rule.setContent {
            SampleRowList(columnAdapter)
        }

        rule.runOnIdle {
            assertNull(columnAdapter.getFocusRequester(keyPattern(10, 10)))
        }
    }
}