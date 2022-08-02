package com.mastercard.test.flow.report;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Handles extracting a javascript app from resources file and populating it
 * with data
 */
class JsApp {

	private final Path originalIndexPath;
	private final Template indexTemplate;

	/**
	 * Initialises the application, extracting the index {@link Template} and
	 * putting all other resources in a directory
	 *
	 * @param name   The name of the application resource directory. This is assumed
	 *               to contain a "manifest.txt" file that lists the jsApp file
	 *               paths relative to itself. The manifest is assumed to list an
	 *               "index.html" file
	 * @param resDir Where to put all non-html files
	 */
	public JsApp( String name, Path resDir ) {
		String manifest = name + "/manifest.txt";
		try( BufferedReader br = new BufferedReader( new InputStreamReader(
				getClass().getResourceAsStream( manifest ) ) ) ) {
			br.lines()
					.map( String::trim )
					.filter( l -> !l.isEmpty() && !l.startsWith( "#" ) )
					.forEach( l -> {
						Path dest = resDir.resolve( l );
						QuietFiles.createDirectories( dest.getParent() );
						String resName = name + "/" + l;
						try( InputStream in = getClass().getResourceAsStream( resName );
								FileOutputStream out = new FileOutputStream( dest.toFile() ) ) {
							copy( in, out );
						}
						catch( NullPointerException | IOException e ) {
							throw new IllegalStateException( "Failed to copy resource " + resName, e );
						}
					} );
		}
		catch( NullPointerException | IOException e ) {
			throw new IllegalStateException( "Failed to extract " + manifest, e );
		}

		try( Stream<Path> files = Files.find( resDir, 10,
				( path, attr ) -> path.endsWith( "index.html" ) ) ) {
			originalIndexPath = files.findAny()
					.orElseThrow(
							() -> new IllegalStateException( "Failed to find index.html in " + resDir ) );
			indexTemplate = new Template( originalIndexPath );
		}
		catch( IOException ioe ) {
			throw new UncheckedIOException( "Failed to extract index.html", ioe );
		}
	}

	/**
	 * Writes an instance of this application to disk
	 *
	 * @param payload     The data to insert into the index {@link Template}
	 * @param destination Where to write the populated index file to
	 * @return <code>this</code>
	 */
	public JsApp write( Object payload, Path destination ) {
		QuietFiles.createDirectories( destination.getParent() );
		QuietFiles.write( destination, indexTemplate.insert(
				payload,
				destination.getParent().relativize( originalIndexPath.getParent() ) )
				.getBytes( UTF_8 ) );
		return this;
	}

	private static void copy( InputStream in, FileOutputStream out ) throws IOException {
		byte[] buff = new byte[10240];
		int read = 0;
		while( (read = in.read( buff )) != -1 ) {
			out.write( buff, 0, read );
		}
	}
}
