package com.mastercard.test.flow.report;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import com.mastercard.test.flow.report.data.Index;

/**
 * Counts payload writes and parsed index entries at the actual filesystem edge.
 */
class CountingReportFiles extends ReportFiles {

	/** Number of complete index files written. */
	int indexWrites;
	/** Sum of membership counts across those index files. */
	int indexEntries;
	/** Number of complete detail files written, including corrections. */
	int detailWrites;
	/** Number of complete diagnostic companions written. */
	int diagnosticWrites;
	/** Bytes written in complete index files. */
	long indexBytes;
	/** Bytes written in complete detail files. */
	long detailBytes;

	@Override
	OutputStream open( Path path ) throws IOException {
		return new FilterOutputStream( super.open( path ) ) {
			@Override
			public void write( byte[] bytes, int offset, int length ) throws IOException {
				out.write( bytes, offset, length );
			}

			@Override
			public void close() throws IOException {
				super.close();
				if( path.getFileName().toString().equals( Writer.DIAGNOSTICS_FILE_NAME ) ) {
					diagnosticWrites++;
				}
				else if( path.getParent().getFileName().toString().equals( Writer.DETAIL_DIR_NAME ) ) {
					detailWrites++;
					detailBytes += Files.size( path );
				}
				else {
					indexWrites++;
					indexBytes += Files.size( path );
					indexEntries += Template.extract( Files.readString( path ), Index.class ).entries.size();
				}
			}
		};
	}
}
