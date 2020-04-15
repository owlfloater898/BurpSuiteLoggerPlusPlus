package loggerplusplus;

/*import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;*/
import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.bigquery.Bigquery;
import com.google.api.services.bigquery.BigqueryScopes;
import com.google.auth.Credentials;
import com.google.auth.oauth2.OAuth2Credentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.bigquery.*;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.GeneralSecurityException;
import java.util.Collections;


public class TestAuth {
    private static HttpTransport httpTransport;
    InstalledAppFlow
    private static final String APPLICATION_NAME = "";

    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();

    private final static String projectId = "extreme-zephyr-272820";
    private static final java.io.File DATA_STORE_DIR =
            new java.io.File(System.getProperty("user.home"), ".store/plus_sample");
    private static BigQuery bigQuery;
    private static Bigquery bigquery;
    private static String datasetName = "cobalt_1";
    private static String tableName = "burplogs";

    private static FileDataStoreFactory dataStoreFactory;

    public static void main(String[] args){
        try{
            httpTransport = GoogleNetHttpTransport.newTrustedTransport();
            dataStoreFactory=new FileDataStoreFactory(DATA_STORE_DIR);
            // authorization
            Credential credential=authorize();
            // set up global Plus instance
            //plus=new Plus.Builder(httpTransport,JSON_FACTORY,credential).setApplicationName(
                   // APPLICATION_NAME).build();
            //bigquery = new BigQuery.Builder(httpTransport,JSON_FACTORY,credential).setApplicationName(APPLICATION_NAME).build();

            bigquery = BigQueryOptions.newBuilder().setProjectId(projectId)
                    .setCredentials(

                    ).build().getService();
            this.table = bigquery.getTable(datasetName, tableName);
            this.writeChannelConfiguration =
                    WriteChannelConfiguration.newBuilder(table.getTableId()).setFormatOptions(FormatOptions.json()).build();
            bigquery.tables().insert(projectId, datasetName, tableName);
            // ...
        } catch (GeneralSecurityException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static Credentials authorize()throws Exception{
        // load client secrets
        Credentials clientSecrets= (Credentials) Credentials.fromStream((new FileInputStream("/Users/jonathanwaldman/BurpSuiteLoggerPlusPlus/src/main/java/loggerplusplus/client_secrets.json")));
        // set up authorization code flow
        GoogleAuthorizationCodeFlow flow=new GoogleAuthorizationCodeFlow.Builder(
                httpTransport,JSON_FACTORY,clientSecrets,
                Collections.singleton(BigqueryScopes.BIGQUERY_INSERTDATA)).setDataStoreFactory(
                dataStoreFactory).build();
        // authorize
        return new AuthorizationCodeInstalledApp(flow,new LocalServerReceiver()).authorize("user");
    }



}
