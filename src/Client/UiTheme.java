package Client;

import javax.swing.BorderFactory;
import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JTextField;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.Color;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.AlphaComposite;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.plaf.basic.BasicGraphicsUtils;

public final class UiTheme {
    public static final Color BG_TOP = new Color(0x0B, 0x2B, 0x5B);
    public static final Color BG_BOTTOM = new Color(0x1E, 0x88, 0xE5);

    public static final Color BRAND_BLUE = new Color(0x1E, 0x88, 0xE5);

    public static final Color TEXT_PRIMARY = new Color(0xF5, 0xF5, 0xF5);
    public static final Color TEXT_DARK = new Color(0x0A, 0x19, 0x2F);
    public static final Color TEXT_MUTED = new Color(210, 220, 236);

    public static final Color ORANGE = new Color(0xFF, 0x8C, 0x42);
    public static final Color ORANGE_STRONG = new Color(0xFF, 0x6B, 0x35);
    public static final Color ORANGE_HOVER = new Color(0xFF, 0x57, 0x22);

    public static final Color CONSOLE_BG = new Color(0x0F, 0x17, 0x2A);
    public static final Color CONSOLE_TEXT = new Color(0xA5, 0xF3, 0xFF);
    public static final Color TIMESTAMP = new Color(165, 165, 165);

    public static final Color PANEL_BG = new Color(25, 55, 90, 178);
    public static final Color GLASS_BG = new Color(255, 255, 255, 28);
    public static final Color GLASS_BORDER = new Color(255, 255, 255, 110);
    public static final Color LIGHT_BLUE = new Color(0xE0, 0xF2, 0xFE);

    private UiTheme() {
    }

    /**
     * Fuerza a que el texto del botón sea legible incluso en estado deshabilitado.
     * Algunos Look&Feel (Windows) bajan demasiado el contraste del disabled text.
     */
    private static final class AlwaysForegroundButtonUI extends BasicButtonUI {
        @Override
        public void update(Graphics g, JComponent c) {
            // Limpia el área del botón para evitar “texto fantasma” al repintar con alpha.
            if (c.isOpaque()) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setComposite(AlphaComposite.Src);
                g2.setColor(c.getBackground());
                g2.fillRect(0, 0, c.getWidth(), c.getHeight());
                g2.dispose();
            }
            paint(g, c);
        }

