package loggerplusplus;
import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.BigQueryOptions;
import com.google.cloud.bigquery.FormatOptions;
import com.google.cloud.bigquery.TableDataWriteChannel;
import com.google.cloud.bigquery.Job;
import com.google.cloud.bigquery.JobStatistics;
import com.google.cloud.bigquery.Table;
import com.google.cloud.bigquery.WriteChannelConfiguration;
import com.google.gson.JsonObject;
import org.elasticsearch.client.indices.GetIndexRequest;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

import com.google.auth.oauth2.ServiceAccountCredentials;

import javax.swing.*;
import java.io.FileInputStream;
import java.util.concurrent.TimeUnit;


public class BigQueryLogger implements LogEntryListener {
    private boolean isEnabled;
    private String indexStringName;
    private GetIndexRequest indexName;
    private LoggerPreferences prefs;
    //private String credsPath = "/Users/jonathanwaldman/Downloads/My First Project-6bbba6fec403.json";
    private String datasetName = "cobalt_1";
    private String tableName = "burplogs";
    private String location = "US";
    private final double bigQueryLimit = 9999999;
    private final int rowCountLimit = 15999;
    private String ptid;
    private com.google.cloud.bigquery.BigQuery bigquery;
    private final ScheduledExecutorService executorService;
    private final String projectId = "extreme-zephyr-272820";
    private ScheduledFuture indexTask;
    private ArrayList<LogEntry> pendingEntries;
    private Table table;
    private BigQuery bigQuery;
    private WriteChannelConfiguration writeChannelConfiguration;

    public BigQueryLogger(LogManager logManager, LoggerPreferences prefs){
        this.prefs = prefs;
        this.isEnabled = false;
        logManager.addLogListener(this);
        executorService = Executors.newScheduledThreadPool(1);
    }
    public void setEnabled(boolean isEnabled) throws IOException {
        if (isEnabled){
            bigQueryInit();
            pendingEntries = new ArrayList<>();
            indexTask = executorService.scheduleAtFixedRate(new Runnable() {
                @Override
                public void run() {
                    uploadEntries();
                }
            }, prefs.getBqDelay(), prefs.getBqDelay(), TimeUnit.SECONDS);
        }
        else{
            if (this.indexTask != null){
                indexTask.cancel(true);
            }
            this.pendingEntries = null;
        }
        this.isEnabled = isEnabled;
    }


    private void bigQueryInit() throws IOException {
        this.bigQuery = null;
        this.ptid = prefs.getPtid().startsWith("#") ? prefs.getPtid() : "#" +  prefs.getPtid(); //Ensure ptid starts with "#"
        this.bigquery = BigQueryOptions.newBuilder().setProjectId(projectId)
                .setCredentials(
                        ServiceAccountCredentials.fromStream(new FileInputStream(prefs.getBqFileLocation()))
                ).build().getService();
        this.table = bigquery.getTable(datasetName, tableName);
        this.writeChannelConfiguration =
                WriteChannelConfiguration.newBuilder(table.getTableId()).setFormatOptions(FormatOptions.json()).build();
        //JOptionPane.showMessageDialog(null, "BQ has been initialized");
    }

