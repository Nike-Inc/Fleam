package com.nike.fleam.sqs
package ops

/** Copyright 2020-present, Nike, Inc.
 * All rights reserved.
 *
 * This source code is licensed under the MIT license found in
 * the LICENSE file in the root directory of this source tree.
 **/

trait AllOps extends TimestampedMessageOps
  with ContainsMessage.ToContainsMessageOps
  with ContainsRetrievedMessage.ToContainsRetrievedMessageOps
  with RetrievedTime.ToRetrievedTimeOps
  with ToMessage.ToToMessageOps
  with ToMessageAttributes.ToToMessageAttributesOps
