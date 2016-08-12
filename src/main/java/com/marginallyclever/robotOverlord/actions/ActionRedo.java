package com.marginallyclever.robotOverlord.actions;

import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;

import javax.swing.JMenuItem;
import javax.swing.KeyStroke;

import com.marginallyclever.robotOverlord.RobotOverlord;

/**
 * Load the world from a file
 * @author Admin
 *
 */
public class ActionRedo extends JMenuItem implements ActionListener {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	protected RobotOverlord ro;
	
	public ActionRedo(RobotOverlord ro) {
		super("Redo",KeyEvent.VK_Y);
		this.ro = ro;
		this.setAccelerator(KeyStroke.getKeyStroke('Y', Toolkit.getDefaultToolkit ().getMenuShortcutKeyMask()));
		addActionListener(this);
	}

	public void actionPerformed(ActionEvent e) {
		ro.redo();
	}
}