        @Override
        protected void paintText(Graphics g, JComponent c, Rectangle textRect, String text) {
            AbstractButton b = (AbstractButton) c;
            FontMetrics fm = g.getFontMetrics();
            int mnemonicIndex = b.getDisplayedMnemonicIndex();
            g.setColor(b.getForeground());
            BasicGraphicsUtils.drawStringUnderlineCharAt(
                    g,
                    text,
                    mnemonicIndex,
                    textRect.x,
                    textRect.y + fm.getAscent()
            );
        }
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
        return new Font("Fira Code", Font.PLAIN, 14);
    }

    public static void styleInput(JTextField field) {
        // Inputs claros (si el fondo se ve blanco, el texto NO puede ser blanco)
        field.setFont(new Font("Poppins", Font.BOLD, 13));
        field.setForeground(TEXT_DARK);
        field.setBackground(new Color(255, 255, 255, 235));
        field.setOpaque(true);
        field.setBorder(compoundRoundBorder(new Color(255, 255, 255, 200), 12, 20));
        field.setCaretColor(TEXT_DARK);
        field.setSelectionColor(new Color(0x1E, 0x88, 0xE5, 80));
        field.setSelectedTextColor(TEXT_DARK);
        field.setDisabledTextColor(TEXT_DARK);
        applyInputState(field);
        field.addPropertyChangeListener("enabled", _e -> applyInputState(field));
    }

    private static void repaintParent(JComponent component) {
        Container parent = component.getParent();
        if (parent == null) {
            component.repaint();
            return;
        }
        Rectangle bounds = component.getBounds();
        int margin = 10;
        int x = Math.max(0, bounds.x - margin);
        int y = Math.max(0, bounds.y - margin);
        int w = Math.min(parent.getWidth() - x, bounds.width + margin * 2);
        int h = Math.min(parent.getHeight() - y, bounds.height + margin * 2);
        parent.repaint(x, y, w, h);
    }

    private static void applyInputState(JTextField field) {
        // Fuerza colores incluso cuando se deshabilita (Windows LAF a veces los cambia).
        field.setOpaque(true);
        field.setForeground(TEXT_DARK);
        field.setDisabledTextColor(TEXT_DARK);
        field.setBackground(new Color(255, 255, 255, field.isEnabled() ? 235 : 210));
        field.setCaretColor(TEXT_DARK);
        field.repaint();
        repaintParent(field);
    }

    public static void styleCommandInput(JTextField field) {
        field.setFont(new Font("Poppins", Font.PLAIN, 13));
        field.setForeground(TEXT_PRIMARY);
        field.setBackground(new Color(20, 36, 58));
        field.setOpaque(true);
        field.setBorder(compoundRoundBorder(new Color(255, 255, 255, 140), 12, 20));
        field.setCaretColor(TEXT_PRIMARY);
        field.setSelectionColor(new Color(0xFF, 0x8C, 0x42, 90));
        field.setSelectedTextColor(TEXT_PRIMARY);
    }

    public static void stylePrimaryButton(JButton button) {
        // Evita que el Look&Feel (especialmente en Windows) “apague” el texto al deshabilitar.
        button.setUI(new AlwaysForegroundButtonUI());
        button.setFont(new Font("Poppins", Font.BOLD, 13));
        button.setForeground(TEXT_PRIMARY);
        button.setBackground(ORANGE_STRONG);
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.setBorderPainted(true);
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setBorder(compoundRoundBorder(new Color(255, 179, 71), 10, 18));
        applyPrimaryState(button);
        button.addPropertyChangeListener("enabled", _e -> applyPrimaryState(button));
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (button.isEnabled()) {
                    button.setBackground(ORANGE_HOVER);
                    button.setBorder(compoundRoundBorder(new Color(255, 220, 170), 10, 18));
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (button.isEnabled()) {
                    button.setBackground(ORANGE_STRONG);
                    button.setBorder(compoundRoundBorder(new Color(255, 179, 71), 10, 18));
                }
            }
        });
    }

    private static void applyPrimaryState(JButton button) {
        if (button.isEnabled()) {
            button.setForeground(TEXT_PRIMARY);
            button.setBackground(ORANGE_STRONG);
            button.setBorder(compoundRoundBorder(new Color(255, 179, 71), 10, 18));
        } else {
            // Mantener legible: mismo layout, pero con menos fuerza visual.
            button.setForeground(new Color(245, 245, 245, 210));
            button.setBackground(new Color(255, 140, 66, 140));
            button.setBorder(compoundRoundBorder(new Color(255, 179, 71, 120), 10, 18));
        }
        button.repaint();
        repaintParent(button);
    }

    public static void styleSecondaryButton(JButton button) {
        button.setUI(new AlwaysForegroundButtonUI());
        button.setFont(new Font("Poppins", Font.BOLD, 13));
        button.setForeground(ORANGE);
        button.setBackground(new Color(0, 0, 0, 0));
        // OPAQUE=true para que update() limpie el buffer del botón.
        button.setOpaque(true);
        button.setContentAreaFilled(false);
        button.setBorderPainted(true);
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setBorder(compoundRoundBorder(new Color(255, 140, 66, 200), 10, 18));
        applySecondaryState(button);
        button.addPropertyChangeListener("enabled", _e -> applySecondaryState(button));
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (button.isEnabled()) {
                    button.setForeground(TEXT_PRIMARY);
                    button.setOpaque(true);
                    button.setBackground(new Color(255, 140, 66, 40));
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (button.isEnabled()) {
                    button.setForeground(ORANGE);
                    button.setOpaque(false);
                    button.setBackground(new Color(0, 0, 0, 0));
                }
            }
        });
    }

    private static void applySecondaryState(JButton button) {
        if (button.isEnabled()) {
            button.setForeground(ORANGE);
            button.setOpaque(true);
            button.setBackground(new Color(0, 0, 0, 0));
            button.setBorder(compoundRoundBorder(new Color(255, 140, 66, 200), 10, 18));
        } else {
            button.setForeground(new Color(255, 140, 66, 160));
            button.setOpaque(true);
            button.setBackground(new Color(0, 0, 0, 0));
            button.setBorder(compoundRoundBorder(new Color(255, 140, 66, 120), 10, 18));
        }
        button.repaint();
        repaintParent(button);
    }

    // Compat: deja el nombre viejo disponible para no romper llamadas existentes.
    public static void styleButton(JButton button) {
        stylePrimaryButton(button);
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


