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
import androidx.compose.foundation.lazy.LazyListPrefetchStrategy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.focus.FocusRequester


@Composable
fun rememberLazyFocusListState(
    indexOfListInParent: Int = 0,
    firstVisibleItemIndex: Int = 0,
    firstVisibleItemScrollOffset: Int = 0,
): LazyFocusListState =
    rememberSaveable(saver = LazyFocusListState.Saver) {
        LazyFocusListState(
            index = indexOfListInParent,
            lazyList = LazyListState(
                firstVisibleItemIndex = firstVisibleItemIndex,
                firstVisibleItemScrollOffset = firstVisibleItemScrollOffset,
            )
        )
    }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun rememberLazyFocusListState(
    indexOfListInParent: Int = 0,
    firstVisibleItemIndex: Int = 0,
    firstVisibleItemScrollOffset: Int = 0,
    prefetchStrategy: LazyListPrefetchStrategy
): LazyFocusListState =
    rememberSaveable(saver = LazyFocusListState.Saver) {
        LazyFocusListState(
            index = indexOfListInParent,
            lazyList = LazyListState(
                firstVisibleItemIndex = firstVisibleItemIndex,
                firstVisibleItemScrollOffset = firstVisibleItemScrollOffset,
                prefetchStrategy = prefetchStrategy
            )
        )
    }



@Immutable
class LazyFocusListState(
    val lazyList: LazyListState = LazyListState(),
    @IntRange(from = 0) var index: Int,
    override var focusedChildIndex: Int = -1,
    var isVisited: Boolean = false
) : AbsFocusController(), ModifierParentalFocusableNode {
    companion object {
        val Saver: Saver<LazyFocusListState, Any> = listSaver(
            save = {
                listOf(
                    it.lazyList.firstVisibleItemIndex,
                    it.lazyList.firstVisibleItemScrollOffset,
                    it.index,
                    it.focusedChildIndex,
                    it.isVisited
                )
            },
            restore = {
                LazyFocusListState(
                    lazyList = LazyListState(it[0] as Int, it[1] as Int),
                    index = it[2] as Int,
                    focusedChildIndex = it[3] as Int,
                    isVisited = it[4] as Boolean
                )
            }
        )
    }


    override fun restoreFocus(): Boolean {
        check(lazyList.layoutInfo.visibleItemsInfo.isNotEmpty(), lazyMessage = {
            "can't restore focus to the list because it is not on the screen"
        })
        return super.restoreFocus(
            lazyList.layoutInfo.visibleItemsInfo[0].index,
            isItemOnScreen = { requestedPosition: Int ->
                lazyList.layoutInfo.visibleItemsInfo.find { it.index == requestedPosition } != null
            })
    }

    fun restoreFocus(defaultKey: String): Boolean {
        check(lazyList.layoutInfo.visibleItemsInfo.isNotEmpty(), lazyMessage = {
            "can't restore focus to the grid because it is not on the screen"
        })
        check(defaultKey.isNotEmpty(), lazyMessage = {
            "restoreFocus with empty key is not allowed please use restoreFocus() with no extra parameters"
        })
        val defaultIndex = focusItems.find { it.key == defaultKey }?.takeIf { focusedItem ->
            lazyList.layoutInfo.visibleItemsInfo.find { it.index == focusedItem.index } != null
        }?.index ?: -1
        return super.restoreFocus(
            defaultPosition = defaultIndex,
            firstVisibleItem = lazyList.layoutInfo.visibleItemsInfo[0].index,
            isItemOnScreen = { requestedPosition: Int ->
                lazyList.layoutInfo.visibleItemsInfo.find { it.index == requestedPosition } != null
            })
    }


    fun restoreFocus(defaultIndex: Int): Boolean {
        check(lazyList.layoutInfo.visibleItemsInfo.isNotEmpty(), lazyMessage = {
            "can't restore focus to the grid because it is not on the screen"
        })
        check(defaultIndex > -1, lazyMessage = {
            "can't restore focus to negative value"
        })
        return super.restoreFocus(
            defaultPosition = defaultIndex,
            firstVisibleItem = lazyList.layoutInfo.visibleItemsInfo[0].index,
            isItemOnScreen = { requestedPosition: Int ->
                lazyList.layoutInfo.visibleItemsInfo.find { it.index == requestedPosition } != null
            })
    }


    override fun requestFocus(@IntRange(from = 0) index: Int): Boolean {
        check(lazyList.layoutInfo.totalItemsCount > index, lazyMessage = {
            "requested index can't be bigger than the total item count."
        })
        val visibleItems = lazyList.layoutInfo.visibleItemsInfo
        check(visibleItems.isNotEmpty(), lazyMessage = {
            "cant request focus to the list because its not on the screen"
        })
        return super.requestFocus(index, isItemOnScreen = { requestedPosition ->
            visibleItems.find { it.index == requestedPosition } != null
        })
    }

    override fun getFocusRequesterForIndexOrNull(@IntRange(from = 0) index: Int): FocusRequester? {
        check(lazyList.layoutInfo.totalItemsCount > index, lazyMessage = {
            "requested index can't be bigger than the total item count."
        })
        val visibleItems = lazyList.layoutInfo.visibleItemsInfo
        check(visibleItems.isNotEmpty(), lazyMessage = {
            "cant request focus to the list because its not on the screen"
        })
        return super.getFocusRequesterForIndexOrNull(index, isItemOnScreen = { requestedPosition ->
            visibleItems.find { it.index == requestedPosition } != null
        })
    }

    fun getFocusRequesterForIndexOrNull(key: String): FocusRequester? {
        val visibleItems = lazyList.layoutInfo.visibleItemsInfo
        check(visibleItems.isNotEmpty(), lazyMessage = {
            "cant request focus to the list because its not on the screen"
        })
        val requestedIndex = focusItems.find { it.key == key }?.index ?: return null
        return super.requestFocusByPositionFromFocusItems(
            requestedIndex,
            isItemOnScreen = { requestedPosition ->
                visibleItems.find { it.index == requestedPosition } != null
            })
    }


    override fun requestFocus(key: String): Boolean {
        check(key.isNotEmpty(), lazyMessage = {
            "key must not be empty"
        })
        val visibleItems = lazyList.layoutInfo.visibleItemsInfo
        check(visibleItems.isNotEmpty(), lazyMessage = {
            "cant request focus to the list because its not on the screen"
        })
        if (focusItems.isEmpty()) return false
        val indexOfRequestedItem = focusItems.indexOfFirst { it.key == key }
        check(lazyList.layoutInfo.totalItemsCount > indexOfRequestedItem, lazyMessage = {
            "requested index can't be bigger than the total item count."
        })
        return super.requestFocus(indexOfRequestedItem, isItemOnScreen = { requestedPosition ->
            lazyList.layoutInfo.visibleItemsInfo.find { it.index == requestedPosition } != null
        })
    }


    suspend fun scrollToAndRequestFocus(
        @IntRange(from = 0) scrollToPosition: Int,
        scrollOffset: Int,
        @IntRange(from = 0) itemToFocus: Int = scrollToPosition
    ): Boolean {
        check(
            lazyList.layoutInfo.totalItemsCount > scrollToPosition && lazyList.layoutInfo.totalItemsCount > itemToFocus,
            lazyMessage = {
                "the requested index is bigger than the total list size"
            }
        )
        lazyList.scrollToItem(scrollToPosition, scrollOffset)
        return requestFocus(itemToFocus)
    }

}


@Stable
class FocusItem(
    var focusRequester: FocusRequester,
    var index: Int,
    var key: String? = null,
)