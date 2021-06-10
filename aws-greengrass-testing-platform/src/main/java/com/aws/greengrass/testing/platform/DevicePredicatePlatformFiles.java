package com.aws.greengrass.testing.platform;

import com.aws.greengrass.testing.api.device.Device;
import com.aws.greengrass.testing.api.device.exception.CommandExecutionException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

public class DevicePredicatePlatformFiles implements PlatformFiles {
    private static final Logger LOGGER = LogManager.getLogger(DevicePredicatePlatformFiles.class);
    private final Device device;
    private final PlatformFiles left;
    private final PlatformFiles right;
    private final Predicate<Device> isLeft;

    public DevicePredicatePlatformFiles(
            final Predicate<Device> isLeft,
            final Device device,
            final PlatformFiles left,
            final PlatformFiles right) {
        this.isLeft = isLeft;
        this.device = device;
        this.left = left;
        this.right = right;
    }

    public static PlatformFiles localOrRemote(final Device device, final PlatformFiles remote) {
        return new DevicePredicatePlatformFiles(d -> d.type().equals("LOCAL"), device, new LocalFiles(), remote);
    }

    private <T> T delegate(Function<PlatformFiles, T> thunk) {
        PlatformFiles files = right;
        if (isLeft.test(device)) {
            files = left;
        }
        LOGGER.debug("Using file provider {}", files);
        return thunk.apply(files);
    }

    @Override
    public void delete(Path filePath) throws CommandExecutionException {
        delegate(files -> {
            files.delete(filePath);
            return null;
        });
    }

    @Override
    public byte[] readBytes(Path filePath) throws CommandExecutionException {
        return delegate(files -> files.readBytes(filePath));
    }

    @Override
    public List<Path> listContents(Path filePath) throws CommandExecutionException {
        return delegate(files -> files.listContents(filePath));
    }
}
