package com.nike.fleam.sqs
package logging

import cats.Show
import cats.implicits._
import software.amazon.awssdk.services.sqs.model.Message

/** Copyright 2020-present, Nike, Inc.
 * All rights reserved.
 *
 * This source code is licensed under the MIT license found in
 * the LICENSE file in the root directory of this source tree.
 **/

class MessageCountErrorLogging(implicit showMessage: Show[Message] = logging.noShow[Message]) {
  val messageCountErrorShow = Show.show[MessageCountError] {
    case numberFormatError: NumberFormatError =>
      join(
        show"Unable to parse retry value as integer.",
        show"Tried parsing '${numberFormatError.attributeValue}'",
        numberFormatError.message.show
      )
    case nullValueError: NullValueError =>
      join(
        show"Retry value not a string value. ${nullValueError.attributeValue.toString}",
        nullValueError.message.show
      )
    }
}
