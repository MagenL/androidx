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

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyListPrefetchStrategy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusEventModifierNode
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.node.ModifierNodeElement


@Composable
fun rememberLazyFocusListsHostState(
    firstVisibleItemIndex: Int = 0,
    firstVisibleItemScrollOffset: Int = 0,
): LazyFocusListsHostState =
    rememberSaveable(saver = LazyFocusListsHostState.Saver) {
        LazyFocusListsHostState(
            parentLazyList = LazyListState(
                firstVisibleItemIndex = firstVisibleItemIndex,
                firstVisibleItemScrollOffset = firstVisibleItemScrollOffset,
            )
        )
    }


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun rememberLazyFocusListsHostState(
    firstVisibleItemIndex: Int = 0,
    firstVisibleItemScrollOffset: Int = 0,
    prefetchStrategy: LazyListPrefetchStrategy
): LazyFocusListsHostState =
    rememberSaveable(saver = LazyFocusListsHostState.Saver) {
        LazyFocusListsHostState(
            parentLazyList = LazyListState(
                firstVisibleItemIndex = firstVisibleItemIndex,
                firstVisibleItemScrollOffset = firstVisibleItemScrollOffset,
                prefetchStrategy = prefetchStrategy
            )
        )
    }



fun Modifier.focusChildList(
    parentState: LazyFocusListsHostState,
    childState: LazyFocusListState,
    firstItemFR: FocusRequester
): Modifier =
    this then (FocusListChildNodeElement(parentState, childState))
        .focusRestorer(
            if (childState.wasRestoredOnEnter) {
                childState.wasRestoredOnEnter = false
                childState.isVisited = true
                FocusRequester.Default
            } else if (!childState.isVisited) {
                childState.isVisited = true
                firstItemFR
            } else {
                childState.isVisited = true
                FocusRequester.Default
            }
        )


fun Modifier.focusChildList(
    parentState: LazyFocusListsHostState,
    childState: LazyFocusListState,
): Modifier =
    this then (FocusListChildNodeElement(
        parentState,
        childState
    )).focusRestorer ( FocusRequester.Default )


private data class FocusListChildNodeElement(
    val parentState: LazyFocusListsHostState,
    val childState: LazyFocusListState,
) : ModifierNodeElement<FocusChildListNode>() {
    override fun create(): FocusChildListNode {
        return FocusChildListNode(parentState, childState)
    }

    override fun update(node: FocusChildListNode) {
        node.childState.index = childState.index
    }


}


private data class FocusChildListNode(
    val parentState: LazyFocusListsHostState,
    val childState: LazyFocusListState,
) : Modifier.Node(), FocusEventModifierNode {

    override fun onDetach() {
        detachFocusChildNode()
    }


    override fun onAttach() {
        attachFocusChildNode()
    }

    private fun attachFocusChildNode() {
        parentState.registerChildList(childState)
    }

    private fun detachFocusChildNode() {
        parentState.unRegisterChildList(childState)
    }

    override fun onFocusEvent(focusState: FocusState) {
        if (focusState.hasFocus) {
            parentState.updateFocusedColumn(childState.index)
        }
    }
}