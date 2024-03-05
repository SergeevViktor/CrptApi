package org.example;

import com.google.gson.*;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

public class CrptApi {
    private static final String URL = "https://ismp.crpt.ru/api/v3/lk/documents/create";
    private final ReentrantLock lock;
    private final CloseableHttpClient httpClient;
    private final AtomicInteger currentCount;
    private final Gson gson;
    private int requestLimit;
    private long interval;
    private long lastResetTime;

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.requestLimit = requestLimit;
        this.interval = timeUnit.toMillis(3);
        this.currentCount = new AtomicInteger(0);
        this.lastResetTime = System.currentTimeMillis();
        this.lock = new ReentrantLock();
        this.httpClient = HttpClients.createDefault();
        this.gson = new GsonBuilder()
                .setPrettyPrinting()
                .registerTypeAdapter(LocalDate.class, new LocalDateAdapter())
                .create();
    }

    public String createDocument(Document document, String signature) {
        if (overruns()) {
            String jsonDocument = gson.toJson(document);
            try {
                HttpPost request = new HttpPost(URL);
                request.setHeader("Content-Type", "application/json");
                request.setHeader("Signature", signature);
                request.setEntity(new StringEntity(jsonDocument, ContentType.APPLICATION_JSON));

                HttpResponse response = httpClient.execute(request);
                HttpEntity entity = response.getEntity();
                System.out.println("Запрос на создания документа.");

                if (entity != null) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(entity.getContent()));
                    String line;
                    StringBuilder responseBody = new StringBuilder();
                    while ((line = reader.readLine()) != null) {
                        responseBody.append(line);
                    }
                    System.out.println("Запрос выполнен.");
                    return responseBody.toString();
                }


            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            System.out.println("API заблокирован, первышен лимит запросов.");
        }
        return null;
    }

    private boolean overruns() {
        lock.lock();
        try {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastResetTime > interval) {
                currentCount.set(0);
                lastResetTime = currentTime;
            }

            return currentCount.getAndIncrement() < requestLimit;
        } finally {
            lock.unlock();
        }
    }

    public static void main(String[] args) {
        CrptApi crptApi = new CrptApi(TimeUnit.MILLISECONDS, 10);
        System.out.println(crptApi.createDocument(new Document(), "signature"));
    }



    private static class LocalDateAdapter implements JsonSerializer<LocalDate> {

        public JsonElement serialize(LocalDate date, Type typeOfSrc, JsonSerializationContext context) {
            return new JsonPrimitive(date.format(DateTimeFormatter.ISO_LOCAL_DATE));
        }
    }

    @Getter
    @Setter
    @RequiredArgsConstructor
    private static class Document {
        private String participantInn;
        private String docId;
        private String docStatus;
        private String docType;
        private boolean importRequest;
        private String ownerInn;
        private String producerInn;
        private LocalDate productionDate;
        private String productionType;
        private LocalDate regDate;
        private String regNumber;
        private List<Product> products;
    }

    @Getter
    @Setter
    @RequiredArgsConstructor
    private static class Product {
        private String certificateDocument;
        private LocalDate certificateDocumentDate;
        private String certificateDocumentNumber;
        private String ownerInn;
        private String producerInn;
        private LocalDate productionDate;
        private String tnvedCode;
        private String uitCode;
        private String uituCode;
    }
}