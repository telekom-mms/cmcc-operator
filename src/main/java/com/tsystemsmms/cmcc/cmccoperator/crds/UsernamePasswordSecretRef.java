/*
 * Copyright (c) 2022. T-Systems Multimedia Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package com.tsystemsmms.cmcc.cmccoperator.crds;

import lombok.Data;

import static com.tsystemsmms.cmcc.cmccoperator.targetstate.TargetState.DATABASE_SECRET_PASSWORD_KEY;
import static com.tsystemsmms.cmcc.cmccoperator.targetstate.TargetState.DATABASE_SECRET_USERNAME_KEY;

/**
 * Allow obtaining a username and password from a secret
 */
@Data
public class UsernamePasswordSecretRef {
    String secret;
    String username = DATABASE_SECRET_USERNAME_KEY;
    String password = DATABASE_SECRET_PASSWORD_KEY;
}
