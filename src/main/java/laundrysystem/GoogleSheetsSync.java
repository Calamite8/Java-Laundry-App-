// Deprecated

package laundrysystem;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.AppendValuesResponse;
import com.google.api.services.sheets.v4.model.ValueRange;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;

import java.io.FileInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

/**
 * Appends a row to a Google Sheet every time an order is claimed, acting
 * as a simple backup ledger alongside MySQL.
 *
 * Requires: google-api-client, google-auth-library-oauth2-http,
 * google-api-services-sheets on the classpath (see the QR claim system
 * guide, Part 2 and Part 3).
 *
 * NOTE: exact class names for the auth library have shifted across
 * versions of the Google API client libraries (older samples use
 * GoogleCredential + FileDataStoreFactory instead of
 * google-auth-library's GoogleCredentials). If this doesn't compile
 * against the version Maven pulls in, check the "Service Accounts"
 * quickstart on https://developers.google.com/sheets/api/quickstart/java
 * for the current recommended auth snippet and swap it in here --
 * the rest of this class (building the Sheets service, calling
 * spreadsheets().values().append(...)) stays the same.
 */
public class GoogleSheetsSync {

    private static final String APPLICATION_NAME = "Laundry System Claims";
    private static final String CREDENTIALS_FILE_PATH = "credentials.json";
    private static final String SPREADSHEET_ID = "PUT_YOUR_SPREADSHEET_ID_HERE";
    private static final String RANGE = "Claims!A:E"; // sheet tab name + columns
    private static final DateTimeFormatter TIMESTAMP_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static Sheets sheetsService;

    private static Sheets getService() throws IOException, GeneralSecurityException {
        if (sheetsService == null) {
            GoogleCredentials credentials = GoogleCredentials
                    .fromStream(new FileInputStream(CREDENTIALS_FILE_PATH))
                    .createScoped(Collections.singletonList("https://www.googleapis.com/auth/spreadsheets"));

            sheetsService = new Sheets.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    GsonFactory.getDefaultInstance(),
                    new HttpCredentialsAdapter(credentials))
                    .setApplicationName(APPLICATION_NAME)
                    .build();
        }
        return sheetsService;
    }

    /**
     * Appends one claim record to the "Claims" tab. Any failure here is
     * logged but NOT thrown -- a Sheets outage shouldn't block the MySQL
     * claim from succeeding.
     */
    public static void logClaim(int orderId, String customerName, String serviceType, double price) {
        try {
            List<Object> row = List.of(
                    orderId,
                    customerName,
                    serviceType,
                    String.format("%.2f", price),
                    LocalDateTime.now().format(TIMESTAMP_FMT)
            );
            ValueRange body = new ValueRange().setValues(Collections.singletonList(row));

            AppendValuesResponse response = getService().spreadsheets().values()
                    .append(SPREADSHEET_ID, RANGE, body)
                    .setValueInputOption("USER_ENTERED")
                    .execute();

            System.out.println("Logged claim to Google Sheets: " + response.getUpdates().getUpdatedRange());
        } catch (Exception e) {
            System.err.println("Google Sheets sync failed (claim was still saved in MySQL): " + e.getMessage());
        }
    }
}
