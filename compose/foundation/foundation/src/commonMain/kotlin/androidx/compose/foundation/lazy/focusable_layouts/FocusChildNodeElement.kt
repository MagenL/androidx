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

import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusEventModifierNode
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.node.ModifierNodeElement


fun Modifier.focusChild(
    parentState: LazyFocusGridState,
    focusItem: FocusItem
): Modifier = this then (FocusChildNodeElement(
    parentState,
    focusItem
)).then(Modifier.focusRequester(focusItem.focusRequester))

fun Modifier.focusChild(
    parentState: LazyFocusListState,
    focusItem: FocusItem
): Modifier = this then (FocusChildNodeElement(
    parentState,
    focusItem
)).then(Modifier.focusRequester(focusItem.focusRequester))

/**
 *  [FocusChildNodeElement] - this object is in a re-use by compose runtime.
 *  on creation, the data should be transferred as is to the [FocusChildNode], on updating, the index and the key should be override with the actual needed data
 *  * [AbsFocusController] - is the abstraction of [LazyFocusGridState] and [LazyFocusListState] and should be given (reference) from the creation
 *
 *  * [focusItem] - is the specific item on screen that should be created when creating a focused component*/

internal data class FocusChildNodeElement(
    val focusModifierState: AbsFocusController,
    val focusItem: FocusItem
) : ModifierNodeElement<FocusChildNode>() {
    override fun create(): FocusChildNode {
        return FocusChildNode(focusModifierState, focusItem)
    }

    override fun update(node: FocusChildNode) {
        node.focusItem.index = focusItem.index
        node.focusItem.key = focusItem.key
        node.focusItem.focusRequester = focusItem.focusRequester
    }


}

/**
 * * this node will be notified on each focus event, encase **isFocused** is true, it will update it's parent
  */

internal data class FocusChildNode(
    val focusModifierState: AbsFocusController,
    val focusItem: FocusItem
) : Modifier.Node(), FocusEventModifierNode {

    override fun onDetach() {
        focusModifierState.unRegisterFocusItem(focusItem)
    }


    override fun onAttach() {
        focusModifierState.registerFocusItem(focusItem)
    }


    override fun onFocusEvent(focusState: FocusState) {
        if (focusState.isFocused) {
            focusModifierState.updateFocusedItem(focusItem)
        }
    }
}