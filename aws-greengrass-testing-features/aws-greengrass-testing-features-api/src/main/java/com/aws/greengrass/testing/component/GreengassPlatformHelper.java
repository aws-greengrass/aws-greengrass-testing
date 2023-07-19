/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import static com.aws.greengrass.testing.platform.PlatformResolver.ARCHITECTURE_KEY;
import static com.aws.greengrass.testing.platform.PlatformResolver.OS_KEY;
import static com.aws.greengrass.testing.platform.PlatformResolver.WILDCARD;

public final class GreengassPlatformHelper {

    // this test exists so to allow future extension, a platform label should not start with special characters
    private static final Pattern SIMPLE_LABEL = Pattern.compile("^[a-zA-Z0-9]");

    private GreengassPlatformHelper() {
    }

    /**
     * find best match from a list of recipes.
     *
     * @param targetPlatform Platform attributes to test against (usually the actual platform of the device).
     * @param manifestList   A list of recipe manifests.
     * @return first matching manifest.
     */
    public static Optional<Map<String, Object>> findBestMatch(final Map<String, String> targetPlatform,
                                                                   final List<Map<String, Object>> manifestList) {
        //
        // Manifests are listed in order of preference, so the first match is the relevant match
        //
        return manifestList.stream().filter(m -> isRequirementSatisfied(targetPlatform, m)).findFirst();
    }

    /**
     * Test that the requirements section of a manifest is satisfied.
     * @param targetPlatform GreengassPlatform to test against (usually the actual platform of the device).
     * @param manifest Single manifest
     * @return
     */
    private static boolean isRequirementSatisfied(final Map<String, String> targetPlatform,
                                                    final Map<String, Object> manifest) {
        //
        // The "requirement" section of the Manifest contains a map of attribute:template
        // Note that it is important that an attribute is permitted to be in targetPlatform but not in requirement,
        // which will happen as we add more attributes to match against. Therefore all requirements must be
        // satisfied, but not all target attributes need to have a requirement template.
        //
        GreengassPlatform platformRequirement = getPlatform(manifest);
        if (platformRequirement == null || platformRequirement.isEmpty()) {
            return true; // no platform is considered a wild-card
        }
        return platformRequirement.entrySet().stream().allMatch(e ->
                isAttributeSatisfied(e.getKey(), targetPlatform.get(e.getKey()), e.getValue()));
    }

    /**
     * Test a single field expression is satisfied.
     *
     * @param name     Attribute name (some names may change match behavior)
     * @param label    GreengassPlatform label (as provided by target platform, null if not defined)
     * @param template Template (a string)
     * @return true if attribute requirement is satisfied
     */
    private static boolean isAttributeSatisfied(String name, String label, String template) {
        if (template == null || template.equals(WILDCARD)) {
            // treat null same as missing template entry.
            // treat both as same as wildcard
            return true;
        }
        if (label == null || label.length() == 0) {
            // in all other cases, label must not be a null / missing / blank
            // (each indicate no value)
            return false;
        }
        if (template.length() >= 2 && template.startsWith("/") && template.endsWith("/")) {
            // regular expression match, such as for alternatives
            return label.matches(template.substring(1, template.length() - 1));
        }
        if (OS_KEY.equals(name) && ("all".equals(template) || "any".equals(template))) {
            return true; // treat as wildcard
        }

        if (ARCHITECTURE_KEY.equals(name) && ("all".equals(template) || "any".equals(template))) {
            return true; // treat as wildcard
        }
        // Other special symbols may be implemented here, so we permit only simple labels for platform matching
        if (!SIMPLE_LABEL.matcher(template).lookingAt()) {
            // reject any special labels, to allow future extension
            // Review note, how to log?
            return false;
        }
        return template.equals(label);
    }

    private static GreengassPlatform getPlatform(final Map<String, Object> manifest) {
        String os = null;
        String architecture = null;

        Map<String, String> platform = (Map<String, String>) manifest.get("Platform");
        if (platform != null) {
            os = (String) platform.get(OS_KEY);
            architecture = (String) platform.get(ARCHITECTURE_KEY);
        }

        return new GreengassPlatform.GreengassPlatformBuilder()
            .os(GreengassPlatform.OS.getOS(os))
            .architecture(GreengassPlatform.Architecture.getArch(architecture))
            .build();
    }
}
