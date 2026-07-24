# Adding a QR Code Claim System (MySQL + Google Sheets)

## What you're building
1. When an order is created, the app generates a QR code containing a random
   claim token (not the order ID — see Security notes).
2. That QR is shown/printed and given to the customer.
3. At pickup, staff click "Scan to Claim", load a photo of the QR (or use a
   webcam), the app decodes the token, looks it up in **MySQL**, marks the
   order `claimed = true`, and appends a row to a **Google Sheet** as a
   backup ledger.

```
New Order -> save to MySQL -> generate QR (token) -> show/print to customer
                                                             |
Pickup: staff scans QR -> decode token -> find order in MySQL
      -> mark claimed + timestamp -> append row to Google Sheet
```

---

## Part 1 — MySQL setup

1. Install MySQL Server (skip if already installed).
2. Create the database and table:

```sql
CREATE DATABASE laundry_system;
USE laundry_system;

CREATE TABLE orders (
    id             INT PRIMARY KEY AUTO_INCREMENT,
    customer_name  VARCHAR(100)   NOT NULL,
    service_type   VARCHAR(50)    NOT NULL,
    quantity       DECIMAL(6,2)   NOT NULL,
    price          DECIMAL(10,2)  NOT NULL,
    status         VARCHAR(30)    DEFAULT 'Pending',
    claim_token    VARCHAR(64)    UNIQUE NOT NULL,
    claimed        BOOLEAN        DEFAULT FALSE,
    claimed_at     DATETIME       NULL,
    created_at     DATETIME       DEFAULT CURRENT_TIMESTAMP
);
```

3. Create a dedicated app user — don't connect as root from the app:

```sql
CREATE USER 'laundry_app'@'localhost' IDENTIFIED BY 'change_this_password';
GRANT SELECT, INSERT, UPDATE ON laundry_system.* TO 'laundry_app'@'localhost';
FLUSH PRIVILEGES;
```

---

## Part 2 — Add dependencies to the NetBeans project

Managing 6+ JARs by hand (download → add to Libraries) works but is
tedious and easy to get wrong. **Recommended:** convert the project to
Maven so dependencies are just declared in `pom.xml` and NetBeans/Maven
downloads them for you.

In NetBeans: right-click the project → *nothing to convert in place*, so
the simplest path is: File → New Project → Maven → Java Application,
name it the same, then copy your existing `src/laundrysystem/*.java`
files into the new Maven project's `src/main/java/laundrysystem/`.
Use the `pom.xml` provided below (drop it in the project root, replacing
the generated one).

If you'd rather stay on the Ant-based project, download these JARs
manually and add them via Project Properties → Libraries → Add JAR:
- `mysql-connector-j-<version>.jar` — https://dev.mysql.com/downloads/connector/j/
- `core-<version>.jar` and `javase-<version>.jar` from ZXing — https://github.com/zxing/zxing
- `google-api-client`, `google-oauth-client-jetty`, `google-api-services-sheets`,
  plus their transitive Jackson/Guava jars — these are much easier to get
  right via Maven, since Sheets API alone pulls in ~10 transitive jars.

---

## Part 3 — Google Sheets setup (do this once)

1. Go to https://console.cloud.google.com → create or select a project.
2. APIs & Services → Library → search "Google Sheets API" → Enable.
3. APIs & Services → Credentials → Create Credentials → **Service Account**.
   Give it any name, no roles needed at the project level.
4. Open the service account → Keys → Add Key → JSON. This downloads a
   file — rename it `credentials.json` and put it in your project
   (e.g. `src/main/resources/credentials.json`). **Never commit this
   file to git or share it** — it's equivalent to a password.
5. Open the JSON file and copy the `client_email` value
   (looks like `something@your-project.iam.gserviceaccount.com`).
6. Create a Google Sheet for your claims log. Click **Share**, paste
   that service-account email in, give it **Editor** access.
7. Copy the Spreadsheet ID from the sheet's URL:
   `https://docs.google.com/spreadsheets/d/`**`THIS_PART`**`/edit`
8. In the sheet, add a header row on a tab named `Claims`:
   `Order ID | Customer | Service | Price | Claimed At`

---

## Part 4 — Files to add to your project

Four new Java classes (provided alongside this guide):

- **`DatabaseManager.java`** — JDBC connection + insert/find/claim queries
- **`QRCodeUtil.java`** — generate a QR image for a token, decode a QR
  from an image file
- **`GoogleSheetsSync.java`** — appends one row to your Claims sheet
- **`ClaimPanel.java`** — a `JPanel` with a "Load QR Image to Claim"
  button; wire it in as a 5th tab in `MainDashboard`

### Wiring into `MainDashboard.java`

In `buildTabs()`, add one line:

```java
jTabbedPane1.addTab("Claim", new ClaimPanel());
```

### Switching order creation to write to MySQL instead of memory

Right now `DataStore.addOrder(...)` just appends to an `ArrayList`. To
make claiming meaningful, orders need to exist in MySQL too. The
simplest change: inside `DataStore.addOrder(...)`, after building the
`Order` object, also call:

```java
String token = java.util.UUID.randomUUID().toString();
DatabaseManager.insertOrder(order.getId(), customer.getName(), serviceType, quantity, order.getPrice(), token);
```

and show/save the resulting QR (`QRCodeUtil.generate(token)`) in the
order-confirmation dialog so it can be printed or shown to the
customer.

---

## Part 5 — Testing checklist

- [ ] `DatabaseManager` can connect (check the console for connection errors)
- [ ] Creating an order inserts a row into MySQL `orders` table
- [ ] The QR image renders and looks scannable (test with your phone's camera app first — it should show a long token string)
- [ ] Loading that QR image back through `ClaimPanel` finds the right order
- [ ] Claiming an already-claimed order is rejected with a clear message
- [ ] A new row appears in the Google Sheet after a successful claim
- [ ] Sheet append failures (e.g. no internet) don't crash the app or block the MySQL claim — log the error and let the user retry the sheet sync separately if needed

---

## Security notes

- **Don't hardcode the MySQL password in source.** Put it in a
  `.gitignore`'d config file or read it from an environment variable.
- **Use a random UUID as the claim token**, not the order ID — if tokens
  were sequential/predictable, anyone could guess another customer's
  token and claim their order.
- **`credentials.json` is a secret.** Add it to `.gitignore`. If it ever
  leaks, revoke the key in Google Cloud Console and issue a new one.
- Consider rate-limiting or requiring staff login before the Claim tab
  is usable, so a customer with just the QR photo can't self-claim
  early.
