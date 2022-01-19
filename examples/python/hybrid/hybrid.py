# Copyright 2021 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS-IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
# [START hybrid-encryption-example]
"""A command-line utility for encrypting a file using hybrid encryption.

It loads cleartext keys from disk - this is not recommended!
"""

from __future__ import absolute_import
from __future__ import division
from __future__ import print_function

# Special imports
from absl import app
from absl import flags
from absl import logging
import tink
from tink import cleartext_keyset_handle
from tink import hybrid


FLAGS = flags.FLAGS

flags.DEFINE_enum('mode', None, ['encrypt', 'decrypt'],
                  'The operation to perform.')
flags.DEFINE_string('keyset_path', None,
                    'Path to the keyset used for encryption.')
flags.DEFINE_string('input_path', None, 'Path to the input file.')
flags.DEFINE_string('output_path', None, 'Path to the output file.')
flags.DEFINE_string('context_info', None,
                    'Context info used for encryption.')


def main(argv):
  del argv  # Unused

  context_info = b'' if not FLAGS.context_info else bytes(
      FLAGS.context_info, 'utf-8')

  # Initialise Tink
  try:
    hybrid.register()
  except tink.TinkError as e:
    logging.exception('Error initialising Tink: %s', e)
    return 1

  # Read the keyset into a keyset_handle
  with open(FLAGS.keyset_path, 'rt') as keyset_file:
    try:
      text = keyset_file.read()
      keyset_handle = cleartext_keyset_handle.read(tink.JsonKeysetReader(text))
    except tink.TinkError as e:
      logging.exception('Error reading key: %s', e)
      return 1

  with open(FLAGS.input_path, 'rb') as input_file:
    data = input_file.read()

  if FLAGS.mode == 'encrypt':
    # Get the primitive
    try:
      primitive = keyset_handle.primitive(hybrid.HybridEncrypt)
    except tink.TinkError as e:
      logging.exception(
          'Error creating hybrid encrypt primitive from keyset: %s', e)
      return 1
    # Encrypt data
    with open(FLAGS.output_path, 'wb') as output_file:
      ciphertext = primitive.encrypt(data, context_info)
      output_file.write(ciphertext)

  if FLAGS.mode == 'decrypt':
    # Get the primitive
    try:
      primitive = keyset_handle.primitive(hybrid.HybridDecrypt)
    except tink.TinkError as e:
      logging.exception(
          'Error creating hybrid encrypt primitive from keyset: %s', e)
      return 1
    # Decrypt data
    with open(FLAGS.output_path, 'wb') as output_file:
      plaintext = primitive.decrypt(data, context_info)
      output_file.write(plaintext)


if __name__ == '__main__':
  flags.mark_flags_as_required(
      ['mode', 'keyset_path', 'input_path', 'output_path'])
  app.run(main)
# [END hybrid-encryption-example]
