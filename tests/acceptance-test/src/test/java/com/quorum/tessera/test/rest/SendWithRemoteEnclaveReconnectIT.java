package com.quorum.tessera.test.rest;

import com.quorum.tessera.api.model.SendRequest;
import com.quorum.tessera.config.AppType;
import com.quorum.tessera.config.CommunicationType;
import com.quorum.tessera.config.Config;
import com.quorum.tessera.config.JdbcConfig;
import com.quorum.tessera.config.KeyConfiguration;
import com.quorum.tessera.config.Peer;
import com.quorum.tessera.config.ServerConfig;
import com.quorum.tessera.config.keypairs.ConfigKeyPair;
import com.quorum.tessera.config.keypairs.DirectKeyPair;
import com.quorum.tessera.config.util.JaxbUtil;
import com.quorum.tessera.test.DBType;
import com.quorum.tessera.test.Party;
import config.ConfigDescriptor;
import config.PortUtil;
import exec.EnclaveExecManager;
import exec.NodeExecManager;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import suite.EnclaveType;
import suite.ExecutionContext;
import suite.NodeAlias;
import suite.SocketType;
import suite.Utils;

public class SendWithRemoteEnclaveReconnectIT {

    private static final Logger LOGGER = LoggerFactory.getLogger(SendWithRemoteEnclaveReconnectIT.class);

    private EnclaveExecManager enclaveExecManager;

    private NodeExecManager nodeExecManager;

    private ConfigDescriptor configDescriptor;

    private Party party;

    //    static {
    //        System.setProperty("application.jar",
    // "../../tessera-dist/tessera-app/target/tessera-app-0.9-SNAPSHOT-app.jar");
    //        System.setProperty("enclave.jaxrs.server.jar",
    // "../../enclave/enclave-jaxrs/target/enclave-jaxrs-0.9-SNAPSHOT-server.jar");
    //        System.setProperty("javax.xml.bind.JAXBContextFactory",
    // "org.eclipse.persistence.jaxb.JAXBContextFactory");
    //        System.setProperty("javax.xml.bind.context.factory", "org.eclipse.persistence.jaxb.JAXBContextFactory");
    //
    //    }
    @Before
    public void onSetup() throws IOException {

        ExecutionContext.Builder.create()
                .with(CommunicationType.REST)
                .with(DBType.H2)
                .with(SocketType.HTTP)
                .with(EnclaveType.REMOTE)
                .buildAndStoreContext();

        final PortUtil portGenerator = new PortUtil(50100);

        final String serverUriTemplate = "http://localhost:%d";

        final Config nodeConfig = new Config();
        JdbcConfig jdbcConfig = new JdbcConfig();
        jdbcConfig.setUrl("jdbc:h2:mem:junit");
        jdbcConfig.setUsername("sa");
        jdbcConfig.setPassword("");
        nodeConfig.setJdbcConfig(jdbcConfig);

        ServerConfig p2pServerConfig = new ServerConfig();
        p2pServerConfig.setApp(AppType.P2P);
        p2pServerConfig.setEnabled(true);
        p2pServerConfig.setServerAddress(String.format(serverUriTemplate, portGenerator.nextPort()));
        p2pServerConfig.setCommunicationType(CommunicationType.REST);

        final ServerConfig q2tServerConfig = new ServerConfig();
        q2tServerConfig.setApp(AppType.Q2T);
        q2tServerConfig.setEnabled(true);
        q2tServerConfig.setServerAddress(String.format(serverUriTemplate, portGenerator.nextPort()));
        q2tServerConfig.setCommunicationType(CommunicationType.REST);

        final Config enclaveConfig = new Config();

        final ServerConfig enclaveServerConfig = new ServerConfig();
        enclaveServerConfig.setApp(AppType.ENCLAVE);
        enclaveServerConfig.setEnabled(true);
        enclaveServerConfig.setServerAddress(String.format(serverUriTemplate, portGenerator.nextPort()));
        enclaveServerConfig.setCommunicationType(CommunicationType.REST);

        nodeConfig.setServerConfigs(Arrays.asList(p2pServerConfig, q2tServerConfig, enclaveServerConfig));

        DirectKeyPair keyPair =
                new DirectKeyPair(
                        "/+UuD63zItL1EbjxkKUljMgG8Z1w0AJ8pNOR4iq2yQc=", "yAWAJjwPqUtNVlqGjSrBmr1/iIkghuOh1803Yzx9jLM=");

        enclaveConfig.setKeys(new KeyConfiguration());
        enclaveConfig.getKeys().setKeyData(Arrays.asList(keyPair));

        nodeConfig.setPeers(Arrays.asList(new Peer(p2pServerConfig.getServerAddress())));

        enclaveConfig.setServerConfigs(Arrays.asList(enclaveServerConfig));

        Path configPath = Files.createFile(Paths.get(UUID.randomUUID().toString()));
        configPath.toFile().deleteOnExit();

        Path enclaveConfigPath = Files.createFile(Paths.get(UUID.randomUUID().toString()));
        enclaveConfigPath.toFile().deleteOnExit();

        try (OutputStream out = Files.newOutputStream(configPath)) {
            JaxbUtil.marshalWithNoValidation(nodeConfig, out);
            out.flush();
        }

        JaxbUtil.marshalWithNoValidation(enclaveConfig, System.out);
        try (OutputStream out = Files.newOutputStream(enclaveConfigPath)) {
            JaxbUtil.marshalWithNoValidation(enclaveConfig, out);
            out.flush();
        }
        configDescriptor = new ConfigDescriptor(NodeAlias.A, configPath, nodeConfig, enclaveConfig, enclaveConfigPath);

        String key = configDescriptor.getKey().getPublicKey();
        URL file = Utils.toUrl(configDescriptor.getPath());
        String alias = configDescriptor.getAlias().name();

        this.party = new Party(key, file, alias);

        nodeExecManager = new NodeExecManager(configDescriptor);
        enclaveExecManager = new EnclaveExecManager(configDescriptor);

        enclaveExecManager.start();

        nodeExecManager.start();
    }

