package Client;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JTextField;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public final class UiTheme {
    public static final Color BG_TOP = new Color(0x0B, 0x2B, 0x5B);
    public static final Color BG_BOTTOM = new Color(0x1E, 0x88, 0xE5);

    public static final Color TEXT_PRIMARY = new Color(0xF5, 0xF5, 0xF5);
    public static final Color TEXT_DARK = new Color(0x0A, 0x19, 0x2F);

    public static final Color ORANGE = new Color(0xFF, 0x8C, 0x42);
    public static final Color ORANGE_STRONG = new Color(0xFF, 0x6B, 0x35);
    public static final Color ORANGE_HOVER = new Color(0xFF, 0x57, 0x22);

    public static final Color PANEL_BG = new Color(25, 55, 90, 178);
    public static final Color GLASS_BG = new Color(255, 255, 255, 28);
    public static final Color GLASS_BORDER = new Color(255, 255, 255, 110);
    public static final Color LIGHT_BLUE = new Color(0xE0, 0xF2, 0xFE);

    private UiTheme() {
    }

    public static void installGlobalLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // Si no se puede aplicar, seguimos con el look por defecto.
        }
    }

    public static Font titleFont() {
        return new Font("Poppins", Font.BOLD, 30);
    }

    public static Font subtitleFont() {
        return new Font("Poppins", Font.BOLD, 18);
    }

    public static Font bodyFont() {
        return new Font("Poppins", Font.PLAIN, 16);
    }

    public static Font infoFont() {
        return new Font("Poppins", Font.PLAIN, 13);
    }

    public static Font codeFont() {
        return new Font("Fira Code", Font.PLAIN, 13);
    }

    public static void styleInput(JTextField field) {
        field.setFont(new Font("Poppins", Font.BOLD, 13));
        field.setForeground(TEXT_DARK);
        field.setBackground(new Color(255, 255, 255, 235));
        field.setBorder(compoundRoundBorder(new Color(255, 255, 255, 180), 8, 12));
        field.setCaretColor(TEXT_DARK);
    }

    public static void styleButton(JButton button) {
        button.setFont(new Font("Poppins", Font.BOLD, 13));
        button.setForeground(TEXT_DARK);
        button.setBackground(ORANGE_STRONG);
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.setBorderPainted(true);
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setBorder(compoundRoundBorder(new Color(255, 179, 71), 8, 16));
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (button.isEnabled()) {
                    button.setBackground(ORANGE_HOVER);
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (button.isEnabled()) {
                    button.setBackground(ORANGE_STRONG);
                }
            }
        });
    }

    public static void styleTitleBorder(JComponent component, String title) {
        component.setBorder(
                BorderFactory.createTitledBorder(
                        BorderFactory.createLineBorder(new Color(255, 179, 71, 180), 1, true),
                        title,
                        0,
                        0,
                        subtitleFont(),
                        TEXT_PRIMARY
                )
        );
    }

    private static Border compoundRoundBorder(Color color, int vPad, int hPad) {
        return new CompoundBorder(new LineBorder(color, 1, true), new EmptyBorder(vPad, hPad, vPad, hPad));
    }
}


