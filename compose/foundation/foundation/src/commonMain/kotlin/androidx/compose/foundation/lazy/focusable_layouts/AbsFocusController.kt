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

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.focus.FocusRequester

/**
 * [AbsFocusController] - is the abstraction of [LazyFocusGridState] and [LazyFocusListState] and should be given (reference) from the creation
 * * [_focusItems] - a mutable state list that holds [FocusItem]s. this list holds only the visible items.
 * there is no guarantee that the first index of the list will be the first index on the screen.
 * to get the first item on the screen you should get the index from the LazyListState from the inherited class.
 *
 * *[focusedChildIndex] - is a mutable Int which keep follows the index of the last focused index by [updateFocusedItem] which is called on focus change - **FocusState.isFocused**.
 *
 * * [wasRestoredOnEnter] - is a mutable boolean that in use for focus restoration via **Modifier.focusRestorer**. the result of this boolean will effect the restored item in case of native restoration.

 * */

@Immutable
abstract class AbsFocusController: ModifierParentalFocusableNode {
    protected val tag = this::class.simpleName ?: "AbsFocusController"
    private val _focusItems = mutableStateListOf<FocusItem>()
    protected val focusItems get() = _focusItems.toList()
    open var focusedChildIndex: Int = -1
    open var wasRestoredOnEnter = false

    fun registerFocusItem(item: FocusItem) {
        _focusItems.add(item)
    }

    fun unRegisterFocusItem(item: FocusItem) {
        _focusItems.remove(item)
    }


    fun updateFocusedItem(item: FocusItem) {
        focusedChildIndex = item.index
    }

    /**
     * this function will restore the last known position for the item in the list; if there is no saved index, the default is to the first visible item
     *
     * [firstVisibleItem] - the index of the first visible item in the list; this data can be supplied by the lazy list items using **layoutInfo.visibleItemsInfo\[0].index**
     *
     * do not use **firstVisibleItemIndex** from the list state, as it could be leaked with index 0 and it should be safer to call it directly from the visible items list
     *
     * */

    protected fun restoreFocus(
        firstVisibleItem: Int,
        isItemOnScreen: (requestedPosition: Int) -> Boolean
    ): Boolean {
        if (focusedChildIndex < 0) {
            val defaultFocusItem = focusItems.find { it.index == firstVisibleItem } ?: return false
            wasRestoredOnEnter = true
            defaultFocusItem.focusRequester.requestFocus()
            return true
        }
        if (focusItems.isEmpty()) {
            return false
        }

        focusItems.takeIf { isItemOnScreen(focusedChildIndex) }
            ?.find { it.index == focusedChildIndex }
            ?.focusRequester?.run {
                wasRestoredOnEnter = true
                requestFocus()
                return true
            } ?: run {
            val defaultFocusItem = focusItems.find { it.index == firstVisibleItem } ?: return false
            wasRestoredOnEnter = true
            defaultFocusItem.focusRequester.requestFocus()
            return false
        }
    }

    protected fun restoreFocus(
        defaultPosition: Int,
        firstVisibleItem: Int,
        isItemOnScreen: (requestedPosition: Int) -> Boolean
    ): Boolean {
        focusItems.apply {
            takeIf { focusedChildIndex >= 0 }
                ?.find { it.index == focusedChildIndex }
                ?.takeIf { isItemOnScreen(focusedChildIndex) }
                ?.focusRequester?.requestFocus()
                ?: takeIf { defaultPosition > -1 && isItemOnScreen(defaultPosition)}
                    ?.find { it.index == defaultPosition }
                    ?.focusRequester?.requestFocus()
                ?: find { it.index == firstVisibleItem }
                    ?.takeIf { isItemOnScreen(firstVisibleItem) }
                    ?.focusRequester?.requestFocus() ?: return false
            return true
        }
    }

    /***/
    protected fun requestFocus(
        index: Int,
        isItemOnScreen: (requestedPosition: Int) -> Boolean
    ): Boolean {
        if (focusItems.isEmpty()) return false
        val indexOfRequestedItem = focusItems.indexOfFirst { it.index == index }
        if (indexOfRequestedItem == -1) return false
        val positionOfRequestedItemOnScreen = focusItems[indexOfRequestedItem].index
        if (!isItemOnScreen(positionOfRequestedItemOnScreen)) {
            return false
        }
        focusItems[indexOfRequestedItem].focusRequester.requestFocus()
        return true
    }

    protected fun getFocusRequesterForIndexOrNull(
        index: Int,
        isItemOnScreen: (requestedPosition: Int) -> Boolean
    ): FocusRequester? {
        if (focusItems.isEmpty()) return null
        val indexOfRequestedItem = focusItems.indexOfFirst { it.index == index }
        if (indexOfRequestedItem == -1) return null
        return requestFocusByPositionFromFocusItems(indexOfRequestedItem, isItemOnScreen)
    }

    protected fun requestFocusByPositionFromFocusItems(
        indexOfRequestedItem: Int,
        isItemOnScreen: (requestedPosition: Int) -> Boolean
    ): FocusRequester? {
        val positionOfRequestedItemOnScreen = focusItems[indexOfRequestedItem].index
        return if (isItemOnScreen(positionOfRequestedItemOnScreen)) {
            focusItems[indexOfRequestedItem].focusRequester
        } else {
            null
        }
    }
}