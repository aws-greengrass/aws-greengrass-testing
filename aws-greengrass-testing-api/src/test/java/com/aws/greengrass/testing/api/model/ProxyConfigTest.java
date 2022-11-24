/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.api.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ProxyConfigTest {
    @Test
    void GIVEN_validUrl_WHEN_parsingUrlLtoProxyConfig_THEN_returnValidProxyConfig() {
        ProxyConfig proxyConfig = ProxyConfig.fromURL("https://myuser:mypass@test.com:1");
        assertEquals("https://myuser:mypass@test.com:1", proxyConfig.proxyUrl());
        assertEquals("myuser", proxyConfig.username());
        assertEquals("mypass", proxyConfig.password());
        assertEquals(1, proxyConfig.port());
        assertEquals("test.com", proxyConfig.host());
        assertEquals("https", proxyConfig.scheme());
    }
}
