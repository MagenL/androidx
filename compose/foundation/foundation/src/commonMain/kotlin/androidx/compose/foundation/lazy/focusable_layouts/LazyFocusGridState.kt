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

import androidx.annotation.IntRange
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridPrefetchStrategy
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.focus.FocusRequester


@Composable
fun rememberFocusGridState(
    firstVisibleItemIndex: Int = 0,
    firstVisibleItemScrollOffset:Int = 0,
): LazyFocusGridState =
    rememberSaveable(saver = LazyFocusGridState.Saver) {
        LazyFocusGridState(
            LazyGridState(
                firstVisibleItemIndex = firstVisibleItemIndex,
                firstVisibleItemScrollOffset = firstVisibleItemScrollOffset,
            )
        )
    }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun rememberFocusGridState(
    firstVisibleItemIndex: Int = 0,
    firstVisibleItemScrollOffset:Int = 0,
    prefetchStrategy: LazyGridPrefetchStrategy
): LazyFocusGridState =
    rememberSaveable(saver = LazyFocusGridState.Saver) {
        LazyFocusGridState(
            LazyGridState(
                firstVisibleItemIndex = firstVisibleItemIndex,
                firstVisibleItemScrollOffset = firstVisibleItemScrollOffset,
                prefetchStrategy = prefetchStrategy
            )
        )
    }


