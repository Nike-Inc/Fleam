package com.nike.fleam.sqs
package instances

import com.amazonaws.services.sqs.model.MessageAttributeValue

/** Copyright 2020-present, Nike, Inc.
 * All rights reserved.
 *
 * This source code is licensed under the MIT license found in
 * the LICENSE file in the root directory of this source tree.
 **/

trait ToMessageAttributesInstances {
  implicit val identityToMessageAttributes = ToMessageAttributes.lift[Map[String, MessageAttributeValue]](identity)

  implicit val tupleStringStringToMessageAttributes = ToMessageAttributes.lift[(String, String)] { case (key, value) =>
    Map(key -> new MessageAttributeValue().withDataType("String").withStringValue(value))
  }

  implicit val tupleStringIntToMessageAttributes = ToMessageAttributes.lift[(String, Int)] { case (key, value) =>
    Map(key -> new MessageAttributeValue().withDataType("Number").withStringValue(value.toString))
  }

  implicit val tupleStringLongToMessageAttributes = ToMessageAttributes.lift[(String, Long)] { case (key, value) =>
    Map(key -> new MessageAttributeValue().withDataType("Number").withStringValue(value.toString))
  }

  implicit val tupleStringDoubleToMessageAttributes = ToMessageAttributes.lift[(String, Double)] { case (key, value) =>
    Map(key -> new MessageAttributeValue().withDataType("Number").withStringValue(value.toString))
  }

  implicit def tupleMapStringTToMessageAttributes[T](implicit single: ToMessageAttributes[(String, T)]) =
    ToMessageAttributes.lift[Map[String, T]] { case mapping =>
      mapping.map(single.toMessageAttributes).reduceLeft(_ ++ _)
    }
}

object ToMessageAttributesInstances extends ToMessageAttributesInstances
