/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.component;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import static com.aws.greengrass.testing.platform.PlatformResolver.ALL_KEYWORD;
import static com.aws.greengrass.testing.platform.PlatformResolver.ARCHITECTURE_KEY;
import static com.aws.greengrass.testing.platform.PlatformResolver.OS_KEY;
import static com.aws.greengrass.testing.platform.PlatformResolver.WILDCARD;

/**
 * <p>Class representing a Greengass platform map.
 * A platform map is a set of key/value pairs for matching against arbitrary keys.
 * Some well defined keys do exist, but is irrelevant for Manifest data model.</p>
 *
 * <p>The value is a match expression, as follows:
 *     <ul>
 *         <li>name=stringValue - where stringValue beings with letter or digit - perform an exact match.</li>
 *         <li>name=/regex/ - match string against regular expression string.</li>
 *         <li>name="*" - match string against anything, including missing value.</li>
 *     </ul>
 */
public class GreengassPlatform extends HashMap<String,String> {
    // GreengassPlatform with no key/value pairs
    private static final GreengassPlatform EMPTY = new GreengassPlatform();

    /**
     * Retrieve specified field. Use wildcard if field does not exist or empty string.
     * @param name Name of field
     * @return Field, substituting wildcard as needed.
     */
    public String getFieldOrWild(String name) {
        Object o = get(name);
        if (o == null || ((String)o).length() == 0) {
            return WILDCARD;
        } else {
            return (String)o;
        }
    }

    private <T extends Enum<T>> T getEnum(String name, Function<String, T> transform) {
        return transform.apply(getFieldOrWild(name));
    }

    public OS getOs() {
        return getEnum(OS_KEY, OS::getOS);
    }

    public Architecture getArchitecture() {
        return getEnum(ARCHITECTURE_KEY, Architecture::getArch);
    }

    /**
     * Retrieve OS, or Wildcard if OS not specified.
     *
     * @return OS as a string with expected default.
     */
    public String getOsField() {
        return getFieldOrWild(OS_KEY);
    }

    /**
     * Retrieve Architecture, or Wildcard if Architecture not specified.
     *
     * @return Architecture as a string with expected default.
     */
    public String getArchitectureField() {
        return getFieldOrWild(ARCHITECTURE_KEY);
    }

    /**
     * Backward compatibility only for transition: Set of OSes.
     */
    public enum OS {
        ALL(ALL_KEYWORD),
        WINDOWS("windows"),
        LINUX("linux"),
        DARWIN("darwin"),
        MACOS("macos"),
        UNKNOWN("unknown");

        private final String name;

        OS(String name) {
            this.name = name;
        }

        /**
         * Backward compatibility only for transition: Convert string to enum value.
         * @param value String value to convert
         * @return enum value
         */
        public static OS getOS(String value) {
            // "any" and "all" keyword are both accepted in recipe.
            if (value == null || "any".equalsIgnoreCase(value) || "all".equalsIgnoreCase(value) || "*".equals(value)) {
                return OS.ALL;
            }

            for (OS os : values()) {
                if (os.getName().equals(value)) {
                    return os;
                }
            }

            // return UNKNOWN instead of throw exception. This is to keep backwards compatibility when
            // cloud recipe has more supported platform than local.
            return OS.UNKNOWN;
        }

        public String getName() {
            return name;
        }
    }

    /**
     * Backward compatibility only for transition: Set of Architectures.
     */
    //@Getter
    //@AllArgsConstructor
    //@Deprecated
    public enum Architecture {
        ALL(ALL_KEYWORD),
        AMD64("amd64"),
        ARM("arm"),
        AARCH64("aarch64"),
        X86("x86"),
        UNKNOWN("unknown");

        private final String name;


        Architecture(String name) {
            this.name = name;
        }

        /**
         * Backward compatibility only for transition: Convert string to enum value.
         * @param value String value to convert
         * @return enum value
         */
        public static Architecture getArch(String value) {
            if (value == null || "any".equalsIgnoreCase(value) || "all".equalsIgnoreCase(value) || "*".equals(value)) {
                // "any" and "all" keyword are both accepted in recipe.
                return Architecture.ALL;
            }

            for (Architecture arch : values()) {
                if (arch.getName().equals(value)) {
                    return arch;
                }
            }
            return Architecture.UNKNOWN;
        }

        public String getName() {
            return name;
        }
    }

    /**
     * This is to help migration to new GreengassPlatform class.
     */
    //@Deprecated
    public static final class GreengassPlatformBuilder {
        private final Map<String, String> platform = new HashMap<String,String>();

        /**
         * Sets OS.
         *
         * @param value the value of OS.
         */
        public GreengassPlatformBuilder os(OS value) {
            if (value == OS.ALL) {
                return add(OS_KEY, "*");
            } else {
                return add(OS_KEY, value.name);
            }
        }

        /**
         * Sets arhitecture.
         *
         * @param value the value of arhitecture
         */
        public GreengassPlatformBuilder architecture(Architecture value) {
            if (value == Architecture.ALL) {
                return add(ARCHITECTURE_KEY, "*");
            } else {
                return add(ARCHITECTURE_KEY, value.name);
            }
        }

        /**
         * Sets additional property.
         *
         * @param name the name of a property
         * @param value the value of a property
         */
        public GreengassPlatformBuilder add(String name, String value) {
            if (value != null) {
                platform.put(name, value);
            }
            return this;
        }

        /**
         * Produce platform instance.
         */
        public GreengassPlatform build() {
            GreengassPlatform p = new GreengassPlatform();
            p.putAll(this.platform);
            return p;
        }
    }

    public static GreengassPlatformBuilder builder() {
        return new GreengassPlatformBuilder();
    }
}
