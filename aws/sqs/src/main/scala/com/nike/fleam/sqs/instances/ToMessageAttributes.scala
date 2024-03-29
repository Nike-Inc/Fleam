package com.nike.fleam.sqs
package instances

import software.amazon.awssdk.services.sqs.model.MessageAttributeValue

/** Copyright 2020-present, Nike, Inc.
 * All rights reserved.
 *
 * This source code is licensed under the MIT license found in
 * the LICENSE file in the root directory of this source tree.
 **/

trait ToMessageAttributesInstances {
  implicit val identityToMessageAttributes: ToMessageAttributes[Map[String, MessageAttributeValue]] =
    ToMessageAttributes.lift[Map[String, MessageAttributeValue]](identity)

  implicit val tupleStringStringToMessageAttributes: ToMessageAttributes[(String, String)] = ToMessageAttributes.lift[(String, String)] { case (key, value) =>
    Map(key -> MessageAttributeValue.builder().dataType("String").stringValue(value).build())
  }

  implicit val tupleStringIntToMessageAttributes: ToMessageAttributes[(String, Int)] = ToMessageAttributes.lift[(String, Int)] { case (key, value) =>
    Map(key -> MessageAttributeValue.builder().dataType("Number").stringValue(value.toString).build())
  }

  implicit val tupleStringLongToMessageAttributes: ToMessageAttributes[(String, Long)] = ToMessageAttributes.lift[(String, Long)] { case (key, value) =>
    Map(key -> MessageAttributeValue.builder().dataType("Number").stringValue(value.toString).build())
  }

  implicit val tupleStringDoubleToMessageAttributes: ToMessageAttributes[(String, Double)] = ToMessageAttributes.lift[(String, Double)] { case (key, value) =>
    Map(key -> MessageAttributeValue.builder().dataType("Number").stringValue(value.toString).build())
  }

  implicit def tupleMapStringTToMessageAttributes[T](implicit single: ToMessageAttributes[(String, T)]): ToMessageAttributes[Map[String, T]] =
    ToMessageAttributes.lift[Map[String, T]] { case mapping =>
      mapping.map(single.toMessageAttributes).reduceLeft(_ ++ _)
    }
}

object ToMessageAttributesInstances extends ToMessageAttributesInstances
