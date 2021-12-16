# Downloads

## Latest version

To download and build the most recent release of BlackLab, clone the repository and build it using Maven:

	git clone git://github.com/INL/BlackLab.git
	cd BlackLab
	mvn install
	
To use the latest development version, switch to the 'dev' branch:

	git clone git://github.com/INL/BlackLab.git
	cd BlackLab
	git checkout dev
	mvn install
	
If you're using Subversion, use:

	svn checkout https://github.com/INL/BlackLab/trunk BlackLab
	cd BlackLab
	mvn install

## Releases

See the [GitHub releases page](https://github.com/INL/BlackLab/releases/) for the complete list. This may also include development versions you can try out. If you're looking for the BlackLab library or commandline tools (i.e. not BlackLab Server), choose the version with libraries included.

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
			<td><a href='https://github.com/INL/BlackLab/archive/dev.tar.gz'>Source (tgz)</a></td>
			<td><a href='https://github.com/INL/BlackLab/archive/dev.zip'>Source (zip)</a></td>
			<td>In-development version</td>
		</tr>
        <tr>
            <td>v2.1.1</td>
            <td><a href='https://github.com/INL/BlackLab/releases/download/v2.1.1/blacklab-server-2.1.1.war'>BlackLab Server WAR</a></td>
            <td><a href='https://github.com/INL/BlackLab/releases/download/v2.1.1/blacklab-core.zip'>BlackLab Core JAR and libs</a></td>
            <td><a href='https://github.com/INL/BlackLab/archive/v2.1.1.tar.gz'>Source (tgz)</a></td>
            <td><a href='https://github.com/INL/BlackLab/archive/v2.1.1.zip'>Source (zip)</a></td>
            <td>Bugfixes. MetadataFieldsWriter allows programmatically setting specials fields such as pidField.
            Fixes log4j security issue by upgrading to 2.16.0.</td>
        </tr>
        <tr>
            <td>v2.0.0</td>
            <td><a href='https://github.com/INL/BlackLab/releases/download/v2.0.0/blacklab-server-2.0.0.war'>Binary (war)</a></td>
            <td><a href='https://github.com/INL/BlackLab/archive/v2.0.0.tar.gz'>Source (tgz)</a></td>
            <td><a href='https://github.com/INL/BlackLab/archive/v2.0.0.zip'>Source (zip)</a></td>
            <td>New Java API. Multithreaded indexing and search. Saxon (XPath 3) support. Multiple metadata values per field possible. Many more improvements.</td>
        </tr>
		<tr>
			<td>v1.7.2</td>
			<td><a href='https://github.com/INL/BlackLab/releases/download/v1.7.2/blacklab-server-1.7.2.war'>Binary (war)</a></td>
			<td><a href='https://github.com/INL/BlackLab/archive/v1.7.2.tar.gz'>Source (tgz)</a></td>
			<td><a href='https://github.com/INL/BlackLab/archive/v1.7.2.zip'>Source (zip)</a></td>
			<td>Made configuring input formats much easier, using a YAML (or JSON) file. Improved corpus structure information to allow better UI generation. Many other improvements.</td>
		</tr>
	</tbody>
</table>
