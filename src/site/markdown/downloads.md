# Downloads

## Latest version
To download and build the most recent (in-development) version of BlackLab, clone the repository and build it using Maven:

	git clone git://github.com/INL/BlackLab.git
	cd BlackLab
	mvn install

If you're using Subversion, use:

	svn checkout https://github.com/INL/BlackLab/trunk BlackLab
	cd BlackLab
	mvn install

## Releases

Also see the [Change log](changelog.html).

<table>
	<tbody>
		<tr>
			<th>Version</th>
			<th colspan='3'>Downloads</th>
			<th>Description</th>
		</tr>
		<tr>
			<td>SNAPSHOT</td>
			<td></td>
			<td><a href='https://github.com/INL/BlackLab/archive/master.tar.gz'>Source (tgz)</a></td>
			<td><a href='https://github.com/INL/BlackLab/archive/master.zip'>Source (zip)</a></td>
			<td>In-development version</td>
		</tr>
		<tr>
			<td>v1.3.4</td>
			<td><a href='https://github.com/INL/BlackLab/releases/download/v1.3.3/blacklab-1.3.4.jar'>Binary (jar)</a></td>
			<td><a href='https://github.com/INL/BlackLab/archive/v1.3.4.tar.gz'>Source (tgz)</a></td>
			<td><a href='https://github.com/INL/BlackLab/archive/v1.3.4.zip'>Source (zip)</a></td>
			<td>Fixed incorrect string escaping.</td>
		</tr>
		<tr>
			<td>v1.3.3</td>
			<td><a href='https://github.com/INL/BlackLab/releases/download/v1.3.3/blacklab-1.3.3.jar'>Binary (jar)</a></td>
			<td><a href='https://github.com/INL/BlackLab/archive/v1.3.3.tar.gz'>Source (tgz)</a></td>
			<td><a href='https://github.com/INL/BlackLab/archive/v1.3.3.zip'>Source (zip)</a></td>
			<td>Fixed empty concordances bug for pre-1.3 indices.</td>
		</tr>
		<tr>
			<td>v1.3.2</td>
			<td><a href='https://github.com/INL/BlackLab/releases/download/v1.3.2/blacklab-1.3.2.jar'>Binary (jar)</a></td>
			<td><a href='https://github.com/INL/BlackLab/archive/v1.3.2.tar.gz'>Source (tgz)</a></td>
			<td><a href='https://github.com/INL/BlackLab/archive/v1.3.2.zip'>Source (zip)</a></td>
			<td>Fixed query rewrite bug; prepared for release to Maven Central.</td>
		</tr>
		<tr>
			<td>v1.3.1</td>
			<td><a href='https://github.com/INL/BlackLab/releases/download/v1.3.1/blacklab-1.3.1.jar'>Binary (jar)</a></td>
			<td><a href='https://github.com/INL/BlackLab/archive/v1.3.1.tar.gz'>Source (tgz)</a></td>
			<td><a href='https://github.com/INL/BlackLab/archive/v1.3.1.zip'>Source (zip)</a></td>
			<td>Fixed bug with AND queries sometimes reporting incorrect hits; skipped special OS files inside archives.</td>
		</tr>
		<tr>
			<td>v1.3</td>
			<td><a href='https://github.com/INL/BlackLab/releases/download/v1.3/blacklab-1.3.jar'>Binary (jar)</a></td>
			<td><a href='https://github.com/INL/BlackLab/archive/v1.3.tar.gz'>Source (tgz)</a></td>
			<td><a href='https://github.com/INL/BlackLab/archive/v1.3.zip'>Source (zip)</a></td>
			<td>Forward index terms file can grow larger than 2 GB; properties without forward index can be defined.</td>
		</tr>
		<tr>
			<td>v1.2.1</td>
			<td><a href='https://github.com/INL/BlackLab/releases/download/v1.2.1/blacklab-1.2.1.jar'>Binary (jar)</a></td>
			<td><a href='https://github.com/INL/BlackLab/archive/v1.2.1.tar.gz'>Source (tgz)</a></td>
			<td><a href='https://github.com/INL/BlackLab/archive/v1.2.1.zip'>Source (zip)</a></td>
			<td>Fixed document-only queries returning incorrect results.</td>
		</tr>
		<tr>
			<td>v1.2.0</td>
			<td><a href='https://github.com/INL/BlackLab/releases/download/v1.2.0/blacklab-1.2.0.jar'>Binary (jar)</a></td>
			<td><a href='https://github.com/INL/BlackLab/archive/v1.2.0.tar.gz'>Source (tgz)</a></td>
			<td><a href='https://github.com/INL/BlackLab/archive/v1.2.0.zip'>Source (zip)</a></td>
			<td>Lucene 5, many optimizations, using Maven</td>
		</tr>
		<tr>
			<td>v1.1.0</td>
			<td><a href='https://github.com/INL/BlackLab/releases/download/v1.1.0/BlackLab.jar'>Binary (jar)</a></td>
			<td><a href='https://github.com/INL/BlackLab/archive/v1.1.0.tar.gz'>Source (tgz)</a></td>
			<td><a href='https://github.com/INL/BlackLab/archive/v1.1.0.zip'>Source (zip)</a></td>
			<td>Lucene 4, last version using Ant</td>
		</tr>
		<tr>
			<td>v1.0</td>
			<td></td>
			<td><a href='https://github.com/INL/BlackLab/archive/v1.0.tar.gz'>Source (tgz)</a></td>
			<td><a href='https://github.com/INL/BlackLab/archive/v1.0.zip'>Source (zip)</a></td>
			<td>Lucene 3.6</td>
		</tr>
	</tbody>
</table>
