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
			Files.delete( originalIndexPath );
		}
		catch( IOException ioe ) {
			throw new UncheckedIOException( "Failed to extract index.html", ioe );
		}

		fixLazyChunkLoadingPath( resDir );
	}

	/**
	 * <p>
	 * I hate this with the heat of a thousand suns, but I <i>really</i> want the
	 * report file structure to be like:
	 * </p>
	 *
	 * <pre>
	 * /report
	 *  ├ index.html
	 *  ├ /detail
	 *  │  ├ &lt;hash&gt;.html
	 *  │  ├ &lt;hash&gt;.html
	 *  │  ├ ...
	 *  │  └ &lt;hash&gt;.html
	 *  └ /res
	 *     ├ main.&lt;hash&gt;.js
	 *     ├ polyfills.&lt;hash&gt;.js
	 *     ├ &lt;whatever>.&lt;hash&gt;.js
	 *     ├ ...
	 *     └ styles.&lt;hash&gt;.css
	 * </pre>
	 * <p>
	 * This makes the report entrypoint super-obvious. Angular seems to have other
	 * ideas though, and all the stuff that I want in <code>/res</code> is just
	 * splatted into the root directory.
	 * </p>
	 * <p>
	 * We're working around this in two ways:
	 * </p>
	 * <ul>
	 * <li>Updating index.html to add <code>/res</code> to resource reference paths.
	 * That's what is happening in {@link Template#insert(Object, Path)}</li>
	 * <li>This method, which is dealing with lazy-loaded javascript chunks by
	 * updating <code>runtime.js</code> to add <code>/res</code>.</li>
	 * </ul>
	 * <p>
	 * <b>N.B.:</b> This second fix will work OK for the index page, but it will
	 * fail for the for detail pages, as we can apply the first fix dynamically in
	 * the detail pages, but they all share the same <code>runtime.js</code> file,
	 * and we can only fix the relative path once. For the moment we only need the
	 * runtime.js fix for mermaid, which is only used in the index page, so this
	 * isn't a problem yet.
	 * </p>
	 *
	 * @param resDir
	 */
	private static void fixLazyChunkLoadingPath( Path resDir ) {
		try( Stream<Path> files = Files.find( resDir, 1,
				( path, attr ) -> path.getFileName().toString().startsWith( "runtime." ) ) ) {
			Path runtime = files.findFirst()
					.orElse( null );
			if( runtime != null ) {
				String content = new String( Files.readAllBytes( runtime ), UTF_8 );
				String fixed = content.replaceAll( "(\\(\\d+===e\\?\"common\":e\\))", "\"res/\" + $1" );
				if( fixed.equals( content ) ) {
					throw new IllegalStateException( "Failed to fix chunk load path" );
				}
				Files.write( runtime, fixed.getBytes( UTF_8 ) );
			}
		}
		catch( IOException ioe ) {
			throw new UncheckedIOException( "Failed to update lazy chunk loading", ioe );
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
