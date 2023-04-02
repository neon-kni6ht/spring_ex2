package spring_ex2;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.Setter;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.lang.Thread.currentThread;


public class CrptApi {
    private long timeUnit;
    private int requestLimit;
    private static volatile List<Long> buffer;

    private volatile String token;

    private final String COMMISSIONING_CONTRACT_RF_CREATE_URL = "/api/v3/lk/documents/commissioning/contract/create";

    private final String AUTHORIZATION_URL = "api/v3/auth/cert/key";

    private final String AUTHENTICATION_URL = "api/v3/auth/cert";
    private final String HOST = "https://ismp.crpt.ru";

    private final long AUTHORIZATION_TTL = 3600000;

    private final long AUTHENTICATION_TTL = 36000000;

    private volatile String uuid;

    private volatile String data;

    private volatile String signedData;

    private volatile LocalDateTime authorizationTime;

    private volatile LocalDateTime authenticationTime;

    @Getter
    @Setter
    private static class AuthResponse {
        private String uuid;
        private String data;
    }

    @Getter
    @Setter
    private static class AuthToken {
        String token;
    }

    private static Logger logger = Logger.getLogger(CrptApi.class.getName());

    private enum DocumentFormat {
        MANUAL,
        XML,
        CSV
    }

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.timeUnit = timeUnit.toMillis(1);
        this.requestLimit = requestLimit;
    }


    public void createCommissioningRFContract(Object document, String signature, String productGroup, String type, DocumentFormat format) throws IOException, InterruptedException {

        String encodedDocument;
        switch (format) {
            default: {
                ObjectMapper mapper = new ObjectMapper();
                String json = mapper.writeValueAsString(document);
                encodedDocument = Base64.getEncoder().encodeToString(json.getBytes());
                break;
            }
        }

        final List<NameValuePair> params = new ArrayList<>();
        params.add(new BasicNameValuePair("document_format", format.name()));
        params.add(new BasicNameValuePair("product_document", encodedDocument));
        params.add(new BasicNameValuePair("signature", signature));
        params.add(new BasicNameValuePair("type", type));
        if (!productGroup.equals(""))
            params.add(new BasicNameValuePair("product_group", productGroup));

        sendPostRequest(params, COMMISSIONING_CONTRACT_RF_CREATE_URL);

    }

    private void sendPostRequest(List<NameValuePair> params, String method) throws InterruptedException {

        getAuthentication();
        getAuthorization();

        while (buffer.size() >= requestLimit) wait(timeUnit);

        buffer.add(currentThread().getId());

        try (final CloseableHttpClient httpclient = HttpClients.createDefault()) {


            final HttpPost httpPost = new HttpPost(HOST + method);

            try {
                HttpEntity requestEntity = new UrlEncodedFormEntity(params);
                httpPost.setEntity(requestEntity);

                httpPost.setHeader(HttpHeaders.CONTENT_TYPE, "application/json");
                httpPost.setHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);

                try (CloseableHttpResponse response = httpclient.execute(httpPost)) {

                    final HttpEntity entity = response.getEntity();
                    logger.log(Level.FINE, EntityUtils.toString(entity));

                } catch (IOException e) {

                    logger.log(Level.SEVERE, "Unable to complete request\n" + e);
                }

            } catch (UnsupportedEncodingException e) {

                logger.log(Level.SEVERE, "Unable to set http entity \n" + params + "\n" + e);

            }
        } catch (IOException e) {

            logger.log(Level.SEVERE, "Unable to initialize http client\n" + e);

        } finally {
            buffer.remove(currentThread().getId());
        }
    }

    private void getAuthorization() {
        if ((LocalDateTime.now().compareTo(authenticationTime.plusSeconds(AUTHORIZATION_TTL / 1000)) > 0) || authorizationTime == null || token == null || token.equals("")) {
            signedData = getSignedData();

            final List<NameValuePair> params = new ArrayList<>();
            params.add(new BasicNameValuePair("uuid", uuid));
            params.add(new BasicNameValuePair("data", Base64.getEncoder().encodeToString(signedData.getBytes())));


            try (final CloseableHttpClient httpclient = HttpClients.createDefault()) {


                final HttpPost httpPost = new HttpPost(HOST + AUTHORIZATION_URL);

                try {
                    HttpEntity requestEntity = new UrlEncodedFormEntity(params);
                    httpPost.setEntity(requestEntity);

                    httpPost.setHeader(HttpHeaders.CONTENT_TYPE, "application/json");

                    try (CloseableHttpResponse response = httpclient.execute(httpPost)) {

                        final HttpEntity entity = response.getEntity();
                        ObjectMapper mapper = new ObjectMapper();
                        AuthToken authToken = mapper.readValue(EntityUtils.toString(entity), AuthToken.class);
                        token = authToken.getToken();
                        logger.log(Level.FINE, EntityUtils.toString(entity));
                        authorizationTime = LocalDateTime.now();
                    } catch (IOException e) {

                        logger.log(Level.SEVERE, "Unable to complete request\n" + e);
                    }

                } catch (UnsupportedEncodingException e) {

                    logger.log(Level.SEVERE, "Unable to set http entity \n" + params + "\n" + e);

                }
            } catch (IOException e) {

                logger.log(Level.SEVERE, "Unable to initialize http client\n" + e);

            }
        }
    }


    private void getAuthentication() {
        if ((uuid != null && LocalDateTime.now().compareTo(authenticationTime.plusSeconds(AUTHENTICATION_TTL / 1000)) > 0)
                || authenticationTime == null
                || uuid == null
                || data == null
                || uuid.equals("")
                || data.equals(""))

            try (final CloseableHttpClient httpclient = HttpClients.createDefault()) {


                final HttpGet httpGet = new HttpGet(HOST + AUTHENTICATION_URL);


                try (CloseableHttpResponse response = httpclient.execute(httpGet)) {

                    final HttpEntity entity = response.getEntity();

                    ObjectMapper mapper = new ObjectMapper();
                    AuthResponse authResponse = mapper.readValue(EntityUtils.toString(entity), AuthResponse.class);
                    uuid = authResponse.getUuid();
                    data = authResponse.getData();
                    authenticationTime = LocalDateTime.now();
                    logger.log(Level.FINE, EntityUtils.toString(entity));

                } catch (IOException e) {

                    logger.log(Level.SEVERE, "Unable to complete request\n" + e);
                }

            } catch (IOException e) {

                logger.log(Level.SEVERE, "Unable to initialize http client\n" + e);

            }
    }

    //?? implement cloud sign
    private String getSignedData() {
        return data;
    }

    public static void main(String[] args) {
        CrptApi crptApi = new CrptApi(TimeUnit.MINUTES, 4);
        crptApi.getAuthentication();

    }
}
