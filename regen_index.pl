use 5.014;
use strict;
use warnings;
use File::Copy;
use File::Path qw( remove_tree );
use POSIX;

# This script manages our archive of build artifacts and regenerates
# sections of the index.md file to link to them.
# New reports are assumed to be dropped into ./execution/ingest and ./mutation/ingest
# Anything found there will be moved into timestamp-named directories under ./execution and ./mutation
# Stable links to the latest reports will be maintained.
# Report retention can be configured in terms of:
#   * lifetime in days
#   * minimum number to retain
#   * maximum number to retain
# This script will emit markdown on stdout that contains links to newly-ingested
# content, suitable for adding to $GITHUB_STEP_SUMMARY
# (per https://github.blog/2022-05-09-supercharging-github-actions-with-job-summaries/)

my $branch_name = $ARGV[0];
my $base = "https://mastercard.github.io/flow";
my $now_ts = time;
my @dirs = qw( execution mutation ng_coverage );
my @markdown_lines = ();

push @markdown_lines, ingest_new_report( $_, $now_ts, $branch_name ) foreach @dirs;

purge_old_reports(
	now_ts => $now_ts,
	retention_days => 90,
	minimum_retention_count => 5,
	maximum_retention_count => 20,
	dir => $_ )	foreach @dirs;

link_latest( $_ ) foreach @dirs;

my %content = map { $_ => generate_table( $_ ) } @dirs;
regenerate_index( %content );

push @markdown_lines, "", "[Build artifact index]($base)";
say foreach @markdown_lines;

1;

sub ingest_new_report {
	my ( $dir, $now_ts, $branch_name ) = @_;
	my $src = "$dir/ingest";
	if( -e $src ) {
		save_branch_name( $src, $branch_name || 'main' );
		my $dst = "$dir/$now_ts";

		move( $src, $dst ) or die "move failed: $!";

		# scan the ingested report for index files to link
		my @index_paths = index_scan( $dst );
		my @names = strip_shared_path_elements( @index_paths );
		my @links = ();
		for( my $i = 0; $i < scalar @index_paths; $i++ ) {
			push @links, " * [$names[$i]]($base/$index_paths[$i])"
		}
		return @links;
	}

	return ();
}

sub save_branch_name {
	my ( $dir, $branch_name ) = @_;
	my $file = "$dir/branch_name.txt";
	open my $wh, '>', $file or die "Failed to open $file $!";
	print $wh $branch_name;
	close $wh;
}

sub load_branch_name {
	my ( $dir ) = @_;
	my $file = "$dir/branch_name.txt";
	if( -e $file ) {
		open my $rh, $file or die "Failed to open $file $!";
		my $branch_name = do { local $/ = undef; <$rh> };
		close $rh;
		return $branch_name;
	}
	return 'main';
}

sub index_scan {
	my ( $dir ) = @_;
	my @index_files = ();

	opendir( DIR, $dir ) or die "Failed to open $dir $@";
	my @ls = grep { $_ ne '.' && $_ ne '..' } readdir( DIR );
	closedir( DIR );

	my $index_found = 0;
	foreach my $path ( map { "$dir/$_" } @ls ) {
		if( -f $path && $path =~ m|/index.html$| ) {
			push @index_files, $path;
			$index_found = 1;
		}
	}

	# we only want the shallowest index in a tree - it is 
	# assumed that deeper ones will be linked to from there
	unless( $index_found ) {
		foreach my $subdir ( grep { -d } map { "$dir/$_" } @ls ) {
			push @index_files, index_scan( $subdir );
		}
	}

	return @index_files;
}

sub strip_shared_path_elements {
	my @strings = @_;

	my @paths = map { [ split '/', $_ ] } @strings;

	if( scalar @paths == 1 ) {
		return $paths[0]->[0];
	}

	while( shared_element( 0, @paths ) ) {
		for( my $i = 0; $i < scalar @paths; $i++ ){
			my @p = @{$paths[$i]};
			shift @p;
			$paths[$i] = [@p];
		}
	}  
	while( shared_element( -1, @paths ) ) {
		for( my $i = 0; $i < scalar @paths; $i++ ){
			my @p = @{$paths[$i]};
			pop @p;
			$paths[$i] = [@p];
		}
	}

	my @stripped = map { join '/', @$_ } @paths;
}

