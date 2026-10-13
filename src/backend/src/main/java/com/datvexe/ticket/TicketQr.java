package com.datvexe.ticket;
import com.google.zxing.*;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Map;
import javax.imageio.ImageIO;

/** Encodes the server-issued opaque token locally, without sending it to another service. */
public final class TicketQr {
    private TicketQr() {}
    public static byte[] png(String token) {
        if (token == null || token.isBlank()) throw new IllegalArgumentException("Ticket QR token is required");
        try {
            var matrix = new QRCodeWriter().encode(token, BarcodeFormat.QR_CODE, 280, 280,
                Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M, EncodeHintType.MARGIN, 4));
            var image = new BufferedImage(280, 280, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < 280; y++) for (int x = 0; x < 280; x++) image.setRGB(x,y,matrix.get(x,y)?0x000000:0xffffff);
            var output = new ByteArrayOutputStream();
            ImageIO.write(image,"png",output);
            return output.toByteArray();
        } catch (Exception e) { throw new IllegalStateException("Unable to generate ticket QR",e); }
    }
    public static String base64(String token) { return Base64.getEncoder().encodeToString(png(token)); }
    public static String dataUri(String token) { return "data:image/png;base64," + base64(token); }
}
