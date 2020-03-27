package com.nike.fleam
package ops

/** Copyright 2020-present, Nike, Inc.
 * All rights reserved.
 *
 * This source code is licensed under the MIT license found in
 * the LICENSE file in the root directory of this source tree.
 **/

trait AllOps extends Stream.ToStreamOps
  with EitherStream.ToEitherStreamOps
  with BiViaSourceOps
  with BiViaFlowOps
  with BroadcastMergeSourceOps
  with BroadcastMergeFlowOps
  with EitherFlattenIterableOps
  with FutureFunctionHelperOps
  with LiftsOps
  with TickingGroupedWithinSourceOps
  with TickingGroupedWithinFlowOps
  with TupleHelperSourceOps
  with TupleHelperFlowOps
  with ContainsCount.ToContainsCountOps
  with Keyed.ToKeyedOps