@Immutable
class LazyFocusGridState(
    val lazyGridState: LazyGridState = LazyGridState(),
    override var focusedChildIndex: Int = -1
) : AbsFocusController() {


    companion object {
        val Saver: Saver<LazyFocusGridState, Any> = listSaver(
            save = {
                listOf(
                    it.lazyGridState.firstVisibleItemIndex,
                    it.lazyGridState.firstVisibleItemScrollOffset,
                    it.focusedChildIndex,
                )
            },
            restore = {
                LazyFocusGridState(
                    LazyGridState(it[0], it[1]),
                    focusedChildIndex = it[2],
                )
            }
        )
    }

    override fun restoreFocus(): Boolean {
        check(lazyGridState.layoutInfo.visibleItemsInfo.isNotEmpty(), lazyMessage = {
            "can't restore focus to the list because it is not on the screen"
        })

        return super.restoreFocus(
            firstVisibleItem = lazyGridState.layoutInfo.visibleItemsInfo[0].index,
            isItemOnScreen = { requestedPosition: Int ->
                lazyGridState.layoutInfo.visibleItemsInfo.find { it.index == requestedPosition } != null
            })

    }

    fun restoreFocus(
        @IntRange(from = 0) defaultIndex: Int
    ): Boolean {
        check(lazyGridState.layoutInfo.visibleItemsInfo.isNotEmpty(), lazyMessage = {
            "can't restore focus to the grid because it is not on the screen"
        })
        check(defaultIndex > -1, lazyMessage = {
            "can't restore focus to negative value"
        })
        return super.restoreFocus(
            defaultPosition = defaultIndex,
            firstVisibleItem = lazyGridState.layoutInfo.visibleItemsInfo[0].index,
            isItemOnScreen = { requestedPosition: Int ->
                lazyGridState.layoutInfo.visibleItemsInfo.find { it.index == requestedPosition } != null
            })
    }

    fun restoreFocus(defaultKey: String): Boolean {
        check(lazyGridState.layoutInfo.visibleItemsInfo.isNotEmpty(), lazyMessage = {
            "can't restore focus to the grid because it is not on the screen"
        })
        check(defaultKey.isNotEmpty(), lazyMessage = {
            "restoreFocus with empty key is not allowed please use restoreFocus() with no extra parameters"
        })
        val defaultIndex = focusItems.find { it.key == defaultKey }?.takeIf { focusedItem ->
            lazyGridState.layoutInfo.visibleItemsInfo.find { it.index == focusedItem.index } != null
        }?.index ?: -1
        return super.restoreFocus(
            defaultPosition = defaultIndex,
            firstVisibleItem = lazyGridState.layoutInfo.visibleItemsInfo[0].index,
            isItemOnScreen = { requestedPosition: Int ->
                lazyGridState.layoutInfo.visibleItemsInfo.find { it.index == requestedPosition } != null
            })
    }


    override fun requestFocus(@IntRange(from = 0) index: Int): Boolean {
        check(
            lazyGridState.layoutInfo.totalItemsCount > index,
            lazyMessage = { "the list has no items to focus" }
        )
        check(index > -1, lazyMessage = {
            "index to focus must be greater than -1"
        })
        val visibleItems = lazyGridState.layoutInfo.visibleItemsInfo
        if (visibleItems.isEmpty()) return false
        return super.requestFocus(index, isItemOnScreen = { requestedPosition ->
            visibleItems.find { it.index == requestedPosition } != null
        })
    }

    override fun requestFocus(key: String): Boolean {
        check(
            lazyGridState.layoutInfo.totalItemsCount > 0,
            lazyMessage = { "the list has no items to focus" }
        )
        check(key.isNotEmpty(), lazyMessage = {
            "key must not be empty"
        })
        if (focusItems.isEmpty()) return false
        val indexOfRequestedItem = focusItems.indexOfFirst { it.key == key }
        return super.requestFocus(indexOfRequestedItem, isItemOnScreen = { requestedPosition ->
            lazyGridState.layoutInfo.visibleItemsInfo.find { it.index == requestedPosition } != null
        })
    }


    suspend fun scrollToAndRequestFocus(
        @IntRange(from = 0) scrollToPosition: Int,
        scrollOffset: Int = 0
    ): Boolean {
        check(
            lazyGridState.layoutInfo.totalItemsCount > scrollToPosition,
            lazyMessage = { "[scrollToPosition] is greater than [totalItemsCount] of the items in the grid" }
        )
        check(scrollToPosition > -1, lazyMessage = {
            "scroll to position must be greater than -1"
        })
        lazyGridState.scrollToItem(scrollToPosition, scrollOffset)
        return requestFocus(scrollToPosition)
    }

    /**
     * this function will work only if you use [GridCells.Fixed] in [LazyVerticalGrid] at **columns** or **rows** attribute
     * in [Orientation.Vertical] - [column] refers to the vertical index of grid, [row] refers to the horizontal index in the row
     * in [Orientation.Horizontal] - [column] refers to the horizontal index of the grid, [row] refers to the vertical index of the item in the vertically aspect
     * */
    suspend fun scrollToAndRequestFocus(
        @IntRange(from = 0) column: Int,
        @IntRange(from = 0) row: Int,
        @IntRange(from = 1) fixedItemsSize: Int,
        scrollOffset: Int = 0,
    ): Boolean {
        check(
            lazyGridState.layoutInfo.totalItemsCount > 0,
            lazyMessage = { "the list has no items to focus" }
        )
        check(column > -1 && row > -1 && fixedItemsSize > 0, lazyMessage = {
            "column/row must be greater than -1, fixedItemsSize must be greater than 0"
        })
        val orientation = lazyGridState.layoutInfo.orientation
        check(
            (row <= fixedItemsSize && orientation == Orientation.Vertical) ||
                    (lazyGridState.layoutInfo.orientation == Orientation.Horizontal && column <= fixedItemsSize),
            lazyMessage = {
                "Base on your orientation: $orientation attribute [fixedItemsInEachColumn] could not be bigger than the corresponding requested position. found ${if (orientation == Orientation.Vertical) "[row] $row " else "[column]"}index when the max is $fixedItemsSize"
            }
        )
        val itemToFocus = getItemIndexToFocus(
            row = row,
            column = column,
            fixedItemsSize = fixedItemsSize
        )
        check(lazyGridState.layoutInfo.totalItemsCount > itemToFocus,
            lazyMessage = {
                "index to focus $itemToFocus could not be higher than the total list size: ${lazyGridState.layoutInfo.totalItemsCount}"
            }
        )
        lazyGridState.scrollToItem(itemToFocus, scrollOffset)
        return requestFocus(itemToFocus)
    }

    private fun getItemIndexToFocus(
        @IntRange(from = 0) row: Int,
        @IntRange(from = 0) column: Int,
        @IntRange(from = 1) fixedItemsSize: Int,
    ): Int {
        check(column > -1 && row > -1 && fixedItemsSize > 0, lazyMessage = {
            "column/row must be greater than -1, fixedItemsSize must be greater than 0"
        })
        return if (lazyGridState.layoutInfo.orientation == Orientation.Horizontal) {
            check(column <= fixedItemsSize, lazyMessage = {
                "requested column index that is greater than maximum. max defined as $fixedItemsSize and requested row was $column"
            })

            (row * fixedItemsSize) + column
        } else {
            check(row <= fixedItemsSize, lazyMessage = {
                "requested row index that is greater than maximum. max defined as $fixedItemsSize and requested row was $row"
            })
            (column * fixedItemsSize) + row
        }
    }


    fun requestFocusByRowAndColumn(
        @IntRange(from = 0) column: Int,
        @IntRange(from = 0) row: Int,
        @IntRange(from = 1) fixedItemsInEachColumn: Int
    ): Boolean {
        getFocusRequesterForIndexOrNull(
            column = column,
            row = row,
            fixedItemsSize = fixedItemsInEachColumn
        )?.requestFocus() ?: return false
        return true
    }


    fun getFocusRequesterForIndexOrNull(
        @IntRange(from = 0) column: Int,
        @IntRange(from = 0) row: Int,
        @IntRange(from = 1) fixedItemsSize: Int,
    ): FocusRequester? {
        check(
            lazyGridState.layoutInfo.totalItemsCount > 0,
            lazyMessage = { "the list has no items to focus" }
        )
        val indexToFocus = getItemIndexToFocus(
            row = row,
            column = column,
            fixedItemsSize = fixedItemsSize,
        )
        check(
            lazyGridState.layoutInfo.totalItemsCount > indexToFocus,
            lazyMessage = { "the list has no items to focus" }
        )
        return getFocusRequesterForIndexOrNull(indexToFocus)
    }

    override fun getFocusRequesterForIndexOrNull(
        @IntRange(from = 0) index: Int
    ): FocusRequester? {
        check(
            lazyGridState.layoutInfo.totalItemsCount > index,
            lazyMessage = { "the list has no items to focus" }
        )
        check(index > -1, lazyMessage = {
            "index must be greater than -1"
        })
        val visibleItems = lazyGridState.layoutInfo.visibleItemsInfo
        check(visibleItems.isNotEmpty(), lazyMessage = {
            "can't get focus requester for invisible item."
        })
        return super.getFocusRequesterForIndexOrNull(index, isItemOnScreen = { requestedPosition ->
            visibleItems.find { it.index == requestedPosition } != null
        })
    }


}