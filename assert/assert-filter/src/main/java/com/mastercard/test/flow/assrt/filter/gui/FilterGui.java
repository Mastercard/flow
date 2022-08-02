package com.mastercard.test.flow.assrt.filter.gui;

import static java.awt.GridBagConstraints.BOTH;
import static java.awt.event.WindowEvent.WINDOW_CLOSING;

import java.awt.BorderLayout;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.border.TitledBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import com.mastercard.test.flow.assrt.filter.Filter;
import com.mastercard.test.flow.assrt.filter.FilterOptions;

/**
 * Shows a graphical interface to allow the filters to be updated before we run
 * the tests. Crucially, we get the chance to choose tags before transactions
 * are built - this can improve performance by avoiding building flows that will
 * not be exercised.
 */
public class FilterGui extends JFrame {

	private static final long serialVersionUID = 1L;

	/**
	 * @return <code>true</code> if the gui should be invoked
	 */
	public static boolean requested() {
		return "gui".equals( FilterOptions.FILTER_UPDATE.value() )
				|| FilterOptions.FILTER_UPDATE.isTrue()
						&& !GraphicsEnvironment.isHeadless();
	}

	private final transient Filter filter;

	/**
	 * Builds a new interface
	 *
	 * @param filter The filter to control
	 */
	public FilterGui( Filter filter ) {
		super( "Flow filters" );
		this.filter = filter;
	}

	/**
	 * Shows the interface. This method will block until the user has configured the
	 * {@link Filter} to their liking
	 */
	public void blockForInput() {
		final AtomicBoolean updatesCompleted = new AtomicBoolean( false );
		final Object monitor = new Object();

		SwingUtilities.invokeLater( () -> {
			setDefaultCloseOperation( WindowConstants.DISPOSE_ON_CLOSE );
			addWindowListener( new WindowAdapter() {
				@Override
				public void windowClosed( WindowEvent e ) {
					updatesCompleted.set( true );
					synchronized( monitor ) {
						monitor.notifyAll();
					}
				}
			} );
			JButton build = new JButton( "Build" );
			build.setName( "build_button" );
			JButton run = new JButton( "Run" );
			run.setName( "run_button" );
			run.addActionListener( ac -> dispatchEvent( new WindowEvent( this, WINDOW_CLOSING ) ) );
			getContentPane().add( buildGUI( build, run ) );
			pack();
			setLocationRelativeTo( null );
			getRootPane().setDefaultButton( build );
			setVisible( true );
		} );

		synchronized( monitor ) {
			while( !updatesCompleted.get() ) {
				try {
					monitor.wait();
				}
				catch( InterruptedException e ) {
					e.printStackTrace();
					Thread.currentThread().interrupt();
				}
			}
		}
	}

	private JPanel buildGUI( JButton build, JButton run ) {
		run.setEnabled( false );
		JTextField filterField = new JTextField();
		filterField.setName( "filter_textfield" );
		filterField.setHorizontalAlignment( SwingConstants.CENTER );

		TagPanel tagPanel = new TagPanel( filter );
		FlowPanel flowPanel = new FlowPanel( filter );

		JPanel buildPanel = titled( "Flows", build );

		JPanel panel = new JPanel( new GridBagLayout() );
		panel.add( titled( "Filter", filterField ),
				new GBCB()
						.fill( BOTH )
						.gridx( 0 ).gridy( 0 )
						.gridwidth( 3 ).gridheight( 1 )
						.weightx( 1 ).weighty( 0 )
						.get() );

		panel.add( tagPanel,
				new GBCB()
						.fill( BOTH )
						.gridx( 0 ).gridy( 1 )
						.gridwidth( 1 ).gridheight( 1 )
						.weightx( 1 ).weighty( 1 )
						.get() );

		panel.add( buildPanel,
				new GBCB()
						.fill( BOTH )
						.gridx( 1 ).gridy( 1 )
						.gridwidth( 1 ).gridheight( 1 )
						.weightx( 0 ).weighty( 1 )
						.get() );

		panel.add( run,
				new GBCB()
						.fill( BOTH )
						.gridx( 2 ).gridy( 1 )
						.gridwidth( 1 ).gridheight( 1 )
						.weightx( 0 ).weighty( 1 )
						.get() );

		AtomicBoolean flowBuilding = new AtomicBoolean( false );
		build.addActionListener( ac -> {
			flowBuilding.set( true );
			flowPanel.withListener( tagPanel::refreshAndLimitTags );
			run.setEnabled( true );
			SwingUtilities.getRootPane( run ).setDefaultButton( run );

			panel.remove( buildPanel );
			panel.add( flowPanel,
					new GBCB()
							.fill( BOTH )
							.gridx( 1 ).gridy( 1 )
							.gridwidth( 1 ).gridheight( 1 )
							.weightx( 3 ).weighty( 1 )
							.get() );

			SwingUtilities.windowForComponent( panel ).pack();
			flowPanel.refresh();
			tagPanel.withListener( flowPanel::refresh );
		} );

		filterField.getDocument().addDocumentListener( new DocumentListener() {

			@Override
			public void removeUpdate( DocumentEvent e ) {
				changedUpdate( e );
			}

			@Override
			public void insertUpdate( DocumentEvent e ) {
				changedUpdate( e );
			}

			@Override
			public void changedUpdate( DocumentEvent e ) {
				String ft = filterField.getText();

				Pattern p;
				try {
					p = Pattern.compile( ft );
				}
				catch( @SuppressWarnings("unused") PatternSyntaxException pse ) {
					p = Pattern.compile( Pattern.quote( ft ) );
				}

				tagPanel.filter( p );
				if( flowBuilding.get() ) {
					flowPanel.filter( p );
				}
			}
		} );

		return panel;
	}

	/**
	 * Places a component in a titled border
	 *
	 * @param title     The title
	 * @param component The component
	 * @return The wrapped component
	 */
	static JPanel titled( String title, JComponent component ) {
		JPanel p = new JPanel( new BorderLayout() );
		p.setBorder( new TitledBorder( title ) );
		p.add( component, BorderLayout.CENTER );
		return p;
	}
}
