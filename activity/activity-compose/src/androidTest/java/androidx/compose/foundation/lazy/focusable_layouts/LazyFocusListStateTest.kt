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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.Card
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LazyFocusListStateTest{

    @get:Rule
    val rule = createComposeRule()
    private val listAdapterState = LazyFocusListState(index = 0)

    @Composable
    private fun SampleRowList(
        listAdapterState: LazyFocusListState,
        listSize: Int = 30,
        onFocusChange: (index: Int, focusState: FocusState, key: String?) -> Unit = { _, _, _->}
    ) {
        LazyRow(
            modifier = Modifier
                .fillMaxSize()
                .focusTarget(),
            state = listAdapterState.lazyList
        ) {
            items(listSize) { index ->
                val isFocused = remember { mutableStateOf(false) }
                Card(
                    modifier = Modifier
                        .width(100.dp)
                        .aspectRatio(16 / 9f)
                        .background(if (isFocused.value) Color.Red else Color.Blue)
                        .onFocusChanged {
                            onFocusChange(index, it, index.toString())
                        }
                        .focusChild(
                            parentState = listAdapterState,
                            remember {
                                FocusItem(
                                    focusRequester = FocusRequester(),
                                    index = index,
                                    key = index.toString()
                                )
                            }
                        )
                ) {
                    Text("index = $index")
                }
            }
        }
    }

    @Test
    fun restoreFocusOnInitalBoot() {
        var focusedIndex: Int = -1


        rule.setContent {
            SampleRowList(listAdapterState, onFocusChange = { index, state, key ->
                if (state.isFocused) {
                    focusedIndex = index
                }
            })
        }
        rule.runOnIdle {
            listAdapterState.restoreFocus()
            assertTrue(focusedIndex == 0)
        }
    }


    @Test
    fun requestFocusToSpecificIndex() {
        var focusedIndex: Int = -1

        val requestFocusTarget = 9
        rule.setContent {
            SampleRowList(listAdapterState, onFocusChange = { index, state, key ->
                if (state.isFocused) {
                    focusedIndex = index
                }
            })
        }
        rule.runOnIdle {
            listAdapterState.requestFocus(requestFocusTarget)
            assertTrue(focusedIndex == requestFocusTarget)
        }
    }

    @Test
    fun requestFocusToOutOfBoundsIndexReturnFalse() {
        rule.setContent {
            SampleRowList(listAdapterState)
        }
        rule.runOnIdle {
            val requestFocusTarget = listAdapterState.lazyList.layoutInfo.visibleItemsInfo.size+1
            assertFalse(listAdapterState.requestFocus(requestFocusTarget))
        }
    }

    @Test
    fun scrollAndRequestFocusSucceed() {
        var focusedIndex: Int = -1

        val requestFocusTarget = 29
        rule.setContent {
            SampleRowList(listAdapterState, onFocusChange = { index, state, key ->
                if (state.isFocused) {
                    focusedIndex = index
                }
            })
        }
        rule.runOnIdle {
            runBlocking {
                listAdapterState.scrollToAndRequestFocus(requestFocusTarget, 0)
                assertTrue(requestFocusTarget == focusedIndex)
            }
        }
    }


    @Test
    fun scrollToOutOfBoundsThrowsException() {

        val listSize = 30
        rule.setContent {
            SampleRowList(listAdapterState, listSize = listSize, onFocusChange = { index, state, key ->

            })
        }
        rule.runOnIdle {
            assertThrows(IllegalStateException::class.java) {
                runBlocking {
                    listAdapterState.scrollToAndRequestFocus(listSize + 1, 0)
                }
            }
        }
    }


    @Test
    fun requestFocusToVisibleItemByKeySucceed() {

        val targetKeyFocus = "5"
        lateinit var  focusedKey: String
        rule.setContent {
            SampleRowList(listAdapterState, onFocusChange = { index, state, key ->
                if (state.isFocused && key!= null ) {
                    focusedKey = key
                }
            })
        }
        rule.runOnIdle {
            assertTrue(listAdapterState.requestFocus(targetKeyFocus))
            assertTrue(focusedKey == targetKeyFocus)
        }
    }

    @Test
    fun restoreFocusWithNegativeDefaultIndexThrowsException() {

        val restoreToDefaultIndex = -1
        rule.setContent {
            SampleRowList(listAdapterState)
        }
        rule.runOnIdle {
            assertThrows(IllegalStateException::class.java) {
                listAdapterState.restoreFocus(restoreToDefaultIndex)
            }
        }
    }


    @Test
    fun requestFocusToEachVisibleItemSucceed() {

        val listSize = 30
        rule.setContent {
            SampleRowList(listAdapterState, listSize = listSize)
        }
        rule.runOnIdle {
            for (i in listAdapterState.lazyList.layoutInfo.visibleItemsInfo.indices) {
                assertTrue(listAdapterState.requestFocus(i))

            }
        }
    }

    @Test
    fun requestFocusToEachItemWithoutScrollFails() {

        rule.setContent {
            SampleRowList(listAdapterState)
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

        rule.setContent {
            SampleRowList(listAdapterState)
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
        lateinit var  focusedKey: String
        rule.setContent {
            SampleRowList(listAdapterState, onFocusChange = { index, state, key ->
                if (state.isFocused && key!= null ) {
                    focusedKey = key
                }
            })
        }
        rule.runOnIdle {
            assertThrows(IllegalStateException::class.java) {
                listAdapterState.restoreFocus(targetKeyFocus)
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
        lateinit var  focusedKey: String
        rule.setContent {
            SampleRowList(listAdapterState, onFocusChange = { index, state, key ->
                if (state.isFocused && key!= null ) {
                    focusedKey = key
                }
            })
        }
        rule.runOnIdle {
            assertThrows(IllegalStateException::class.java) {
                listAdapterState.requestFocus(targetKeyFocus)
            }
            assertThrows(UninitializedPropertyAccessException::class.java) {
                @Suppress("UNUSED_EXPRESSION")
                focusedKey
            }
        }
    }


    @Test
    fun requestFocusToUnVisibleItemByKeyFailed() {


        lateinit var  focusedKey: String
        rule.setContent {
            SampleRowList(listAdapterState, onFocusChange = { index, state, key ->
                if (state.isFocused && key!= null ) {
                    focusedKey = key
                }
            })
        }
        rule.runOnIdle {
            val targetKeyFocus = listAdapterState.lazyList.layoutInfo.visibleItemsInfo.size +1
            assertFalse(listAdapterState.requestFocus(targetKeyFocus))
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
            SampleRowList(listAdapterState, listSize = listSize, onFocusChange = { index, state,key  ->

            })
        }
        rule.runOnIdle {
            assertThrows(IllegalStateException::class.java) {
                listAdapterState.requestFocus(listSize+1)
            }
        }
    }

    @Test
    fun scrollAndRequestFocusMultipleTimesSucceed() {
        var focusedIndex: Int = -1

        var requestFocusTarget = 29
        rule.setContent {
            SampleRowList(listAdapterState, onFocusChange = { index, state, key ->
                if (state.isFocused) {
                    focusedIndex = index
                }
            })
        }
        rule.runOnIdle {
            runBlocking {
                listAdapterState.scrollToAndRequestFocus(requestFocusTarget, 0)
                assertTrue(requestFocusTarget == focusedIndex)
                requestFocusTarget = 15
                listAdapterState.scrollToAndRequestFocus(requestFocusTarget, 0)
                assertTrue(requestFocusTarget == focusedIndex)
                requestFocusTarget = 0
                listAdapterState.scrollToAndRequestFocus(requestFocusTarget, 0)
                assertTrue(requestFocusTarget == focusedIndex)
            }
        }
    }


    @Test
    fun requestFocusToUnInitializeListThrowsException() {

        val requestFocusTarget = 11
        assertThrows(IllegalStateException::class.java) {
            listAdapterState.requestFocus(requestFocusTarget)
        }
    }


//    @Test
//    fun scrollNavigateAndRestoreSucceed() {
//        var focusedIndex = -1
//        lateinit var navController: NavHostController
//        lateinit var focusManager: FocusManager
//        lateinit var columnAdapter: LazyFocusListState
//        rule.setContent {
//            columnAdapter = rememberLazyFocusListState()
//            navController = rememberNavController()
//            focusManager = LocalFocusManager.current
//            NavHost(navController = navController, startDestination = "firstScreen") {
//                composable("firstScreen") {
//                    SampleRowList(columnAdapter, onFocusChange = { index, state, key ->
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
//                assertTrue(columnAdapter.restoreFocus())
//                focusManager.moveFocus(FocusDirection.Left)
//                focusManager.moveFocus(FocusDirection.Left)
//                //position to r2
//                println("current values focused index $focusedIndex")
//                navController.navigate("secondScreen")
//                assertTrue(columnAdapter.restoreFocus())
//                println("current values focused index $focusedIndex")
//                assertTrue(focusedIndex == 2)
//            }
//        }
//
//    }

}