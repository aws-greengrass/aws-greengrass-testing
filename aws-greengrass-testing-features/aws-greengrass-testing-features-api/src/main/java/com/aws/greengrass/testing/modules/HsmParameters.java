/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.modules;

import com.aws.greengrass.testing.api.Parameters;
import com.aws.greengrass.testing.api.model.Parameter;

import java.util.Arrays;
import java.util.List;

public class HsmParameters implements Parameters {

    public static final String HSM_CONFIGURED = "ggc.hsm.configured";
    public static final String PKCS_LIBRARY_PATH = "ggc.hsm.pkcs11ProviderPath";
    public static final String SLOT_ID = "ggc.hsm.slotId";
    public static final String SLOT_USER_PIN = "ggc.hsm.slotUserPin";
    public static final String SLOT_LABEL = "ggc.hsm.slotLabel";
    public static final String HSM_CERT_AND_KEY_LABEL = "ggc.hsm.certandkey.label";


    @Override
    public List<Parameter> available() {
        return Arrays.asList(
                Parameter.of(HSM_CONFIGURED, "Boolean value indicating whether device is configured with "
                        + "Hardware Security Module or not"),
                Parameter.of(PKCS_LIBRARY_PATH, "The pkcs library path on the host. To provide a path on the "
                        + "DUT itself, prefix the path with 'dut:'"),
                Parameter.of(SLOT_ID, "HSM slot Id"),
                Parameter.of(SLOT_USER_PIN, "HSM slot user pin"),
                Parameter.of(SLOT_LABEL, "HSM Slot label"),
                Parameter.of(HSM_CERT_AND_KEY_LABEL, "The label for the private key and certificate in hsm")
        );
    }
}