sub shared_element {
	my ( $offset, @paths ) = @_;
	my $element = $paths[0]->[$offset];
	foreach my $p ( @paths ) {
		if( $element ne $p->[$offset] ) {
			return 0;
		}
	}
	return 1;
}

sub purge_old_reports {
	my %args = @_;

	opendir( DIR, $args{dir} ) or die "Failed to open $args{dir}";
	my @reports = sort grep { m/^\d+$/ } readdir( DIR );
	closedir( DIR );

	my $purge_limit = $args{now_ts} - $args{retention_days} * 24 *60 *60;
	while( scalar @reports > $args{minimum_retention_count} ) {
		my $oldest = shift @reports;
		if( $oldest < $purge_limit || scalar @reports > ( $args{maximum_retention_count} -1 ) ) {
			remove_tree( "$args{dir}/$oldest" );
		}
		else {
			# the oldest report is still newer than the purge limit. We're done here.
			last;
		}
	}
}

sub generate_table {
	my ( $dir ) = @_;
	
	opendir( DIR, $dir ) or die "Failed to open $dir";
	my @reports = sort grep { $_ ne '.' && $_ ne '..' } readdir( DIR );
	closedir( DIR );
	
	# map from time to name to path
	my $data = {};
	# map from time to branch name
	my %branch_names = ();
	# unique names
	my %all_index_names = ();
	my %report_names = ();
	foreach my $report ( @reports ) {
		my $name = $report;
		$name = strftime '%Y-%m-%dT%H:%M:%S', gmtime $name if $name =~ m/^\d+$/;
		$branch_names{$name} = load_branch_name( "$dir/$report" );
		$report_names{$name} = 1;
		my @index_paths = index_scan( "$dir/$report" );

		my @index_names = strip_shared_path_elements( @index_paths );
		$all_index_names{$_} = 1 foreach @index_names;
		for( my $i = 0; $i < scalar @index_paths; $i++ ) {
			$data->{$name}->{$index_names[$i]} = $index_paths[$i];
		}
	}
	
	my $table = <<EOT;
<table>
	<tbody>
EOT
	foreach my $time ( reverse sort keys %report_names ) {
		$table .= "\t\t<tr> <th><code>$time</code></th>\n";
		$table .= "\t\t\t <th><code>$branch_names{$time}</code></th>\n";
		foreach my $name ( sort keys %all_index_names ) {
			$table .= "\t\t\t<td>";
			$table .= "<a href=\"$data->{$time}->{$name}\">$name</a>" if defined $data->{$time}->{$name};
			$table .= "</td>\n";
		}
		$table .= "\t\t</tr>\n";
	}
	$table .= <<EOT;
	</tbody>
</table>
EOT
	return $table;
}

sub regenerate_index {
	my %content = @_;
	
	my $file = 'index.md';
	open my $rh, $file or die "Failed to open $file $!";
	my @lines = <$rh>;
	close $rh;
	
	my $regenerated = "";
	for( my $i = 0; $i < scalar @lines; $i++ ) {
		$regenerated .= $lines[$i];
		if( $lines[$i] =~ m/<!-- start:(\w+) -->/ && defined $content{$1} ) {
			# insert our content
			$regenerated .= $content{$1};

			# advance the index to the end of the block
			while( $i < scalar @lines && $lines[$i] !~ m/<!-- end:$1 -->/ ) {
				$i++;
			}
			$regenerated .= $lines[$i];
		}
	}
	
	
	open my $wh, '>', $file or die "Failed to open $file $!";
	print $wh $regenerated;
	close $wh;
}

sub link_latest {
	my ( $dir ) = @_;

	opendir( DIR, $dir ) or die "Failed to open $dir";
	my @timestamps = sort grep { m/^\d+$/ } readdir( DIR );
	closedir( DIR );

	if( scalar @timestamps ){
		my $latest = $timestamps[-1];
		system( "cd $dir; rm -rf latest; ln -sf $latest latest; cd .." );
	}
}
