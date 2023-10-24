package com.mastercard.test.flow.report.duct;

import java.awt.AWTException;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.net.URL;

import javax.imageio.ImageIO;
import javax.swing.JFileChooser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles the {@link Duct} graphical user interface - the systray icon and the
 * popup menu
 */
class Gui {

	private static final Logger LOG = LoggerFactory.getLogger( Gui.class );

	// https://brand.mastercard.com/brandcenter-ca/branding-requirements/mastercard.html
	private static final Color MC_RED = new Color( 235, 0, 27 );
	private static final Color MC_ORANGE = new Color( 255, 95, 0 );
	private static final Color MC_YELLOW = new Color( 247, 158, 27 );

	private final TrayIcon trayIcon;

	/**
	 * @param duct The {@link Duct} instance to control
	 */
	Gui( Duct duct ) {
		trayIcon = new TrayIcon(
				icon(),
				"Flow reports",
				menu( duct ) );

		// manually resize the image so we can use smooth scaling.
		// TrayIcon.setImageAutoSize does not produce good results
		Dimension d = trayIcon.getSize();
		trayIcon.setImage( trayIcon.getImage()
				.getScaledInstance( d.width, d.height, Image.SCALE_SMOOTH ) );
	}

	private static Image icon() {
		Image icon;
		try {
			icon = ImageIO.read( Duct.class.getClassLoader().getResource( "duct.png" ) );
		}
		catch( Exception e ) {
			LOG.error( "Failed to read icon", e );
			// fall back to a trivial mastercard-hued tricolour flag
			icon = new BufferedImage( 3, 3, BufferedImage.TYPE_INT_ARGB );
			Graphics g = icon.getGraphics();
			g.setColor( MC_RED );
			g.fillRect( 0, 0, 1, 3 );
			g.setColor( MC_ORANGE );
			g.fillRect( 1, 0, 1, 3 );
			g.setColor( MC_YELLOW );
			g.fillRect( 2, 0, 1, 3 );
		}
		return icon;
	}

	private static PopupMenu menu( Duct duct ) {
		PopupMenu menu = new PopupMenu();
		menu.add( index( duct ) );
		menu.add( add( duct ) );
		menu.add( content( duct ) );
		menu.addSeparator();
		menu.add( exit( duct ) );
		return menu;
	}

	private static MenuItem index( Duct duct ) {
		MenuItem index = new MenuItem( "Report index" );
		index.addActionListener( ev -> {
			try {
				Desktop.getDesktop().browse( new URI( "http://localhost:" + duct.port() ) );
			}
			catch( Exception e ) {
				LOG.error( "Failed to provoke browser", e );
			}
		} );
		return index;
	}

	private static MenuItem add( Duct duct ) {
		MenuItem add = new MenuItem( "Add report..." );
		add.addActionListener( ev -> {
			JFileChooser chooser = new JFileChooser();
			chooser.setFileSelectionMode( JFileChooser.DIRECTORIES_ONLY );

			int result = chooser.showDialog( null, "duct: Serve report" );
			if( result == JFileChooser.APPROVE_OPTION ) {
				URL serving = duct.add( chooser.getSelectedFile().toPath() );
				try {
					Desktop.getDesktop().browse( serving.toURI() );
				}
				catch( Exception e ) {
					LOG.error( "Failed to provoke browser", e );
				}
			}
		} );
		return add;
	}

	private static MenuItem content( Duct duct ) {
		MenuItem content = new MenuItem( "Manage content" );
		content.addActionListener( ev -> {
			try {
				Desktop.getDesktop().open( duct.servedDirectory().toFile() );
			}
			catch( IOException e ) {
				LOG.error( "Failed to browse content directory", e );
			}
		} );
		return content;
	}

	private static MenuItem exit( Duct duct ) {
		MenuItem exit = new MenuItem( "Exit" );
		exit.addActionListener( ev -> duct.stop() );
		return exit;
	}

	/**
	 * Adds the icon to the system tray
	 */
	void show() {
		try {
			SystemTray.getSystemTray().add( trayIcon );
		}
		catch( AWTException e ) {
			LOG.error( "Failed to add systray icon", e );
		}
	}

	/**
	 * Removes the icon from the system tray
	 */
	void hide() {
		SystemTray.getSystemTray().remove( trayIcon );
	}
}
