/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.cluster;

import static glide.TestConfiguration.SERVER_VERSION;
import static glide.TestUtilities.commonClusterClientConfig;
import static glide.TestUtilities.getRandomString;
import static glide.api.BaseClient.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import glide.api.GlideClusterClient;
import glide.api.models.configuration.RedisCredentials;
import glide.api.models.exceptions.ClosingException;
import glide.api.models.exceptions.RequestException;
import java.util.concurrent.ExecutionException;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(10) // seconds
public class ClusterClientTests {

    @SneakyThrows
    @Test
    public void register_client_name_and_version() {
        String minVersion = "7.2.0";
        assumeTrue(
                SERVER_VERSION.isGreaterThanOrEqualTo(minVersion),
                "Redis version required >= " + minVersion);

        GlideClusterClient client =
                GlideClusterClient.createClient(commonClusterClientConfig().build()).get();

        String info =
                (String) client.customCommand(new String[] {"CLIENT", "INFO"}).get().getSingleValue();
        assertTrue(info.contains("lib-name=GlideJava"));
        assertTrue(info.contains("lib-ver=unknown"));

        client.close();
    }

    @SneakyThrows
    @Test
    public void can_connect_with_auth_requirepass() {
        GlideClusterClient client =
                GlideClusterClient.createClient(commonClusterClientConfig().build()).get();

        String password = "TEST_AUTH";
        client.customCommand(new String[] {"CONFIG", "SET", "requirepass", password}).get();

        // Creation of a new client without a password should fail
        ExecutionException exception =
                assertThrows(
                        ExecutionException.class,
                        () -> GlideClusterClient.createClient(commonClusterClientConfig().build()).get());
        assertTrue(exception.getCause() instanceof ClosingException);

        // Creation of a new client with credentials
        GlideClusterClient auth_client =
                GlideClusterClient.createClient(
                                commonClusterClientConfig()
                                        .credentials(RedisCredentials.builder().password(password).build())
                                        .build())
                        .get();

        String key = getRandomString(10);
        String value = getRandomString(10);

        assertEquals(OK, auth_client.set(key, value).get());
        assertEquals(value, auth_client.get(key).get());

        // Reset password
        client.customCommand(new String[] {"CONFIG", "SET", "requirepass", ""}).get();

        auth_client.close();
        client.close();
    }

    @SneakyThrows
    @Test
    public void can_connect_with_auth_acl() {
        GlideClusterClient client =
                GlideClusterClient.createClient(commonClusterClientConfig().build()).get();

        String username = "testuser";
        String password = "TEST_AUTH";
        assertEquals(
                OK,
                client
                        .customCommand(
                                new String[] {
                                    "ACL",
                                    "SETUSER",
                                    username,
                                    "on",
                                    "allkeys",
                                    "+get",
                                    "+cluster",
                                    "+ping",
                                    "+info",
                                    "+client",
                                    ">" + password,
                                })
                        .get()
                        .getSingleValue());

        String key = getRandomString(10);
        String value = getRandomString(10);

        assertEquals(OK, client.set(key, value).get());

        // Creation of a new cluster client with credentials
        GlideClusterClient testUserClient =
                GlideClusterClient.createClient(
                                commonClusterClientConfig()
                                        .credentials(
                                                RedisCredentials.builder().username(username).password(password).build())
                                        .build())
                        .get();

        assertEquals(value, testUserClient.get(key).get());

        ExecutionException executionException =
                assertThrows(ExecutionException.class, () -> testUserClient.set("foo", "bar").get());
        assertTrue(executionException.getCause() instanceof RequestException);

        client.customCommand(new String[] {"ACL", "DELUSER", username}).get();

        testUserClient.close();
        client.close();
    }

    @SneakyThrows
    @Test
    public void client_name() {
        GlideClusterClient client =
                GlideClusterClient.createClient(
                                commonClusterClientConfig().clientName("TEST_CLIENT_NAME").build())
                        .get();

        String clientInfo =
                (String) client.customCommand(new String[] {"CLIENT", "INFO"}).get().getSingleValue();
        assertTrue(clientInfo.contains("name=TEST_CLIENT_NAME"));

        client.close();
    }

    @Test
    @SneakyThrows
    public void closed_client_throws_ExecutionException_with_ClosingException_as_cause() {
        GlideClusterClient client =
                GlideClusterClient.createClient(commonClusterClientConfig().build()).get();

        client.close();
        ExecutionException executionException =
                assertThrows(ExecutionException.class, () -> client.set("foo", "bar").get());
        assertTrue(executionException.getCause() instanceof ClosingException);
    }
}
