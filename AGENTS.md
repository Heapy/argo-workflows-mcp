## Do a reality check more often

Try to implement an MVP solution first, then add features one by one. 
Do not overengineer, keep it simple initially, let the user verify the working solution ASAP.

For example, in the case of MCP server, do not implement all tools at once. 
Implement one, let the user test it, and only then add another.

## Dependencies

Check gradle/libs.versions.toml for dependencies. 
As well as @settings.gradle.kts and @build.gradle.kts.

## MCP Configuration

Do not use files for configuration, everything should be passed as environment variables.

## Argo Client

Code in argo-client is generated from OpenAPI spec, you can check @codegen folder for details. 
Never edit code in argo-client directly, update codegen configuration or create new files in root module.


## Testing

- Run `./gradlew test` to run all tests
- Use Junit Jupiter, the current version is 6.0.1
- Do not use @BeforeAll/@AfterAll, ensure each test is isolated and contained setup/teardown logic.
- Propose JUnit extensions for common tasks.
