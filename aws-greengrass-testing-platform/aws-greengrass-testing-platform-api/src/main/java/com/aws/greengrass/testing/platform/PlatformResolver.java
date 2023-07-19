/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.platform;

import com.aws.greengrass.testing.api.device.Device;
import com.aws.greengrass.testing.api.device.model.CommandInput;
import com.aws.greengrass.testing.api.model.PillboxContext;
import com.aws.greengrass.testing.platform.exception.PlatformResolutionException;
import com.aws.greengrass.testing.platform.linux.LinuxPlatform;
import com.aws.greengrass.testing.platform.macos.MacosPlatform;
import com.aws.greengrass.testing.platform.windows.WindowsPlatform;
import com.google.common.annotations.VisibleForTesting;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;


public class PlatformResolver {
    public static final String ALL_KEYWORD = "all";
    private static final String UNKNOWN_KEYWORD = "unknown";
    public static final String WILDCARD = "*";

    public static final String OS_KEY = "os";
    public static final String ARCHITECTURE_KEY = "architecture";
    public static final String ARCHITECTURE_DETAIL_KEY = "architecture.detail";


    // Note that this is not an exhaustive list of OSes, but happens to be a set of platforms detected.
    private static final String OS_WINDOWS = "windows";
    private static final String OS_DARWIN = "darwin";
    private static final String OS_LINUX = "linux";


    // Note that this is not an exhaustive list of Architectures, but happens to be a set of platforms detected.
    private static final String ARCH_AMD64 = "amd64";
    private static final String ARCH_X86 = "x86";
    private static final String ARCH_ARM = "arm";
    private static final String ARCH_AARCH64 = "aarch64";

    private static final Set<String> SUPPORTED_PLATFORMS = Collections.unmodifiableSet(Stream.of(
            "all", "any", "unix", "posix", OS_LINUX, "debian", OS_WINDOWS, "fedora", "ubuntu", "macos",
            "raspbian", "qnx", "cygwin", "freebsd", "solaris", "sunos").collect(Collectors.toSet()));

    private static final AtomicReference<Map<String, String>> DETECTED_PLATFORM =
            new AtomicReference<>();

    private final Device device;
    private final PillboxContext pillboxContext;

    public PlatformResolver(final Device device, final PillboxContext pillboxContext) {
        this.device = device;
        this.pillboxContext = pillboxContext;
    }

    /**
     * Resolve to a concrete {@link Platform} from the underlying {@link Device} object.
     *
     * @return
     */
    public Platform resolve() {
        final Map<String, Integer> ranks = createRanks();
        if (ranks.containsKey(OS_LINUX)) {
            return new LinuxPlatform(device, pillboxContext);
        } else if (ranks.containsKey("macos")) {
            return new MacosPlatform(device, pillboxContext);
        } else if (ranks.containsKey(OS_WINDOWS)) {
            return new WindowsPlatform(device, pillboxContext);
        }
        throw new PlatformResolutionException("Could not find a platform support for device: " + device.platform());
    }

    @VisibleForTesting
    Map<String, Integer> createRanks() {
        Map<String, Integer> ranks = new HashMap<>();
        // figure out what OS we're running and add applicable tags
        // The more specific a tag is, the higher its rank should be
        // TODO: use better way to determine if a field is platform specific. Eg: using 'platform$' prefix.
        ranks.put("all", 0);
        ranks.put("any", 0);

        if (device.platform().isWindows()) {
            ranks.put(OS_WINDOWS, 5);
        } else {
            if (device.exists("/bin/bash") || device.exists("/usr/bin/bash")) {
                ranks.put("unix", 3);
                ranks.put("posix", 3);
            }
            if (device.exists("/proc")) {
                ranks.put(OS_LINUX, 10);
            }
            if (device.exists("/usr/bin/apt-get")) {
                ranks.put("debian", 11);
            }
            if (device.exists("/usr/bin/yum")) {
                ranks.put("fedora", 11);
            }
            String sysver = device.executeToString(CommandInput.builder()
                    .line("sh").addArgs("-c", "uname -a")
                    .build());
            if (sysver.contains("ubuntu")) {
                ranks.put("ubuntu", 20);
            }
            if (sysver.contains(OS_DARWIN) || sysver.contains("Darwin")) {
                ranks.put("macos", 20);
            }
            if (sysver.contains("raspbian")) {
                ranks.put("raspbian", 22);
            }
            if (sysver.contains("qnx")) {
                ranks.put("qnx", 22);
            }
            if (sysver.contains("cygwin")) {
                ranks.put("cygwin", 22);
            }
            if (sysver.contains("freebsd")) {
                ranks.put("freebsd", 22);
            }
            if (sysver.contains("solaris") || sysver.contains("sunos")) {
                ranks.put("solaris", 22);
            }
        }
        return ranks;
    }

    /**
     * Get current Greengrass platform.
     * Detect current Greengrass platform.
     *
     * @return Greengrass Platform key-value map
     */
    public Map<String, String> getCurrentPlatform() {
        return getGreengrassPlatform();
    }

    private synchronized Map<String, String> getGreengrassPlatform() {
        Map<String, String> detected = DETECTED_PLATFORM.get();
        if (detected == null) {
            detected = initializePlatform();
            DETECTED_PLATFORM.set(detected);
        }
        return detected;
    }

    private Map<String, String> initializePlatform() {
        Map<String, String> platform = new HashMap<>();
        platform.put("os", getOSInfo());
        platform.put("architecture", getArchInfo());
        platform.put("architecture.detail", getArchDetailInfo());

        return platform;
    }

    private String getOSInfo() {
        if (device.platform().isWindows()) {
            return OS_WINDOWS;
        }
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("mac os")) {
            return OS_DARWIN;
        }
        if (Files.exists(Paths.get("/proc"))) {
            return OS_LINUX;
        }
        return UNKNOWN_KEYWORD;
    }

    private static String getArchInfo() {
        String arch = System.getProperty("os.arch").toLowerCase();
        if ("x86_64".equals(arch) || "amd64".equals(arch)) {
            return ARCH_AMD64; // x86_64 & amd64 are same
        }
        if ("i386".equals(arch) || "x86".equals(arch)) {
            return ARCH_X86;
        }
        if (arch.contains("arm")) {
            return ARCH_ARM;
        }
        if ("aarch64".equals(arch)) {
            return ARCH_AARCH64;
        }
        return UNKNOWN_KEYWORD;
    }

    private String getArchDetailInfo() {
        if (device.platform().isWindows()) {
            return null;
        }

        String arch = getArchInfo();
        // Since we only can detect the architecture details for arm, only run uname -m when we are running
        // on arm.
        if (ARCH_ARM.equals(arch) || ARCH_AARCH64.equals(arch)) {
            CommandInput command = CommandInput.builder().line("sh").addArgs("-c", "uname -m").build();
            String archDetail = resolve().commands().executeToString(command).toLowerCase();
            // TODO: "uname -m" is not sufficient to capture arch details on all platforms.
            // Currently only return if detected arm, as required by lambda launcher.
            if ("armv6l".equals(archDetail) || "armv7l".equals(archDetail) || "armv8l".equals(archDetail)) {
                return archDetail;
            }
        }
        return null;
    }
}
