package com.aws.greengrass.testing.modules.exception;

public class ModuleProvisionException extends RuntimeException {
    private static final long serialVersionUID = -3712670679780602675L;

    public ModuleProvisionException(Throwable ex) {
        super(ex);
    }
}
