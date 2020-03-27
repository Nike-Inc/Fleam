package com.nike.fleam

/** Copyright 2020-present, Nike, Inc.
 * All rights reserved.
 *
 * This source code is licensed under the MIT license found in
 * the LICENSE file in the root directory of this source tree.
 **/

package object ops {
  object all extends AllOps
  object stream extends Stream.ToStreamOps
  object eitherStream extends EitherStream.ToEitherStreamOps

  object biVia extends BiViaSourceOps with BiViaFlowOps
  object biViaSource extends BiViaSourceOps
  object biViaFlow extends BiViaFlowOps

  object broadcastMerge extends BroadcastMergeSourceOps with BroadcastMergeFlowOps
  object broadcastMergeSource extends BroadcastMergeSourceOps
  object broadcastMergeFlow extends BroadcastMergeFlowOps

  object eitherFlattenIterable extends EitherFlattenIterableOps
  object futureFunctionHelpers extends FutureFunctionHelperOps
  object lifts extends LiftsOps

  object tickingGroupedWithin extends TickingGroupedWithinSourceOps with TickingGroupedWithinFlowOps
  object tickingGroupedWithinSource extends TickingGroupedWithinSourceOps
  object tickingGroupedWithinFlow extends TickingGroupedWithinFlowOps

  object tupleHelper extends TupleHelperSourceOps with TupleHelperFlowOps
  object tupleHelperSource extends TupleHelperSourceOps
  object tupleHelperHolderFlow extends TupleHelperFlowOps
}
