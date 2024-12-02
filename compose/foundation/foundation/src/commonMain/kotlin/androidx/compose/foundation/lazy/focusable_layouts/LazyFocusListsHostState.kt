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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.util.fastForEach

@Immutable
class LazyFocusListsHostState(
    val parentLazyList: LazyListState = LazyListState(),
    val restoredFocusedColumn: Int = -1,
) {
    private val tag = this::class.simpleName ?: "FocusModifierStateList"
    private val childrenLazyLists: SnapshotStateList<LazyFocusListState> = mutableStateListOf()

    companion object {
        val Saver: Saver<LazyFocusListsHostState, Any> = listSaver(
            save = {
                listOf(
                    it.parentLazyList.firstVisibleItemIndex,
                    it.parentLazyList.firstVisibleItemScrollOffset,
                    it.focusedColumn,
                )
            },
            restore = {

                LazyFocusListsHostState(
                    parentLazyList = LazyListState(it[0], it[1]),
                    restoredFocusedColumn = it[2],
                )
            }
        )
    }


    private var focusedColumn: Int = -1


    fun restoreFocus(): Boolean {
        parentLazyList.layoutInfo.visibleItemsInfo.find {
            it.index == restoredFocusedColumn || it.index == focusedColumn
        } ?: return false // it means the actual column is not in the viewport
        val listAdapter =
            childrenLazyLists.find { it.index == restoredFocusedColumn || it.index == focusedColumn }
                ?: return false
        return listAdapter.restoreFocus()
    }

    fun restoreFocus(defaultColumn: Int, defaultRow: Int): Boolean {
        val restoredColumn = parentLazyList.layoutInfo.visibleItemsInfo.find {
            it.index == restoredFocusedColumn || it.index == focusedColumn
        }
        if (restoredColumn != null) {
            val listAdapter =
                childrenLazyLists.find { it.index == restoredFocusedColumn || it.index == focusedColumn }
                    ?: return false
            return listAdapter.restoreFocus()
        } else {
            parentLazyList.layoutInfo.visibleItemsInfo.find {
                it.index == defaultColumn
            } ?: return false // it means the actual column is not in the viewport
            return childrenLazyLists.find { it.index == defaultColumn }?.restoreFocus(defaultRow)
                ?: false
        }
    }

    fun restoreFocus(key: String): Boolean {
        val restoredColumn = parentLazyList.layoutInfo.visibleItemsInfo.find {
            it.index == restoredFocusedColumn || it.index == focusedColumn
        }
        if (restoredColumn != null) {
            val listAdapter =
                childrenLazyLists.find { it.index == restoredFocusedColumn || it.index == focusedColumn }
                    ?: return false
            return listAdapter.restoreFocus()
        } else {
            return requestFocus(key)
        }
    }


    suspend fun scrollAndFocus(
        @IntRange(from = 0) columnIndexToScroll: Int,
        @IntRange(from = 0) row: Int,
        @IntRange(from = 0) columnOffset: Int = 0,
        checkForHorizontalScroll: Boolean = true,
    ): Boolean {
        check(parentLazyList.layoutInfo.totalItemsCount > columnIndexToScroll, lazyMessage = {
            "couldn't scroll and focus because the [columnIndexToScroll] is greater than list size."
        })
        check(parentLazyList.layoutInfo.visibleItemsInfo.isNotEmpty(), lazyMessage = {
            "list must be visible on the screen in order to manipulate it."
        })

        parentLazyList.scrollToItem(columnIndexToScroll, columnOffset)
        if (checkForHorizontalScroll) {
            val columnIndex = childrenLazyLists.indexOfFirst { it.index == columnIndexToScroll }
            if (columnIndex < 0) return false
            val selectedColumn = childrenLazyLists[columnIndex]
            if (selectedColumn.lazyList.layoutInfo.visibleItemsInfo.find { it.index == row } == null) {
                // request focus to the first visible item is not possible, after request focus the list is remeasure it self and move the focus to the first item on the left.
                // to solve this issue, we need to target the requested item to be the last visible item after scroll
//                childrenLazyLists[columnIndex].lazyList.scrollToItem(row)
                childrenLazyLists[columnIndex].lazyList.scrollToItem(
                    (row - (childrenLazyLists[columnIndex].lazyList.layoutInfo.visibleItemsInfo.size - 1)).coerceAtLeast(
                        0
                    )
                )
            }
            // request focus twice to force remeasure the list before focusing to the target
            requestFocus(columnIndexToScroll, row)
        }
        return requestFocus(columnIndexToScroll, row)
    }


    fun requestFocus(
        @IntRange(from = 0) column: Int,
        @IntRange(from = 0) row: Int
    ): Boolean {
        check(row > -1, lazyMessage = {
            "[row] must be greater than -1"
        })
        val columnIndex = validateColumnIndexAndReturnVisibleIndex(column) ?: return false
        if (parentLazyList.firstVisibleItemIndex != column) {
            childrenLazyLists[columnIndex].requestFocus(row)
        }
        return childrenLazyLists[columnIndex].requestFocus(row)
    }

    fun getFocusRequester(column: Int, row: Int): FocusRequester? {
        check(row > -1, lazyMessage = {
            "[row] must be greater than -1"
        })
        val columnIndex = validateColumnIndexAndReturnVisibleIndex(column) ?: return null
        return childrenLazyLists[columnIndex].getFocusRequesterForIndexOrNull(row)
    }

    fun getFocusRequester(key: String): FocusRequester? {
        check(key.isNotEmpty(), lazyMessage = {
            "key must not be empty"
        })
        childrenLazyLists.fastForEach {
            val fr = it.getFocusRequesterForIndexOrNull(key)
            if (fr != null) {
                return fr
            }
        }
        return null
    }

    fun requestFocus(
        @IntRange(from = 0) column: Int,
        key: String
    ): Boolean {
        check(key.isNotEmpty(), lazyMessage = {
            "key must not be empty"
        })
        val columnIndex = validateColumnIndexAndReturnVisibleIndex(column) ?: return false
        return childrenLazyLists[columnIndex].requestFocus(key)
    }

    fun requestFocus(key: String): Boolean {
        check(key.isNotEmpty(), lazyMessage = {
            "key must not be empty"
        })
        return childrenLazyLists.find {
            it.getFocusRequesterForIndexOrNull(key) != null
        }?.restoreFocus() ?: return false
    }


    private fun validateColumnIndexAndReturnVisibleIndex(
        @IntRange(from = 0) column: Int
    ): Int? {
        check(column > -1, lazyMessage = {
            "column must be greater than -1"
        })
        val visibleItems = parentLazyList.layoutInfo.visibleItemsInfo
        check(visibleItems.isNotEmpty(), lazyMessage = {
            "list must be visible on the screen"
        })
        check(column < parentLazyList.layoutInfo.totalItemsCount, lazyMessage = {
            "requested column is greater than the list size"
        })
        visibleItems.find { it.index == column } ?: return null
        val columnIndex = childrenLazyLists.indexOfFirst { it.index == column }
        if (columnIndex == -1) return null
        return columnIndex
    }


    fun registerChildList(item: LazyFocusListState) {
        childrenLazyLists.add(item)

    }

    fun unRegisterChildList(item: LazyFocusListState) {
        childrenLazyLists.remove(item)
    }

    fun updateFocusedColumn(
        @IntRange(from = 0) index: Int
    ) {
        check(index > -1, lazyMessage = {
            "index must be greater than -1"
        })
        focusedColumn = index
    }

}
