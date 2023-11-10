# Changelog

## v1.2.0
### New features
* Added network related steps to configure MQTT and internet network connectivity during tests.
* Added system metric steps to monitor device RAM and CPU use.

### Bug fixes and improvements
* Greengrass local CLI deployment step will retry until it succeeds.
* Tests gracefully stop Nucleus instead of killing it.
* Polls the IoT Credential endpoint until credentials are retrievable for the thing and role alias.
* Fixed bugs relating to missing artifacts and recipe directories, and missing component versions.
* No longer fails during docker image cleanup if the docker image does not exist.
* Add CURRENT keyword as version of component.

## v1.1.0
### New features
* Adds the ability to install a custom component with configuration. This requires recipe for the custom component.
* Adds the ability to update a local deployment with a custom configuration.

### Bug fixes and improvements
* Fix log context OTF version inconsistence issue.


## v1.0.0


AWS Greengrass Testing Framework is a collection of building blocks to support end to end automation from the customer perspective, using Cucumber as the feature driver. 
 AWS Greengrass users, including component developers and owners can use these very same building blocks to build custom testing solutions and qualify software changes on variable devices.