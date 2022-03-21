/*
 * Copyright (c) 2022. T-Systems Multimedia Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package com.tsystemsmms.cmcc.cmccoperator.targetstate;

import com.tsystemsmms.cmcc.cmccoperator.crds.ClientSecretRef;
import io.fabric8.kubernetes.api.model.Secret;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;
import java.util.Optional;

/**
 * Holds a secret reference and a secret. The optional secret is filled when either the operator is generating the
 * secret (and thus the Secret resource has to be built), or the operator needs to access one of the keys in the
 * secret directly.
 */
public class ClientSecret {
    @Getter
    @Setter
    private ClientSecretRef ref;
    private Secret secret;

    public ClientSecret(ClientSecretRef ref) {
        this.ref = ref;
        this.secret = null;
    }

    public ClientSecret(ClientSecretRef ref, Secret secret) {
        this.ref = ref;
        this.secret = secret;
    }

    public Optional<Secret> getSecret() {
        return Optional.ofNullable(secret);
    }

    public void setSecret(Secret secret) {
        this.secret = secret;
    }

    /**
     * Returns the entries of the secret.
     *
     * @return the base64-decoded entries of the secret
     */
    public Map<String, String> getStringData() {
        return getSecret()
                .orElseThrow(() -> new CustomResourceConfigError("Unable to find secret for clientSecretRef \"" + getRef().getSecretName() + "\""))
                .getStringData();
    }

}
