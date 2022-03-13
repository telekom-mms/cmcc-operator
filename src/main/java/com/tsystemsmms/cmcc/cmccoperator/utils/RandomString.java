/*
 * Copyright (c) 2022. T-Systems Multimedia Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package com.tsystemsmms.cmcc.cmccoperator.utils;

import java.security.SecureRandom;
import java.util.Random;

/**
 * Securely generate a random string, suitable for use as a password.
 */
public class RandomString {
    private static final String repertoire = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private final Random random = new SecureRandom();
    private final char[] buffer;

    public RandomString(int length) {
        buffer = new char[length];
    }

    public String next() {
        for (int i = 0; i < buffer.length; i++) {
            buffer[i] = repertoire.charAt(random.nextInt(repertoire.length()));
        }
        return new String(buffer);
    }
}
