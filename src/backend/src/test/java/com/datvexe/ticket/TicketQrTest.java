package com.datvexe.ticket;
import com.google.zxing.*;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.RGBLuminanceSource;
import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class TicketQrTest {
    @Test void pngDecodesToTheExactOpaqueToken() throws Exception {
        String token = "a8f91e97-ee72-4e20-8604-f957fc278577";
        var image = ImageIO.read(new ByteArrayInputStream(TicketQr.png(token)));
        int[] pixels = image.getRGB(0,0,280,280,null,0,280);
        var bitmap = new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(280,280,pixels)));
        assertEquals(token,new MultiFormatReader().decode(bitmap).getText());
        assertTrue(TicketQr.dataUri(token).startsWith("data:image/png;base64,"));
        assertThrows(IllegalArgumentException.class,() -> TicketQr.png(""));
    }
}
