package nl.reinspanjer.wiremock.dev.deployment;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.*;
import io.quarkus.deployment.builditem.DevServicesResultBuildItem.RunningDevService;
import io.quarkus.deployment.console.ConsoleInstalledBuildItem;
import io.quarkus.deployment.logging.LoggingSetupBuildItem;
import io.quarkus.runtime.LaunchMode;
import org.jboss.logging.Logger;

import java.io.Closeable;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

class WiremockDevProcessor {

    private static final String FEATURE = "wiremock-dev";
    private static final String DEV_SERVICE_NAME = "wiremock-dev-service";
    private static final Logger LOGGER = Logger.getLogger(WiremockDevProcessor.class);

    static volatile RunningDevService devService;
    static volatile WireMockServer server;

    @BuildStep(onlyIf = IsEnabled.class)
    public FeatureBuildItem featureBuildItem() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep(onlyIf = IsEnabled.class)
    DevServicesResultBuildItem setup(LaunchModeBuildItem launchMode,
                                     LiveReloadBuildItem liveReload,
                                     CuratedApplicationShutdownBuildItem shutdown,
                                     WireMockDevConfig config) {

        if (!liveReload.isLiveReload()) {
            Runnable closeTask = () -> {
                if (devService != null) {
                    shutdownDevService();
                }
                devService = null;
                server = null;
            };
            shutdown.addCloseTask(closeTask, true);
        } else if (config.reload) {
            server.stop();
            server.start();
        }

        if (devService != null){
            return devService.toBuildItem();
        }

        if (launchMode.getLaunchMode() == LaunchMode.DEVELOPMENT) {
            LOGGER.debug("Mode is Dev");
            devService = startWireMock(config);
            return devService.toBuildItem();
        } else {
            LOGGER.debug("Running in TST mode currently not supported");
            return null;
        }

    }
    private RunningDevService startWireMock(WireMockDevConfig config) {

        LOGGER.debug("Starting WireMockServer with port [" + config.port + "] " + "and path [" + config.path + "]"   );

        WireMockConfiguration configuration = options()
                .port(config.port)
                .usingFilesUnderDirectory(config.path);

        server = new WireMockServer(configuration);

        final Supplier<RunningDevService> supplier = () -> {
            try {
                server.start();
                return new RunningDevService(DEV_SERVICE_NAME,
                        null,
                        server::stop,
                        prepareConfiguration(config)
                );
            } catch (Throwable ex) {
                throw new RuntimeException(ex);
            }
        };

        return supplier.get();
    }

    private Map<String, String> prepareConfiguration(WireMockDevConfig config){
        return new HashMap<>();
    }

    private void shutdownDevService(){
        try {
            devService.close();
        } catch (Throwable e) {
            LOGGER.error("Failed to stop WireMock server", e);
        } finally {
            devService = null;
            server = null;
        }
    }
    public static class IsEnabled implements BooleanSupplier {
        WireMockDevConfig config;
        public boolean getAsBoolean() {
            if (config.enabled){
                return true;
            } else {
                return false;
            }
        }
    }

}
