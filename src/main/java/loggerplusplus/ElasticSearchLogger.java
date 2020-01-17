package loggerplusplus;

import org.apache.http.HttpHost;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequestBuilder;
import org.elasticsearch.action.bulk.BulkItemResponse;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.client.*;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.CreateIndexResponse;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.TransportAddress;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
//import org.elasticsearch.transport.client.PreBuiltTransportClient;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class ElasticSearchLogger implements LogEntryListener{
    IndicesClient adminClient;
    RestHighLevelClient client;
    ArrayList<LogEntry> pendingEntries;
    private InetAddress address;
    private short port;
    private String clusterName;
    private boolean isEnabled;
    private String indexStringName;
    private GetIndexRequest indexName;
    private LoggerPreferences prefs;

    private final ScheduledExecutorService executorService;
    private ScheduledFuture indexTask;


    public ElasticSearchLogger(LogManager logManager, LoggerPreferences prefs){
        this.prefs = prefs;
        this.isEnabled = false;
        this.indexName = new GetIndexRequest("logger");

        logManager.addLogListener(this);
        executorService = Executors.newScheduledThreadPool(1);
    }

    public void setEnabled(boolean isEnabled) throws UnknownHostException {
        if(isEnabled){
            this.address = InetAddress.getByName(prefs.getEsAddress());
            this.port = prefs.getEsPort();
            this.clusterName = prefs.getEsClusterName();
            this.indexStringName = prefs.getEsIndex();
            this.indexName = new GetIndexRequest(indexStringName);

            this.client = new RestHighLevelClient(
                    RestClient.builder(
                            new HttpHost(address, port, "https")));
            adminClient = client.indices();
            createIndices();
            pendingEntries = new ArrayList<>();
            indexTask = executorService.scheduleAtFixedRate(new Runnable() {
                @Override
                public void run() {
                    indexPendingEntries();
                }
            }, prefs.getEsDelay(), prefs.getEsDelay(), TimeUnit.SECONDS);
        }else{
            if(this.indexTask != null){
                indexTask.cancel(true);
            }
            this.pendingEntries = null;
            this.client = null;
            this.adminClient = null;
        }
        this.isEnabled = isEnabled;
    }

    private void createIndices() {
//            XContentBuilder builder = jsonBuilder().startObject()
//                    .startObject("requestresponse")
//                        .startObject("properties")
//                            .startObject("protocol")
//                            .field("type", "text")
//                            .field("store", "true")
//                            .endObject()
//                            .startObject("method")
//                            .field("type", "text")
//                            .field("store", "true")
//                            .endObject()
//                            .startObject("host")
//                            .field("type", "text")
//                            .field("store", "true")
//                            .endObject()
//                            .startObject("path")
//                            .field("type", "text")
//                            .field("store", "true")
//                            .endObject()
//                            .startObject("requesttime")
//                            .field("type", "date")
//                            .field("store", "true")
//                            .field("format", "yyyy/MM/dd HH:mm:ss")
//                            .endObject()
//                            .startObject("responsetime")
//                            .field("type", "date")
//                            .field("store", "true")
//                            .field("format", "yyyy/MM/dd HH:mm:ss")
//                            .endObject()
//                            .startObject("status")
//                            .field("type", "text")
//                            .field("store", "true")
//                            .endObject()
//                            .startObject("title")
//                            .field("type", "text")
//                            .field("store", "true")
//                            .endObject()
//                            .startObject("newcookies")
//                            .field("type", "text")
//                            .field("store", "true")
//                            .endObject()
//                            .startObject("sentcookies")
//                            .field("type", "text")
//                            .field("store", "true")
//                            .endObject()
//                            .startObject("referrer")
//                            .field("type", "text")
//                            .field("store", "true")
//                            .endObject()
//                            .startObject("requestcontenttype")
//                            .field("type", "text")
//                            .field("store", "true")
//                            .endObject()
//                        .endObject()
//                    .endObject().endObject();
        boolean exists = false;
        try {
            exists = adminClient.exists(this.indexName, RequestOptions.DEFAULT);
        }
        catch (Exception e) {
            System.err.println(e);

        }
        if(!exists) {
            IndexRequest request = new IndexRequest(indexStringName);

            try {
                XContentBuilder builder = XContentFactory.jsonBuilder();
                builder.startObject();
                {
                    builder.field("start", "start");
                }
                builder.endObject();
                request.source();
                IndexResponse indexResponse = client.index(request, RequestOptions.DEFAULT);
            } catch (Exception e) {
                System.err.println(e);
            }

        }
//            .addMapping("requestresponse", builder).get();
    }

    public IndexRequest buildIndexRequest(LogEntry logEntry){
        try{
            IndexRequest request = new IndexRequest(this.indexStringName);
            XContentBuilder builder = XContentFactory.jsonBuilder();


            builder.startObject()
                                .field("protocol", logEntry.protocol)
                                .field("method", logEntry.method)
                                .field("host", logEntry.host)
                                .field("path", logEntry.relativeURL)
                                .field("requesttime", logEntry.requestTime.equals("NA") ? null : logEntry.requestTime)
                                .field("responsetime", logEntry.responseTime.equals("NA") ? null : logEntry.responseTime)
                                .field("status", logEntry.status)
                                .field("title", logEntry.title)
                                .field("newcookies", logEntry.newCookies)
                                .field("sentcookies", logEntry.sentCookies)
                                .field("referrer", logEntry.referrerURL)
                                .field("requestcontenttype", logEntry.requestContentType)
                                .field("requestbody", new String(logEntry.requestResponse.getRequest()))
//                                .field("responsebody", new String(logEntry.requestResponse.getResponse()))
                            .endObject();
            request.source(builder);
            return request;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private void addToPending(LogEntry logEntry){
        if(!this.isEnabled) return;
        synchronized (pendingEntries) {
            pendingEntries.add(logEntry);
        }
    }

    private void indexPendingEntries(){
        if(!this.isEnabled || this.pendingEntries.size() == 0) return;

        //BulkRequestBuilder bulkBuilder = client.prepareBulk();
        BulkRequest brequest = new BulkRequest();
        ArrayList<LogEntry> entriesInBulk;
        synchronized (pendingEntries){
            entriesInBulk = (ArrayList<LogEntry>) pendingEntries.clone();
            pendingEntries.clear();
        }

        for (LogEntry logEntry : entriesInBulk) {
            IndexRequest request = buildIndexRequest(logEntry);
            if(request != null) {
                brequest.add(request);
            }else{
                //Could not build index request. Ignore it?
            }
        }

        //BulkResponse resp = brequest.get();

        try {
            BulkResponse resp = client.bulk(brequest, RequestOptions.DEFAULT);
            if(resp.hasFailures()){
                for (BulkItemResponse bulkItemResponse : resp.getItems()) {
                    System.err.println("there was an error ------> JW");
                    System.err.println(bulkItemResponse.getFailureMessage());
                }
            }
        } catch (IOException e) {

        }


    }

    @Override
    public void onRequestAdded(LogEntry logEntry, boolean hasResponse) {
        if(!this.isEnabled) return;
        if(hasResponse){
            addToPending(logEntry);
        }
    }

    @Override
    public void onResponseUpdated(LogEntry existingEntry) {
        if(!this.isEnabled) return;
        addToPending(existingEntry);
    }

    @Override
    public void onRequestRemoved(int index, LogEntry logEntry) {

    }
}
