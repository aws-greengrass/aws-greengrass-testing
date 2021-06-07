# AWS Resource Management

This testing framework for Greengrass standardizes resource creation and removal
for you. The only thing you need to worry about is defining the `ResourceSpec` and the concrete
resources, and the rest is magic.

## What's a ResourceSpec?

A `ResourceSpec` is an abstract that defines how to create an `AWSResource`. While most
`ResourceSpec`'s match a concrete AWS resource, it doesn't always have to. For example: an `IoTThingSpec`
combines a number of related AWS IoT resources like a Thing, Certificate, RoleAlias, etc.
This means that once the `IoTThingSpec` creates the resource, through an
`AWSLifecycle`, all underlying resources are tracked for deletion.

## How does I add my own?

Extended the resource management feature is done by doing the following:

1. Create a `ResourceSpec`
1. Create an `AWResource`
1. Add an `AWSLifecycle`
1. Add an `AWSLifecycleModule`

Then you resource management utilities are dynamically loaded through DI.