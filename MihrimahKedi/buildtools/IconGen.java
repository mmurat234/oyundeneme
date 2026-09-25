import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** Uygulama ikonunu (turuncu yavru kedi Pamuk) PNG olarak çizer. */
public class IconGen {
    public static void main(String[] a) throws Exception {
        String outDir = a[0];
        int[][] sizes = {{48, 0}, {72, 1}, {96, 2}, {144, 3}, {192, 4}};
        String[] names = {"mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"};
        for (int[] s : sizes) {
            BufferedImage img = draw(s[0]);
            File d = new File(outDir, "mipmap-" + names[s[1]]);
            d.mkdirs();
            ImageIO.write(img, "png", new File(d, "ic_launcher.png"));
        }
    }

    static BufferedImage draw(int n) {
        BufferedImage img = new BufferedImage(n, n, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        float u = n / 100f;
        g.setPaint(new GradientPaint(0, 0, new Color(0xFF9EC8), 0, n, new Color(0xFF6FA8)));
        g.fill(new Ellipse2D.Float(2 * u, 2 * u, 96 * u, 96 * u));
        Color fur = new Color(0xF4A340), dark = new Color(0xD9822B), light = new Color(0xFFF3E3);
        // Kulaklar
        g.setColor(fur);
        g.fill(tri(22 * u, 45 * u, 26 * u, 12 * u, 46 * u, 30 * u));
        g.fill(tri(78 * u, 45 * u, 74 * u, 12 * u, 54 * u, 30 * u));
        g.setColor(new Color(0xFFB3C6));
        g.fill(tri(28 * u, 38 * u, 30 * u, 20 * u, 41 * u, 30 * u));
        g.fill(tri(72 * u, 38 * u, 70 * u, 20 * u, 59 * u, 30 * u));
        // Kafa
        g.setColor(fur);
        g.fill(new Ellipse2D.Float(18 * u, 22 * u, 64 * u, 60 * u));
        g.setColor(light);
        g.fill(new Ellipse2D.Float(32 * u, 55 * u, 36 * u, 24 * u));
        g.setColor(dark);
        g.setStroke(new BasicStroke(3.5f * u, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Float(50 * u, 24 * u, 50 * u, 36 * u));
        g.draw(new Line2D.Float(41 * u, 26 * u, 43 * u, 35 * u));
        g.draw(new Line2D.Float(59 * u, 26 * u, 57 * u, 35 * u));
        // Gözler
        g.setColor(new Color(0x5BC26A));
        g.fill(new Ellipse2D.Float(31 * u, 44 * u, 14 * u, 14 * u));
        g.fill(new Ellipse2D.Float(55 * u, 44 * u, 14 * u, 14 * u));
        g.setColor(new Color(0x1E1E1E));
        g.fill(new Ellipse2D.Float(35.5f * u, 44.5f * u, 5 * u, 13 * u));
        g.fill(new Ellipse2D.Float(59.5f * u, 44.5f * u, 5 * u, 13 * u));
        g.setColor(Color.WHITE);
        g.fill(new Ellipse2D.Float(37 * u, 46 * u, 3 * u, 3 * u));
        g.fill(new Ellipse2D.Float(61 * u, 46 * u, 3 * u, 3 * u));
        // Burun ve ağız
        g.setColor(new Color(0xFF7FA0));
        g.fill(tri(45 * u, 60 * u, 55 * u, 60 * u, 50 * u, 66 * u));
        g.setColor(new Color(0x3A2A20));
        g.setStroke(new BasicStroke(2f * u, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Arc2D.Float(42 * u, 62 * u, 8 * u, 7 * u, 180, 180, Arc2D.OPEN));
        g.draw(new Arc2D.Float(50 * u, 62 * u, 8 * u, 7 * u, 180, 180, Arc2D.OPEN));
        g.setStroke(new BasicStroke(1.3f * u, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Float(38 * u, 63 * u, 14 * u, 58 * u));
        g.draw(new Line2D.Float(38 * u, 66 * u, 14 * u, 68 * u));
        g.draw(new Line2D.Float(62 * u, 63 * u, 86 * u, 58 * u));
        g.draw(new Line2D.Float(62 * u, 66 * u, 86 * u, 68 * u));
        // Pembe fiyonk (Mihrimah'ın hediyesi)
        g.setColor(new Color(0xFF3D8B));
        g.fill(tri(70 * u, 20 * u, 60 * u, 12 * u, 60 * u, 28 * u));
        g.fill(tri(70 * u, 20 * u, 80 * u, 12 * u, 80 * u, 28 * u));
        g.fill(new Ellipse2D.Float(66.5f * u, 16.5f * u, 7 * u, 7 * u));
        g.dispose();
        return img;
    }

    static Shape tri(float x1, float y1, float x2, float y2, float x3, float y3) {
        Path2D.Float p = new Path2D.Float();
        p.moveTo(x1, y1); p.lineTo(x2, y2); p.lineTo(x3, y3); p.closePath();
        return p;
    }
}
