package net.lbruun.tsid.k8sinstanceid;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.utils.Utils;
import org.slf4j.helpers.MessageFormatter;

import java.util.Objects;

/**
 * This is an example of how to use the client to update or create a ConfigMap.
 */
public class SetInstanceId {
    private static final int RETRY_PAUSE_MS = 400;
    private static final int MAX_ATTEMPTS = 20;

    public static void main(String[] args) throws InterruptedException {
        try (KubernetesClient k8sClient = new KubernetesClientBuilder().build()) {

            // Fabric8 default config will attempt to read the current namespace from
            // file '/var/run/secrets/kubernetes.io/serviceaccount/namespace/' which is automatically set by
            // Kubernetes.

            String namespace = Utils.getEnvVar("K8S_NAMESPACE", k8sClient.getConfiguration().getNamespace());
            namespace = "mytest";  // !!!!!!!!!!!!!!!!!!!!!!!!!!!! remove !!!!!!!!!!!!!!!!!!!!!


            Objects.requireNonNull(namespace, "Could not determine Kubernetes namespace value");

            log("Current namespace: {}", namespace);


            String configMapName = "cheese";

            int attempts = 0;

            while (true) {
                attempts++;

                if (attempts > MAX_ATTEMPTS ) {
                    logE("Not able to obtain exclusive lock on ConfigMap '{}' in {} attempts. Aborting.", configMapName, MAX_ATTEMPTS);
                    System.exit(1);
                }
                log("Querying Kubernetes for ConfigMap '{}'", configMapName);
                Resource<ConfigMap> configMapResource = k8sClient.configMaps().inNamespace(namespace).withName(configMapName);
                ConfigMap oldConfigMap = configMapResource.get();
                boolean configMapExist = (configMapResource.get() != null);

                try {
                    if (configMapExist) {
                        log("Existing ConfigMap '{}' found", configMapName);
                        log("Updating ConfigMap '{}'", configMapName);
                        k8sClient.configMaps().inNamespace(namespace)
                                .resource(getConfigMapFromExisting(oldConfigMap))
                                .update();
                    } else {
                        log("No existing ConfigMap '{}' found", configMapName);
                        log("Creating ConfigMap '{}'", configMapName);
                        k8sClient.configMaps().inNamespace(namespace)
                                .resource(getConfigMapNew(configMapName))
                                .create();
                    }
                    break;
                } catch (KubernetesClientException ex) {
                    if (ex.getCode() == 409) {
                        if (configMapExist) {
                            log("Optimistic locking error while updating ConfigMap '{}'. Another Pod has updated the ConfigMap in the meantime.", configMapName);
                        } else {
                            log("Optimistic locking error while creating ConfigMap '{}'. Another Pdd has already created the ConfigMap.", configMapName);
                        }
                        log("Retrying operation in {} milliseconds.", RETRY_PAUSE_MS);
                        Thread.sleep(RETRY_PAUSE_MS);
                    } else {
                        throw ex;
                    }
                }
            }
        }
    }

    private static ConfigMap getConfigMapFromExisting(ConfigMap existingConfigMap) {
        ConfigMapBuilder configMapBuilder = new ConfigMapBuilder(existingConfigMap);
        return addConfigMapContent(configMapBuilder).build();
    }

    private static ConfigMap getConfigMapNew(String configMapName) {
        ConfigMapBuilder configMapBuilder = new ConfigMapBuilder().withNewMetadata().withName(configMapName).endMetadata();
        return addConfigMapContent(configMapBuilder).build();
    }

    private static ConfigMapBuilder addConfigMapContent (ConfigMapBuilder configMapBuilder) {
        return configMapBuilder.addToData("foo", Long.toString(System.currentTimeMillis()));
    }


    private static void log(String format, Object... arguments) {
        System.out.println(MessageFormatter.format(format, arguments));
    }

    private static void logE(String format, Object... arguments) {
        System.err.println(MessageFormatter.format(format, arguments));
    }
}