    @After
    public void onTearDown() {

        nodeExecManager.stop();

        enclaveExecManager.stop();

        ExecutionContext.destroyContext();
    }

    @Test
    public void sendTransactiuonToSelfWhenEnclaveIsDown() throws InterruptedException {
        LOGGER.info("Stopping Enclave node");
        enclaveExecManager.stop();
        LOGGER.info("Stopped Enclave node");

        RestUtils utils = new RestUtils();
        byte[] transactionData = utils.createTransactionData();
        final SendRequest sendRequest = new SendRequest();
        sendRequest.setFrom(party.getPublicKey());
        sendRequest.setPayload(transactionData);

        Client client = ClientBuilder.newClient();

        final Response response =
                client.target(party.getQ2TUri())
                        .path("send")
                        .request()
                        .post(Entity.entity(sendRequest, MediaType.APPLICATION_JSON));

        assertThat(response.getStatus()).isEqualTo(503);

        //        LOGGER.info("Starting Enclave node");
        //        enclaveExecManager.start();
        //        LOGGER.info("Started Enclave node");
        //
        //        final Response secondresponse =
        //                client.target(party.getQ2TUri())
        //                        .path("send")
        //                        .request()
        //                        .post(Entity.entity(sendRequest, MediaType.APPLICATION_JSON));
        //
        //        assertThat(secondresponse.getStatus()).isEqualTo(201);
    }

    @Test
    public void reconnectingToEnclaveUpdatesKeys() throws IOException {
        enclaveExecManager.stop();

        final DirectKeyPair addedKeypair =
                new DirectKeyPair(
                        "sL/prFZhfUNhbL+7Ky7bHA+OEBhqty0L+PaOuA0bj1M=", "JIQ3a2udSn+xxfhM5pQP+sn3u9BblC84Clpk5tsYmg4=");

        final Config enclaveConfig = this.configDescriptor.getEnclaveConfig().get();
        List<ConfigKeyPair> keys = new ArrayList<>(enclaveConfig.getKeys().getKeyData());
        keys.add(addedKeypair);
        enclaveConfig.getKeys().setKeyData(keys);

        this.writeConfig(enclaveConfig, this.configDescriptor.getEnclavePath());

        this.enclaveExecManager = new EnclaveExecManager(this.configDescriptor);
        enclaveExecManager.start();

        Client client = ClientBuilder.newClient();

        final Response response = client.target(party.getP2PUri()).path("partyinfo").request().get();

        assertThat(response.getStatus()).isEqualTo(200);
    }

    private void writeConfig(final Config config, final Path outputPath) throws IOException {
        try (OutputStream out = Files.newOutputStream(outputPath)) {
            JaxbUtil.marshalWithNoValidation(config, out);
            out.flush();
        }
    }
}
