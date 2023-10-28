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
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.prefs.Preferences;

import javax.imageio.ImageIO;
import javax.swing.JFileChooser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mastercard.test.flow.report.Browse;

/**
 * Handles the {@link Duct} graphical user interface - the systray icon and the
 * popup menu
 */
class SystrayGui implements Gui {

	private static final String SEARCH_PATH_PREF = "search_path";

	private static final Logger LOG = LoggerFactory.getLogger( SystrayGui.class );

	// https://brand.mastercard.com/brandcenter-ca/branding-requirements/mastercard.html
	private static final Color MC_RED = new Color( 235, 0, 27 );
	private static final Color MC_ORANGE = new Color( 255, 95, 0 );
	private static final Color MC_YELLOW = new Color( 247, 158, 27 );

	private final TrayIcon trayIcon;

	private static final Preferences prefs = Preferences.userNodeForPackage( SystrayGui.class );

	/**
	 * @param duct The {@link Duct} instance to control
	 */
	SystrayGui( Duct duct ) {
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
		menu.add( clearIndex( duct ) );
		menu.add( log() );
		menu.addSeparator();
		menu.add( exit( duct ) );
		return menu;
	}

	private static MenuItem index( Duct duct ) {
		MenuItem index = new MenuItem( "Index" );
		index.addActionListener( ev -> Browse.WITH_AWT.to(
				"http://localhost:" + duct.port(), LOG::error ) );
		return index;
	}

	private static MenuItem add( Duct duct ) {
		MenuItem add = new MenuItem( "Add..." );
		add.addActionListener( ev -> {
			File start = null;
			try {
				start = new File( prefs.get( SEARCH_PATH_PREF, System.getProperty( "user.home" ) ) );
			}
			catch( Exception e ) {
				LOG.warn( "Failed to restore search directory", e );
			}

			JFileChooser chooser = new JFileChooser( start );
			chooser.setFileSelectionMode( JFileChooser.DIRECTORIES_ONLY );

			int result = chooser.showDialog( null, "Find reports" );
			if( result == JFileChooser.APPROVE_OPTION ) {
				Path path = chooser.getSelectedFile().toPath();
				prefs.put( SEARCH_PATH_PREF, path.toAbsolutePath().toString() );

				Search.find( path )
						.map( duct::add )
						.forEach( url -> Browse.WITH_AWT.to( url, LOG::error ) );
			}
		} );
		return add;
	}

	private static MenuItem clearIndex( Duct duct ) {
		MenuItem item = new MenuItem( "Clear" );
		item.addActionListener( ev -> {
			duct.clearIndex();
		} );
		return item;
	}

	private static MenuItem log() {
		MenuItem item = new MenuItem( "Logs" );
		item.addActionListener(
				ev -> {
					try {
						Desktop.getDesktop().open( Duct.INDEX_DIRECTORY.toFile() );
					}
					catch( IOException e ) {
						LOG.error( "Failed to open log file", e );
					}
				} );
		return item;
	}

	private static MenuItem exit( Duct duct ) {
		MenuItem item = new MenuItem( "Exit" );
		item.addActionListener( ev -> duct.stop() );
		return item;
	}

	@Override
	public void show() {
		LOG.info( "Showing gui" );
		try {
			SystemTray.getSystemTray().add( trayIcon );
		}
		catch( AWTException e ) {
			LOG.error( "Failed to add systray icon", e );
		}
	}

	@Override
	public void hide() {
		LOG.info( "hiding gui" );
		SystemTray.getSystemTray().remove( trayIcon );
	}
}
