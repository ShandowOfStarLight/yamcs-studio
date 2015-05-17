package org.yamcs.studio.ui.archive;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.image.BufferedImage;
import java.util.TreeSet;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import org.yamcs.studio.ui.archive.ArchivePanel.IndexChunkSpec;
import org.yamcs.studio.ui.archive.IndexBox.IndexLineSpec;

class Timeline extends JPanel implements MouseListener {
    private static final long serialVersionUID = 1L;
    private static final Color BLUEISH = new Color(135, 206, 250);
    private final IndexBox tmBox;
    TreeSet<IndexChunkSpec> tmspec;
    IndexLineSpec pkt;
    ZoomSpec zoom;
    int leftDelta; //we have to move everything to the left with this amount (because this component is in a bordered parent)
    BufferedImage image = null;

    Timeline(IndexBox tmBox, IndexLineSpec pkt, TreeSet<IndexChunkSpec> tmspec, ZoomSpec zoom, int leftDelta) {
        super();
        setBorder(BorderFactory.createEmptyBorder());
        this.tmBox = tmBox;
        this.pkt = pkt;
        this.zoom = zoom;
        this.leftDelta = leftDelta;
        addMouseListener(this);
        setOpaque(false);

        this.tmspec = tmspec;
    }

    @Override
    public Point getToolTipLocation(MouseEvent e) {
        return tmBox.getToolTipLocation(e);
    }

    private MouseEvent translateEvent(MouseEvent e, Component dest) {
        // workaround for this bug
        //http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=7181403
        MouseEvent me = SwingUtilities.convertMouseEvent(e.getComponent(), e, dest);
        return new MouseEvent(me.getComponent(), me.getID(), me.getWhen(), me.getModifiers(), me.getX(), me.getY(), me.getXOnScreen(),
                me.getYOnScreen(), me.getClickCount(), me.isPopupTrigger(), e.getButton());
    }

    @Override
    public void mousePressed(MouseEvent e) {
        if (e.isPopupTrigger()) {
            tmBox.selectedPacket = pkt;
            tmBox.showPopup(translateEvent(e, tmBox));
        }
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        if (e.isPopupTrigger()) {
            tmBox.selectedPacket = pkt;
            tmBox.showPopup(translateEvent(e, tmBox));
        }
    }

    @Override
    public void mouseClicked(MouseEvent e) {
    }

    @Override
    public void mouseEntered(MouseEvent e) {
    }

    @Override
    public void mouseExited(MouseEvent e) {
    }

    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (image == null) {
            image = new BufferedImage(getWidth(), getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D big = image.createGraphics();
            big.setComposite(AlphaComposite.getInstance(AlphaComposite.CLEAR));
            big.fillRect(0, 0, getWidth(), getHeight());

            big.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER));
            //big.clearRect(0, 0, getWidth(),getHeight());
            big.setColor(BLUEISH);
            for (IndexChunkSpec pkt : tmspec) {
                int x1 = zoom.convertInstantToPixel(pkt.startInstant);
                int x2 = zoom.convertInstantToPixel(pkt.stopInstant);
                int width = (x2 - x1 <= 1) ? 1 : x2 - x1 - 1;
                big.fillRect(x1 - leftDelta, 0, width, getHeight());
            }
        }
        g.drawImage(image, 0, 0, this);
    }

}