    private long upload(String content) throws InterruptedException {
        //JOptionPane.showMessageDialog(null, "beginning to upload");
        TableDataWriteChannel writer = this.bigquery.writer(writeChannelConfiguration);
        // Write data to writer
        try (OutputStream stream = Channels.newOutputStream(writer)) {
            stream.write(content.getBytes());
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null, "Error uploading");
            e.printStackTrace();
        }
        Job job = writer.getJob();
        job = job.waitFor();
        JobStatistics.LoadStatistics stats = job.getStatistics();
        //JOptionPane.showMessageDialog(null, stats.getOutputRows());
        return stats.getOutputRows();
    }

    public String buildIndexRequest(LogEntry logEntry){
        try{
            JsonObject jsonObject = new JsonObject();


            jsonObject.addProperty("protocol", logEntry.protocol);
            jsonObject.addProperty("method", logEntry.method);
            jsonObject.addProperty("host", logEntry.host);
            jsonObject.addProperty("path", logEntry.relativeURL);
            jsonObject.addProperty("requesttime", logEntry.requestTime.equals("NA") ? null : logEntry.requestTime);
            jsonObject.addProperty("responsetime", logEntry.responseTime.equals("NA") ? null : logEntry.responseTime);
            jsonObject.addProperty("status", logEntry.status.toString());
            jsonObject.addProperty("title", logEntry.title);
            jsonObject.addProperty("newcookies", logEntry.newCookies);
            jsonObject.addProperty("sentcookies", logEntry.sentCookies);
            jsonObject.addProperty("referrer", logEntry.referrerURL);
            jsonObject.addProperty("requestcontenttype", logEntry.requestContentType);
            jsonObject.addProperty("ptid", this.ptid);
 //           jsonObject.addProperty("requestbody", new String(logEntry.requestResponse.getRequest()));
 //           jsonObject.addProperty("responsebody", new String(logEntry.requestResponse.getResponse()));


            return jsonObject.toString();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }


    private void uploadEntries() {
        //JOptionPane.showMessageDialog(null, "uploading entries");
        if (!this.isEnabled || this.pendingEntries.size() == 0) return;
        ArrayList<LogEntry> entriesInBulk;
        StringBuilder sb = new StringBuilder();
        int rowCount = 0;
        synchronized (pendingEntries) {
            entriesInBulk = (ArrayList<LogEntry>) pendingEntries.clone();
            pendingEntries.clear();
        }

        for (LogEntry logEntry : entriesInBulk) {
            if (mimeIsUploadable(convertMimetype(logEntry.responseMimeType, logEntry.responseInferredMimeType))) {
                String request = buildIndexRequest(logEntry);
                double totalSizeToBe = request.length() + sb.length() +1;
                //JOptionPane.showMessageDialog(null, "this is the request: " + request);


                if (request != null && request.length() + 1 < this.bigQueryLimit && totalSizeToBe <= this.bigQueryLimit && rowCount < rowCountLimit) {      //Include checks to ensure that the entry itself is not greater than 10MB. If entry is greater than 10MB, discard the entry.
                    sb.append(request);
                    sb.append("\n");
                    rowCount ++;
                    //JOptionPane.showMessageDialog(null, "appended the request: " + request);
                }

                else if (request.length() + 1 >= this.bigQueryLimit) {   //if the size of one request is greater than 10MB, do nothing
                    //do nothing
                }

                else if(totalSizeToBe > this.bigQueryLimit || rowCount >= rowCountLimit) {      //If whole request will be greater than 10MB, send the request prior to appending, delete the contents of the builder, then add the new request
                    prepareToSend(sb);
                    sb.setLength(0);
                    rowCount = 0;
                    sb.append(request);
                    rowCount ++;
                }
                else {
                    //Drop the request
                }
            }
        }
        prepareToSend(sb);
    }



    private synchronized void prepareToSend(StringBuilder sb) {
        long resp = 0;
        String stringifiedBuilder = sb.toString();
        try {
            JOptionPane.showMessageDialog(null, "this is the string builder to upload: " + stringifiedBuilder);

            if (stringifiedBuilder.length() > 0) {
                resp = upload(stringifiedBuilder.trim());
            }
            if (resp == 0) {
                JOptionPane.showMessageDialog(null, "Nothing was uploaded. Either nothing to upload or there was an error");
            }

        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }



    private String convertMimetype(String responseMimeType, String responseInferredMimeType) {
        if (responseMimeType.equalsIgnoreCase("png") || responseInferredMimeType.equalsIgnoreCase("png") ||
                responseMimeType.equalsIgnoreCase("gif") || responseInferredMimeType.equalsIgnoreCase("gif") ||
                responseMimeType.equalsIgnoreCase("jpeg") || responseInferredMimeType.equalsIgnoreCase("jpeg") ||
                responseMimeType.equalsIgnoreCase("image") || responseInferredMimeType.equalsIgnoreCase("image")
        ) {return "image";}

        else if (responseMimeType.equalsIgnoreCase("text") || responseInferredMimeType.equalsIgnoreCase("text") ||
                responseMimeType.equalsIgnoreCase("json") || responseInferredMimeType.equalsIgnoreCase("json")
        ) {return "text";}

        else if (responseMimeType.equalsIgnoreCase("flash") || responseInferredMimeType.equalsIgnoreCase("flash")
        ) {return "flash";}


        else if (responseMimeType.equalsIgnoreCase("html") || responseInferredMimeType.equalsIgnoreCase("html")

        ) {return "html";}

        else if (responseMimeType.equalsIgnoreCase("script") || responseInferredMimeType.equalsIgnoreCase("script")
        ) {return "script";}


        else if (responseMimeType.equalsIgnoreCase("xml") || responseInferredMimeType.equalsIgnoreCase("xml")
        ) { return "xml";}

        else if (responseMimeType.equalsIgnoreCase("css") || responseInferredMimeType.equalsIgnoreCase("css")
        ) { return "css";}

        else if (responseMimeType.equalsIgnoreCase("app") || responseInferredMimeType.equalsIgnoreCase("app") ||
                responseMimeType.equalsIgnoreCase("video") || responseInferredMimeType.equalsIgnoreCase("video")
        ) { return "binary";}

        else if (responseMimeType.equalsIgnoreCase("") && responseInferredMimeType.equalsIgnoreCase("")
        ) { return "";}

        return null;
    }

    private boolean mimeIsUploadable(String mimeType) {
        Boolean uploadable = false;
        if (mimeType == null) {uploadable = true; return  uploadable;} //if mimetype is null return true.

        switch (mimeType) {
            case "":                                                   //if mimetype is empty string, return true.
                uploadable = true;
                break;

            case "binary":
                if (prefs.isEnabled4MimeBinary()) {
                    uploadable = true;
                }
                break;
            case "css":
                if (prefs.isEnabled4MimeCSS()) {
                    uploadable = true;
                }
                break;
            case "flash":
                if (prefs.isEnabled4MimeFlash()) {
                    uploadable = true;
                }
                break;
            case "xml":
                if (prefs.isEnabled4MimeXML()) {
                    uploadable = true;
                }
                break;
            case "script":
                if (prefs.isEnabled4MimeScript()) {
                    uploadable = true;
                }
                break;
            case "html":
                if (prefs.isEnabled4MimeHtml()) {
                    uploadable = true;
                }
                break;
            case "text":
                if (prefs.isEnabled4MimeOtherText()) {
                    uploadable = true;
                }
                break;
        }

        return uploadable;
    }

    private void addToPending(LogEntry logEntry) {
        if(!this.isEnabled) return;
        synchronized (pendingEntries) {

            pendingEntries.add(logEntry);
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
