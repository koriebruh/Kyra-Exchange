package com.kyra.app;

import io.quarkus.test.junit.QuarkusIntegrationTest;

/**
 * Integration smoke test that runs against the <em>packaged</em> artifact — the
 * runnable JAR in a normal {@code verify}, and the compiled <b>native binary</b>
 * in {@code verify -Dnative}. Proves the shipped executable actually boots,
 * migrates the database, and serves traffic (not merely that it compiles).
 *
 * <p>Inherits the health/metrics assertions from {@link AppSmokeTest};
 * {@code @QuarkusIntegrationTest} re-targets them at the black-box process.
 */
@QuarkusIntegrationTest
class AppSmokeIT extends AppSmokeTest {
}
