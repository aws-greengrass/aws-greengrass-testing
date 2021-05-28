package com.aws.greengrass.testing.platform;

import com.aws.greengrass.testing.api.device.Device;
import com.aws.greengrass.testing.platform.exception.PlatformResolutionException;
import com.aws.greengrass.testing.api.device.model.CommandInput;
import com.aws.greengrass.testing.platform.linux.LinuxPlatform;
import com.aws.greengrass.testing.platform.macos.MacosPlatform;
import com.aws.greengrass.testing.platform.windows.WindowsPlatform;

import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PlatformResolver {
    private static final Set<String> SUPPORTED_PLATFORMS = Collections.unmodifiableSet(Stream.of(
            "all", "any", "unix", "posix", "linux", "debian", "windows", "fedora", "ubuntu", "macos",
            "raspbian", "qnx", "cygwin", "freebsd", "solaris", "sunos").collect(Collectors.toSet()));

    private final Device device;

    public PlatformResolver(final Device device) {
        this.device = device;
    }

    public Platform resolve() {
        final Map<String, Integer> ranks = createRanks();
        if (ranks.containsKey("linux")) {
            return new LinuxPlatform(device);
        } else if (ranks.containsKey("macos")) {
            return new MacosPlatform(device);
        } else if (ranks.containsKey("windows")) {
            return new WindowsPlatform(device);
        }
        throw new PlatformResolutionException("Could not find a platform support for device: " + device.platform());
    }

    private Map<String, Integer> createRanks() {
        Map<String, Integer> ranks = new HashMap<>();
        // figure out what OS we're running and add applicable tags
        // The more specific a tag is, the higher its rank should be
        // TODO: use better way to determine if a field is platform specific. Eg: using 'platform$' prefix.
        ranks.put("all", 0);
        ranks.put("any", 0);

        if (device.platform().isWindows()) {
            ranks.put("windows", 5);
        } else {
            if (device.exists(Paths.get("/bin/bash")) || device.exists(Paths.get("/usr/bin/bash"))) {
                ranks.put("unix", 3);
                ranks.put("posix", 3);
            }
            if (device.exists(Paths.get("/proc"))) {
                ranks.put("linux", 10);
            }
            if (device.exists(Paths.get("/usr/bin/apt-get"))) {
                ranks.put("debian", 11);
            }
            if (device.exists(Paths.get("/usr/bin/yum"))) {
                ranks.put("fedora", 11);
            }
            String sysver = device.executeToString(CommandInput.builder()
                    .line("sh").addArgs("-c", "uname -a")
                    .build());
            if (sysver.contains("ubuntu")) {
                ranks.put("ubuntu", 20);
            }
            if (sysver.contains("darwin")) {
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
}
