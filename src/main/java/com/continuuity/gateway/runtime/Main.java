package com.continuuity.gateway.runtime;

import com.continuuity.app.guice.LocationRuntimeModule;
import com.continuuity.common.conf.CConfiguration;
import com.continuuity.common.guice.ConfigModule;
import com.continuuity.common.guice.DiscoveryRuntimeModule;
import com.continuuity.common.guice.IOModule;
import com.continuuity.common.metrics.OverlordMetricsReporter;
import com.continuuity.data.runtime.DataFabricModules;
import com.continuuity.gateway.Gateway;
import com.google.inject.Guice;
import com.google.inject.Injector;

import java.util.concurrent.TimeUnit;

/**
 * Main is a simple class that allows us to launch the Gateway as a standalone
 * program. This is also where we do our runtime injection.
 * <p/>
 */
public class Main {

  /**
   * Our main method.
   *
   * @param args Our command line options
   */
  public static void main(String[] args) {
    // Load our configuration from our resource files
    CConfiguration configuration = CConfiguration.create();

    // Set up our Guice injections
    Injector injector = Guice.createInjector(
        new GatewayModules().getDistributedModules(),
        new DataFabricModules().getDistributedModules(),
        new ConfigModule(configuration),
        new IOModule(),
        new LocationRuntimeModule().getDistributedModules(),
        new DiscoveryRuntimeModule().getDistributedModules()
        );

    // Get our fully wired Gateway
    Gateway theGateway = injector.getInstance(Gateway.class);

    // Now, initialize the Gateway
    try {

      // enable metrics for this JVM. Note this may only be done once
      // per JVM, hence we do it only in the gateway.Main.
      OverlordMetricsReporter.enable(1, TimeUnit.SECONDS, configuration);

      // Start the gateway!
      theGateway.start(null, configuration);

    } catch (Exception e) {
      e.printStackTrace();
      System.exit(-1);
    }

  }

} // End of Main

