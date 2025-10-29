/*
 * Copyright (c) 2024. T-Systems Multimedia Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package com.tsystemsmms.cmcc.cmccoperator.components.job;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.Data;

@Data
public class MgmtCronJobConfig {
  @JsonPropertyDescription("k8s cron expression determining when the job will be run. Default every day at midnight")
  String cron = "0 0 0 * *";

  @JsonPropertyDescription("Time zone for the time specification, default local timezone of the cluster")
  String timezone = "";

  @JsonPropertyDescription("Override for 'active deadline' in seconds, default: 30 min.")
  long activeDeadlineSeconds = 30 * 60L;
}
