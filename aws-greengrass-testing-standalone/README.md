## AWS Greengrass Testing Application

This is a standalone application to drive "feature testing" outside the
context of a Java programming environment.

## Run custom features

Let's assume that you are building a Python based component like the
[HelloWorld][1]. You will need a minimum:

1. The component recipe file
2. The component source
3. Some version of the [Greengrass archive][2] downloaded as `greengrass-nucleus-latest.zip`

Assuming the following directory structure [hello_world](hello_world):

- [recipe.yaml](hello_world/recipe.yaml)
- [src/main.py](hello_world/src/main.py)
- [features/example.feature](hello_world/features/example.feature)

You can now run the end to end feature:

```
java \
-Dggc.archive=greengrass-nucleus-latest.zip \
-Dfeature.path=hello_world/features \
-Dtest.log.path=hello_world/test-results \
-Dtags=HelloWorld \
-jar aws-greengrass-testing-standlone.jar
```

The results of the run can be found in `hello_world/test-results`.

[1]: https://docs.aws.amazon.com/greengrass/v2/developerguide/create-components.html#develop-component
[2]: https://d2s8p88vqu9w66.cloudfront.net/releases/greengrass-nucleus-latest.zip
