package laundrysystem;

import com.google.zxing.*;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;

/**
 * Generates and decodes QR codes using ZXing ("Zebra Crossing").
 *
 * These are standard ISO/IEC 18004 QR codes -- any generic scanner app
 * (phone camera, Google Lens, a dedicated barcode app) can read them, not
 * just this application. The error-correction level and quiet-zone margin
 * below are set explicitly (rather than left as library defaults) because
 * some third-party scanner apps are stricter about a proper quiet zone
 * than others -- this keeps codes readable everywhere.
 *
 * Requires zxing-core and zxing-javase on the classpath (see the QR_Claim_SYSTEM_GUIDE.md
 * 
 */
public class QRCodeUtil {

    private static final int QR_SIZE = 300; // pixels, square

    /**
     * Generates a fresh random claim token. This is stored as-is in MySQL
     * (claim_token column) and embedded as a query parameter in the URL
     * that actually gets put into the QR code -- see StatusServer.buildStatusUrl.
     */
    public static String newClaimToken() {
        return UUID.randomUUID().toString();
    }

    /**
     * QR codes now encode a full status-page URL (e.g.
     * "https://localhost:8080","http://192.168.1.50:8080/status?token=..."), not a bare token, so
     * that scanning with any generic camera/scanner app offers to open it
     * as a link. This pulls the token back out of that URL for the staff
     * Claim tab, which still just needs the raw token to look the order up.
     * Falls back to treating the whole scanned string as the token, for
     * backwards compatibility with any QR codes generated before this URL
     * format was introduced.
     */
    public static String extractTokenFromScannedContent(String scanned) {
        if (scanned == null) return null;
        int queryStart = scanned.indexOf('?');
        if (queryStart != -1) {
            String query = scanned.substring(queryStart + 1);
            for (String pair : query.split("&")) {
                String[] kv = pair.split("=", 2);
                if (kv.length == 2 && kv[0].equals("token")) {
                    return java.net.URLDecoder.decode(kv[1], java.nio.charset.StandardCharsets.UTF_8);
                }
            }
        }
        return scanned; // not a URL we recognize -- assume it's already a bare token
    }

    /**
     * Renders the given text (typically a claim token) as a QR code image,
     * using explicit error-correction and margin settings for reliable
     * scanning across all standard QR reader apps.
     */
    public static BufferedImage generate(String text) throws WriterException {
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
        hints.put(EncodeHintType.MARGIN, 2); // quiet zone, in modules

        QRCodeWriter writer = new QRCodeWriter();
        BitMatrix matrix = writer.encode(text, BarcodeFormat.QR_CODE, QR_SIZE, QR_SIZE, hints);

        BufferedImage image = new BufferedImage(QR_SIZE, QR_SIZE, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < QR_SIZE; x++) {
            for (int y = 0; y < QR_SIZE; y++) {
                image.setRGB(x, y, matrix.get(x, y) ? 0x000000 : 0xFFFFFF);
            }
        }
        return image;
    }

    /** Convenience: generate and save straight to a PNG file, e.g. for printing. */
    public static void generateToFile(String text, File outputFile) throws WriterException, IOException {
        BufferedImage image = generate(text);
        ImageIO.write(image, "PNG", outputFile);
    }

    /**
     * Decodes a QR code from an image file. Returns the encoded text,
     * or null if no QR code was found in the image.
     */
    public static String decode(File imageFile) throws IOException {
        BufferedImage bufferedImage = ImageIO.read(imageFile);
        if (bufferedImage == null) {
            return null; // not a readable image
        }
        BufferedImageLuminanceSource source = new BufferedImageLuminanceSource(bufferedImage);
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
        try {
            Result result = new MultiFormatReader().decode(bitmap);
            return result.getText();
        } catch (NotFoundException e) {
            return null; // no QR code found
        }
    }
}

