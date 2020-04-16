package loggerplusplus;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.StoredCredential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.http.apache.ApacheHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.DataStore;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.bigquery.Bigquery;
import com.google.api.services.bigquery.BigqueryScopes;
import com.google.auth.Credentials;
import com.google.auth.http.HttpTransportFactory;
import com.google.auth.oauth2.*;
import com.google.cloud.bigquery.*;
import com.google.cloud.http.HttpTransportOptions;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import javax.swing.*;
import java.io.*;
import java.nio.channels.Channels;
import java.security.GeneralSecurityException;
import java.util.*;


public class TestAuth {
    private static HttpTransport httpTransport;
    private static final String APPLICATION_NAME = "";

    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();

    private final static String projectId = "extreme-zephyr-272820";
    private static final java.io.File DATA_STORE_DIR =
            new java.io.File(System.getProperty("user.home"), ".store/plus_sample");
    private static BigQuery bigQuery;
    private static String datasetName = "cobalt_1";
    private static String tableName = "request_response";
    private static String TOKENS_DIRECTORY_PATH = "/Users/jonathanwaldman/tokens";

    public static void main(String[] args) {
        try {

            Map<String, Object> hashmap = new HashMap<>();
            hashmap.put("body", "helpmelordjesuschrist");
            httpTransport = GoogleNetHttpTransport.newTrustedTransport();

            // authorization
            Credential credential = authorize();
            credential.refreshToken();

            OAuth2Credentials credentials =
                    UserCredentials.newBuilder()
                            .setAccessToken(new AccessToken(credential.getAccessToken(), new Date(credential.getExpirationTimeMilliseconds())))
                            .setRefreshToken(credential.getRefreshToken())
                            .setHttpTransportFactory(new HttpTransportOptions.DefaultHttpTransportFactory())
                            .setClientId("4977645627-qn35ro2cg9oenqsm2lv63vmnk9fblt3c.apps.googleusercontent.com")
                            .setClientSecret("Rptd3iH-zxjihiB5S2CtDAvg")
                            .build();


            System.out.println(credential.getAccessToken());
            System.out.println(credentials.getAccessToken());
            // set up global Plus instance
            //plus=new Plus.Builder(httpTransport,JSON_FACTORY,credential).setApplicationName(
            // APPLICATION_NAME).build();
            //bigquery = new BigQuery.Builder(httpTransport,JSON_FACTORY,credential).setApplicationName(APPLICATION_NAME).build();

            bigQuery = BigQueryOptions.newBuilder().setProjectId(projectId)
                    .setCredentials(
                            credentials
                    ).build().getService();
            TableId tableid = TableId.of(datasetName, tableName);
            InsertAllResponse response =
                    bigQuery.insertAll(
                            InsertAllRequest.newBuilder(tableid)
                                    .addRow(hashmap)
                                    // More rows can be added in the same RPC by invoking .addRow() on the builder.
                                    // You can also supply optional unique row keys to support de-duplication scenarios.
                                    .build());
            if (response.hasErrors()) {
                // If any of the insertions failed, this lets you inspect the errors
                for (Map.Entry<Long, List<BigQueryError>> entry : response.getInsertErrors().entrySet()) {
                    // inspect row error
                }

            /*WriteChannelConfiguration writeChannelConfiguration =
                    WriteChannelConfiguration.newBuilder(tableid).setFormatOptions(FormatOptions.json()).build();
            TableDataWriteChannel writer = bigQuery.writer(writeChannelConfiguration);
            // Write data to writer
            try (OutputStream stream = Channels.newOutputStream(writer)) {
                stream.write("{\"Body\":\"testoauth?\"}".getBytes());
            } catch (IOException e) {
                JOptionPane.showMessageDialog(null, "Error uploading");
                e.printStackTrace();
            }*/
            }
        } catch (GeneralSecurityException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }



        public static Credential authorize()
            throws IOException {
        // Load client secrets
        GoogleClientSecrets clientSecrets =
                GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(new FileInputStream("/Users/jonathanwaldman/BurpSuiteLoggerPlusPlus/src/main/java/loggerplusplus/client_secrets.json")));


        GoogleAuthorizationCodeFlow flow =
                new GoogleAuthorizationCodeFlow.Builder(httpTransport, JSON_FACTORY, clientSecrets, Collections.singleton(BigqueryScopes.BIGQUERY_INSERTDATA))
                        .setDataStoreFactory(new FileDataStoreFactory(new java.io.File(TOKENS_DIRECTORY_PATH)))
                        .setAccessType("offline")
                        .build();

        // authorize
        return new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver()).authorize("thsisdfohdsedsfffsngtxxmatter");
    }



}
